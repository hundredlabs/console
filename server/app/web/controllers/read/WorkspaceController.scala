package web.controllers.read

import java.io.{File, FileInputStream, FileReader}
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Sink, Source}
import com.gigahex.commons.models.{ClusterProvider, ClusterStatus, NewCluster}
import com.mohiva.play.silhouette.api.{HandlerResult, Silhouette}
import controllers.AssetsFinder
import javax.inject.Inject
import web.models.{ ClusterJsonFormat, ClusterMetric, ErrorResponse, IllegalParam, InternalServerErrorResponse, NewSandboxCluster, OrgDetail, UserNotAuthenticated, VerifyCluster}
import web.models.formats.{AuthResponseFormats, SecretsJsonFormat}
import web.models.rbac.{AccessPolicy, SubjectType}
import web.models.requests.{CreateOrgWorkspace, OrgRequestsJsonFormat, ProvisionWorkspace}
import web.services.{ClusterService, MemberService, SecretStore}
import play.api.{Configuration, Environment, Play}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, ControllerComponents, InjectedController, Request, WebSocket}
import play.cache.NamedCache
import utils.auth.{DefaultEnv, RandomGenerator}
import web.controllers.handlers.SecuredWebRequestHandler

import concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, blocking}

class WorkspaceController @Inject()(
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    clusterService: ClusterService,
    @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    secretStore: SecretStore,
    configuration: Configuration,
    env: Environment
)(
    implicit
    ex: ExecutionContext,
    assets: AssetsFinder,
    system: ActorSystem,
    mat: Materializer
) extends InjectedController
    with I18nSupport
    with AuthResponseFormats
    with ErrorResponse
    with SecretsJsonFormat
    with ClusterJsonFormat
    with OrgRequestsJsonFormat
    with SecuredWebRequestHandler {

  implicit val clusterMetricsFlowTransfer = MessageFlowTransformer.jsonMessageFlowTransformer[String, ClusterMetric]

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def createWorkspace: Action[CreateOrgWorkspace] = silhouette.UserAwareAction.async(validateJson[CreateOrgWorkspace]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      roles
        .find(p => p.subjectId == profile.orgId && p.subjectType == SubjectType.ORG && p.policies.contains(AccessPolicy.ORG_MANAGE)) match {
        case None =>
          Future.successful(Forbidden)

        case Some(_) =>
          memberService.createOrgWorkspace(request.identity.get.id.get, profile.orgId, secretStore, request.body).map {
            case Left(value) => BadRequest(Json.toJson(IllegalParam(request.path, memberId = 0, message = value.getMessage)))
            case Right(_)    => Created(Json.toJson(Map("created" -> true)))
          }
      }
    }
  }

  def getOrgDetails = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasOrgManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        memberService
          .getOrgDetail(profile.orgId)
          .map(org =>
            org match {
              case None        => NotFound
              case Some(value) => Ok(Json.toJson(value))
          })
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def assetsAt(file: String) = Action { implicit request =>
    val img = new File(s"${configuration.get[String]("gigahex.images")}/${file}")
    if (img.exists()) {
      Ok.sendFile(img)
    } else {
      NotFound
    }

  }

  def updateOrg = silhouette.UserAwareAction.async(validateJson[OrgDetail]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasOrgManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        memberService
          .updateOrg(profile.orgId, request.body)
          .map(org =>
            org match {
              case None        => NotFound
              case Some(value) => Ok(Json.toJson(value))
          })
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def uploadOrganisationLogo = silhouette.UserAwareAction.async(parse.multipartFormData) { request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasOrgManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        val rootpath = configuration.get[String]("gigahex.images")

        request.body
          .file("picture")
          .map { picture =>
            Future {
              blocking {
                val filename    = Paths.get(picture.filename).getFileName
                val tmpFilePath = s"${rootpath}/${filename}"
                picture.ref.copyTo(Paths.get(tmpFilePath), replace = true)
                val picFile             = new File(tmpFilePath)
                val extension           = tmpFilePath.substring(tmpFilePath.lastIndexOf('.'))
                val destinationFileName = System.currentTimeMillis() + extension
                val destFile            = new File(s"${rootpath}/${destinationFileName}")
                picFile.renameTo(destFile)
                Ok(Json.toJson(Map("path" -> s"/web/assets/img/${destinationFileName}")))
              }
            }
          }
          .getOrElse(Future.successful(InternalServerError("Failed uploading")))
      } else {
        Future.successful(Forbidden)
      }
    }

  }

  def listWorkspaces = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        memberService
          .listWorkspaces(profile.orgId)
          .map(ws => Ok(Json.toJson(ws)))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * Fetches the list of sandbox versions supported for the desktop environment
    * @return
    */
  def listSandboxVersions = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .listSandboxVersions()
          .map(cs => Ok(Json.toJson(cs)))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def fetchPackageVersions(name: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterPackages(name).map(vs => Ok(Json.toJson(vs)))
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * Save Docker based sandbox cluster.
    * @return
    */
  def updateClusterStatus(clusterId: Long, status: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .updateCluster(profile.workspaceId, clusterId, ClusterStatus.withName(status))
          .map(done => Ok(Json.toJson(Map("updated" -> done))))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def listAPIKeys = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        memberService
          .listWorkspaceAPIKeys(profile.workspaceId, secretStore, workspaceKeyCache)
          .map(keys => Created(Json.toJson(keys)))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def listWorkspaceHosts: Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {

        clusterService
          .listLocalHostByWorkspace(profile.workspaceId)
          .map(hosts => Ok(Json.toJson(hosts)))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }


  /**
    * Verify if the cluster exists
    * @return
    */
  def verifyCluster = silhouette.UserAwareAction.async(validateJson[VerifyCluster]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .clusterExists(profile.workspaceId, request.body.name, request.body.provider.toString())
          .map { cId =>
            cId match {
              case None    => NotFound(Json.toJson(InternalServerErrorResponse(request.path, "No cluster found for the given name.")))
              case Some(v) => Ok(Json.toJson(Map("clusterId" -> v)))
            }
          }
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * List all the clusters
    * @return
    */
  def listAllWorkspaceClusters: Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .listAllClusters(profile.orgId, profile.workspaceId)
          .map(clusters => Ok(Json.toJson(clusters)))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }


  def newCluster = silhouette.UserAwareAction.async(validateJson[NewCluster]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .addCluster(profile.orgId, profile.workspaceId, request.body, None)
          .map(result =>
            result.fold(ex => BadRequest(Json.toJson(Map("error" -> ex.getMessage))), id => Created(Json.toJson(Map("clusterId" -> id)))))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }



  def onboardMember: Action[ProvisionWorkspace] = silhouette.UserAwareAction.async(validateJson[ProvisionWorkspace]) { implicit request =>
    request.identity match {
      case Some(v) if v.id.isDefined =>
        memberService.getMemberProfile(v.id.get).flatMap {
          case None =>
            memberService.provisionWorkspace(v.name, request.body, secretStore, v.id.get).map {
              case Left(ex)     => BadRequest(Json.toJson(IllegalParam(request.path, 0, ex.getMessage)))
              case Right(value) => Created(Json.toJson(value))
            }
          case Some(_) =>
            Future.successful(BadRequest(Json.toJson(IllegalParam(request.path, 0, "There is already an organisation for this account."))))
        }
      case _ => Future.successful(Forbidden)

    }
  }

}

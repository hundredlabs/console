package web.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.gigahex.aws.S3DataService
import com.gigahex.services.fs.FailedFileListing
import com.gigahex.services.{AWSS3Connection, ServiceConnection}
import com.mohiva.play.silhouette.api.{HandlerResult, Silhouette}
import controllers.AssetsFinder
import javax.inject.Inject
import web.models.{ErrorResponse, InternalServerErrorResponse}
import web.models.formats.{AuthResponseFormats, ConnectionFormats}
import web.services.{ClusterService, FileServiceManager, MemberService, SecretStore, WorkspaceService}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{ControllerComponents, InjectedController}
import play.cache.NamedCache
import utils.auth.{DefaultEnv, RandomGenerator}
import web.controllers.handlers.SecuredWebRequestHandler

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Failure

class FSController @Inject()(
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    workspaceService: WorkspaceService,
    @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
    secretStore: SecretStore
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
    with SecuredWebRequestHandler
    with ConnectionFormats {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def listRootLevelFiles(connectionId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        workspaceService
          .getConnection(profile.workspaceId, connectionId)
          .map {
            case Left(e) => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
            case Right(Some(v)) =>
              secretStore
                .decryptText(v.encProperties, profile.workspaceId, workspaceKeyCache)
                .map(p => Json.parse(p).as[ServiceConnection])
                .map {
                  case x: AWSS3Connection => new FileServiceManager[AWSS3Connection](x, S3DataService)
                }
                .map(service => service.listFiles(None, None, v.name, v.provider))
                .getOrElse(Failure(new RuntimeException("Failed extracting the config")))
                .fold(
                  err => {
                    BadRequest(Json.toJson(FailedFileListing(v.name, v.provider, err.getMessage)))
                  },
                  result => Ok(Json.toJson(result))
                )
            case _ => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, "Invalid connection configuration provided")))
          }

      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def listFiles(connectionId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        workspaceService
          .getConnection(profile.workspaceId, connectionId)
          .map {
            case Left(e) => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
            case Right(Some(v)) =>
              secretStore
                .decryptText(v.encProperties, profile.workspaceId, workspaceKeyCache)
                .map(p => Json.parse(p).as[ServiceConnection])
                .map {
                  case x: AWSS3Connection => new FileServiceManager[AWSS3Connection](x, S3DataService)
                }
                .map(service => service.listFiles(Some(path), None, v.name, v.provider))
                .getOrElse(Failure(new RuntimeException("Failed extracting the config")))
                .fold(
                  err => {
                    BadRequest(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage)))
                  },
                  result => Ok(Json.toJson(result))
                )
            case _ => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, "Invalid connection configuration provided")))
          }

      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def newDirectory(connectionId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        workspaceService
          .getConnection(profile.workspaceId, connectionId)
          .map {
            case Left(e) => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
            case Right(Some(v)) =>
              secretStore
                .decryptText(v.encProperties, profile.workspaceId, workspaceKeyCache)
                .map(p => Json.parse(p).as[ServiceConnection])
                .map {
                  case x: AWSS3Connection => new FileServiceManager[AWSS3Connection](x, S3DataService)
                }
                .map(service => service.createDirectory(path))
                .getOrElse(Failure(new RuntimeException("Failed extracting the config")))
                .fold(
                  err => {

                    BadRequest(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage)))
                  },
                  result => Ok(Json.toJson(Map("success" -> result)))
                )
            case _ => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, "Invalid connection configuration provided")))
          }

      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def deleteFile(connectionId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        workspaceService
          .getConnection(profile.workspaceId, connectionId)
          .map {
            case Left(e) => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
            case Right(Some(v)) =>
              secretStore
                .decryptText(v.encProperties, profile.workspaceId, workspaceKeyCache)
                .map(p => Json.parse(p).as[ServiceConnection])
                .map {
                  case x: AWSS3Connection => new FileServiceManager[AWSS3Connection](x, S3DataService)
                }
                .map(service => service.deleteFile(path))
                .getOrElse(Failure(new RuntimeException("Failed extracting the config")))
                .fold(
                  err => {

                    BadRequest(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage)))
                  },
                  result => Ok(Json.toJson(Map("success" -> result)))
                )
            case _ => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, "Invalid connection configuration provided")))
          }

      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def upload(connectionId: Long, path: String) = silhouette.UserAwareAction.async(parse.multipartFormData) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        workspaceService
          .getConnection(profile.workspaceId, connectionId)
          .map {
            case Left(e) => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
            case Right(Some(v)) =>
              request.body
                .file("file")
                .flatMap { f =>
                  secretStore
                    .decryptText(v.encProperties, profile.workspaceId, workspaceKeyCache)
                    .map(p => Json.parse(p).as[ServiceConnection])
                    .map {
                      case x: AWSS3Connection => new FileServiceManager[AWSS3Connection](x, S3DataService)
                    }
                    .map(service => service.uploadFile(path, f.ref.path.toFile))
                }
                .getOrElse(Failure(new RuntimeException("Failed extracting the config")))
                .fold(
                  err => {

                    BadRequest(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage)))
                  },
                  result => Ok(Json.toJson(Map("success" -> result)))
                )
            case _ => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, "Invalid connection configuration provided")))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

}

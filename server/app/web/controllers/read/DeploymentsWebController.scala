package web.controllers.read

import java.io.FileInputStream

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.gigahex.commons.models.{DeploymentActionLogReq, DeploymentView, JobConfig, NewDeploymentRequest, NewDeploymentRun, RunStatus, TriggerMethod}
import com.mohiva.play.silhouette.api.{HandlerResult, Silhouette}
import javax.inject.{Inject, Named}
import web.models.{ DeploymentJsonFormat, ErrorResponse, JobFormats, UserNotAuthenticated}
import web.services.{DeploymentService, JobService, MemberService}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}

import concurrent.duration._
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, InjectedController, Request, WebSocket}
import play.cache.NamedCache
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler
import web.models.formats.AuthResponseFormats

import scala.concurrent.{ExecutionContext, Future}

class DeploymentsWebController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    configuration: Configuration,
    jobService: JobService,
    deploymentService: DeploymentService
)(
    implicit
    ex: ExecutionContext,
    system: ActorSystem,
    mat: Materializer
) extends InjectedController
    with I18nSupport
    with AuthResponseFormats
    with ErrorResponse
    with JobFormats
    with DeploymentJsonFormat
    with SecuredWebRequestHandler {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  implicit val listJobsFlowTransfer = MessageFlowTransformer.jsonMessageFlowTransformer[DeploymentActionLogReq, String]

  def listDeployments(jobId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        deploymentService.listDeployments(profile.workspaceId, jobId).map { r =>
          Ok(Json.toJson(r))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def addDeployment(jobId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        deploymentService.listDeployments(profile.workspaceId, jobId).map { r =>
          Ok(Json.toJson(r))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def updateDeployment(deploymentId: Long, jobId: Long) = silhouette.UserAwareAction.async(validateJson[JobConfig]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        deploymentService.updateDeployment(jobId, profile.workspaceId, deploymentId, request.body).map { r =>
          Ok(Json.toJson(Map("updated" -> r)))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def runDeployment(deploymentId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        deploymentService
          .newDeploymentJob(profile.workspaceId, NewDeploymentRun(deploymentId, TriggerMethod.MANUAL, RunStatus.Waiting))
          .map { r =>
            Ok(Json.parse(s"""{"deploymentRunId": ${r.getOrElse(-1)}}"""))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  case class UserName(name: String)
  implicit val jsonFormatUsername = Json.format[UserName]
  def say = Action.async(parse.json[UserName]) { request =>
    Future.successful(Ok(Json.obj("name " -> request.body.name)))
  }

  def streamDeploymentRunDetail(projectId: Long, deploymentRunId: Long) = WebSocket.acceptOrResult[String, String] { implicit request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(m)) =>
          val mv = memberService.getMemberValue(m.id.getOrElse(0), userCache)
          if (mv.orgIds.size > 0) {
            val in = Sink.ignore
            val deployments = Source
              .tick(2.seconds, 4.seconds, "tick")
              .mapAsync(1)(_ => deploymentService.getDeploymentRunResult(mv.orgIds.head, deploymentRunId))
              .map(Json.toJson(_).toString())

            Right(Flow.fromSinkAndSource(in, deployments))
          } else {
            Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
          }

        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def listDeploymentStream(projectId: Long) = WebSocket.acceptOrResult[String, String] { implicit request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .flatMap {
        case HandlerResult(r, Some(m)) =>
          usingIdentity(m.id.get, memberService) { (roles, profile) =>
            if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
              val in = Sink.ignore
              val deployments = Source
                .tick(2.seconds, 3.seconds, "tick")
                .mapAsync(1)(_ => deploymentService.listDeployments(profile.workspaceId, projectId))
                .map(Json.toJson(_).toString())

              Future.successful(Right(Flow.fromSinkAndSource(in, deployments)))
            } else {
              Future.successful(Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
            }
          }

        case HandlerResult(r, None) => Future.successful(Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def deleteDeployment(jobId: Long, deploymentId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        deploymentService
          .delete(profile.workspaceId, jobId, deploymentId)
          .map { r =>
            Ok(Json.parse(s"""{"hasDeleted": ${r}}"""))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getDeploymentRunDetail(deploymentRunId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrg(request, memberService, userCache) { (mv) =>
      deploymentService.getDeploymentRunResult(mv.orgIds.head, deploymentRunId).map { r =>
        if (r.nonEmpty) {
          Ok(Json.toJson(r.get))
        } else NotFound
      }
    }
  }

  def getDeploymentRunHistory(jobId: Long, deploymentConfigId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        deploymentService.listDeploymentHistory(profile.workspaceId, jobId, deploymentConfigId).map { r =>
          Ok(Json.toJson(r))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getDeploymentActionLog(projectId: Long, deploymentRunId: Long, actionName: String) = silhouette.UserAwareAction.async {
    implicit request =>
      handleRequestWithOrgAndJobs(request, memberService, userCache, projectId, jobCache, jobService) { (mv, jv) =>
        val source: Source[String, _] = deploymentService.getActionLogs(deploymentRunId, actionName, configuration)
        val logsFile                  = s"${configuration.underlying.getString("gigahex.logsDir")}/${deploymentRunId}/${actionName}.log"
        val contentStream             = StreamConverters.fromInputStream(() => new FileInputStream(logsFile))

        Future.successful(Ok.chunked(contentStream))
      }
  }

  def streamDeploymentActionLog(deploymentRunId: Long, actionName: String) = WebSocket.acceptOrResult[String, String] {
    implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      silhouette
        .SecuredRequestHandler { securedRequest =>
          Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
        }
        .flatMap {
          case HandlerResult(r, Some(m)) =>
            usingIdentity(m.id.get, memberService) { (roles, profile) =>
              if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
                val in = Sink.ignore
                Future.successful(
                  Right(Flow.fromSinkAndSource(in, deploymentService.getActionLogs(deploymentRunId, actionName, configuration))))
              } else {
                Future.successful(Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
              }
            }

          case HandlerResult(r, None) => Future.successful(Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
        }
        .recover {
          case e: Exception =>
            val result = InternalServerError(e.getMessage)
            Left(result)
        }
  }

}

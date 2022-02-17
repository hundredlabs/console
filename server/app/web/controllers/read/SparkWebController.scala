package web.controllers.read

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.gigahex.commons.models.{JobType, RunStatus}
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named}
import web.models
import web.models.spark.{GetSparkMetric, GetSparkStage, GetSparkTimeSerie, JobRun, SparkMetricFmt}
import web.models.{ErrorResponse, TimeFilter, UserNotAuthenticated}
import web.services.{DeploymentService, JobService, MemberService, SparkEventService}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, InjectedController, Request, WebSocket}
import play.cache.NamedCache
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler
import web.models.formats.AuthResponseFormats

import concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class SparkWebController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    sparkEventService: SparkEventService,
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
    with SparkMetricFmt
    with SecuredWebRequestHandler {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  /**
    * Fetch the errors if any
    * @param jobId
    * @param runId
    * @return
    */
  def getJobRunErrors(jobId: Long, runId: Long, attemptId: String) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        jobService.getError(runId, attemptId).map { result =>
          Ok(Json.toJson(result))
        }
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def getExecutionSummary(jobId: Long, runId: Long, attemptId: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService.getExecutionMetrics(runId, attemptId).map {
          case None        => NotFound
          case Some(value) => Ok(Json.toJson(value))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getWorkerMetrics(jobId: Long, runId: Long, attemptId: String, workerId: String) = silhouette.UserAwareAction.async {
    implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
          sparkEventService.getWorkersRuntimeMetrics(runId, attemptId, workerId).map {
            case None        => NotFound
            case Some(value) => Ok(Json.toJson(value))
          }
        } else {
          Future.successful(Forbidden)
        }
      }
  }

  def streamAggregateMetrics(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] { implicit request =>
    implicit val req = Request(request, AnyContentAsEmpty)

    handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
      Source
        .tick(2.seconds, 2.seconds, "tick")
        .mapAsync(1)(_ => sparkEventService.getSparkAggMetrics(projectId, runId, attemptId))
        .map { result =>
          result match {
            case Left(err) =>
              Json.toJson(Map("error" -> err.getMessage)).toString()
            case Right(v) => Json.toJson(v).toString()
          }
        }

    }
  }

  def streamAppOverview(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] { implicit request =>
    implicit val req = Request(request, AnyContentAsEmpty)

    handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
      Source
        .tick(2.seconds, 2.seconds, "tick")
        .mapAsync(1)(_ => sparkEventService.getSparkOverviewMetric(projectId, runId, attemptId))
        .map(Json.toJson(_).toString())
    }
  }

  def getOverview = silhouette.UserAwareAction.async(validateJson[GetSparkMetric]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService.getSparkOverviewMetric(request.body.projectId, request.body.runId, request.body.attemptId).map {
          case None        => NotFound
          case Some(value) => Ok(Json.toJson(value))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamJobMetrics(projectId: Long, runId: Long, attemptId: String, startTime: Long, endTime: Long) =
    WebSocket.acceptOrResult[String, String] { implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
        Source
          .tick(2.seconds, 3.seconds, "tick")
          .mapAsync(1)(_ => sparkEventService.getSparkJobMetrics(projectId, runId, attemptId, startTime, endTime))
          .map {
            case Left(error)  => Json.toJson(models.IllegalParam(request.path, 0, error.getMessage)).toString()
            case Right(value) => Json.toJson(value).toString()
          }
      }
    }

  def getSparkJobMetrics(jobId: Long, runId: Long, appAttemptId: String) =
    silhouette.UserAwareAction.async(validateJson[TimeFilter]) { implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
          sparkEventService.getSparkJobMetrics(jobId, runId, appAttemptId, request.body.startTime, request.body.endTime).map { result =>
            result.fold(
              error => BadRequest(Json.toJson(models.IllegalParam(request.path, profile.workspaceId, error.getMessage))),
              jv => Ok(Json.toJson(jv))
            )
          }
        } else {
          Future.successful(Forbidden)
        }
      }
    }

  def getRuntimeObservations = silhouette.UserAwareAction.async(validateJson[GetSparkMetric]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService.analyzeRuntimeMetrics(request.body.projectId, request.body.runId, request.body.attemptId).map { value =>
          Ok(Json.toJson(value))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamCPUTimeSerie(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] { implicit request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
      Source
        .tick(2.seconds, 3.seconds, "tick")
        .mapAsync(1)(_ => sparkEventService.getCPUTimeSerie(runId, attemptId, None, None))
        .map {
          case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
          case Some(value) => Json.toJson(value).toString()
        }
    }
  }

  def getSparkCPUTimeserie = silhouette.UserAwareAction.async(validateJson[GetSparkTimeSerie]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService.getCPUTimeSerie(request.body.runId, request.body.attemptId, request.body.startTime, request.body.endTime).map {
          case None        => NotFound
          case Some(value) => Ok(Json.toJson(value))
        }
      } else {
        Future.successful(Forbidden)
      }
    }

  }

  def streamMemoryTimeserie(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] {
    implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
        Source
          .tick(2.seconds, 3.seconds, "tick")
          .mapAsync(1)(_ => sparkEventService.getMemTimeSerie(runId, attemptId, None, None))
          .map {
            case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }
      }
  }

  def getSparkMemoryTimeserie = silhouette.UserAwareAction.async(validateJson[GetSparkTimeSerie]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService.getMemTimeSerie(request.body.runId, request.body.attemptId, request.body.startTime, request.body.endTime).map {
          case None        => NotFound
          case Some(value) => Ok(Json.toJson(value))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamHeapMemoryTimeserie(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] {
    implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
        Source
          .tick(2.seconds, 3.seconds, "tick")
          .mapAsync(1)(_ => sparkEventService.getHeapMemTimeSerie(runId, attemptId, None, None))
          .map {
            case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }
      }
  }

  def getSparkHeapMemoryTimeserie = silhouette.UserAwareAction.async(validateJson[GetSparkTimeSerie]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService
          .getHeapMemTimeSerie(request.body.runId, request.body.attemptId, request.body.startTime, request.body.endTime)
          .map {
            case None        => NotFound
            case Some(value) => Ok(Json.toJson(value))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamOffHeapMemoryTimeserie(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] {
    implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
        Source
          .tick(2.seconds, 3.seconds, "tick")
          .mapAsync(1)(_ => sparkEventService.getOffHeapMemTimeSerie(runId, attemptId, None, None))
          .map {
            case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }
      }
  }

  def getSparkOffHeapMemoryTimeserie = silhouette.UserAwareAction.async(validateJson[GetSparkTimeSerie]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService
          .getOffHeapMemTimeSerie(request.body.runId, request.body.attemptId, request.body.startTime, request.body.endTime)
          .map {
            case None        => NotFound
            case Some(value) => Ok(Json.toJson(value))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamCPUCoresTimeSerie(projectId: Long, runId: Long, attemptId: String) = WebSocket.acceptOrResult[String, String] {
    implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkWebsocketRequest(silhouette, memberService, userCache, request, projectId, runId, attemptId) { (_, _, _) =>
        Source
          .tick(2.seconds, 3.seconds, "tick")
          .mapAsync(1)(_ => sparkEventService.getCpuCoreTimeSerie(runId, attemptId, None, None))
          .map {
            case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }
      }
  }

  def listComparableSparkApps = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrg(request, memberService, userCache) { member =>
      jobService.listJobs(member.orgIds, JobType.spark).map(xs => Ok(Json.toJson(xs)))
    }

  }

  def getSparkCoreTimeserie = silhouette.UserAwareAction.async(validateJson[GetSparkTimeSerie]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService
          .getCpuCoreTimeSerie(request.body.runId, request.body.attemptId, request.body.startTime, request.body.endTime)
          .map {
            case None        => NotFound
            case Some(value) => Ok(Json.toJson(value))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getSparkStageSummary = silhouette.UserAwareAction.async(validateJson[GetSparkStage]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService
          .getStageMetricDetail(request.body.runId,
                                request.body.attemptId,
                                request.body.stageId,
                                request.body.parentJobId,
                                request.body.stageAttemptId)
          .map {
            case None        => NotFound
            case Some(value) => Ok(Json.toJson(value))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def compareJobRun = silhouette.UserAwareAction.async(validateJson[JobRun]) { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, request.body.projectId, jobCache, jobService) { (mv, jv) =>
      sparkEventService.fetchRunComparison(request.body).map {
        case None        => NotFound
        case Some(value) => Ok(Json.toJson(value))
      }
    }
  }

}

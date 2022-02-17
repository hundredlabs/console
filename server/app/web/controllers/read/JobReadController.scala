package web.controllers.read

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.gigahex.commons.constants.Headers
import com.gigahex.commons.models.{DeploymentJob, NewDeploymentRequest}
import com.mohiva.play.silhouette.api.{HandlerResult, Silhouette}
import javax.inject.{Inject, Named}
import web.actors._
import web.models.spark._
import web.services.JobServiceClientActor.{JobRunDetailReq, ListJobs}
import web.services.{JobService, JobServiceClientActor, MemberService, SparkEventService}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._
import play.cache.NamedCache
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler
import web.models.{ClusterJsonFormat, EntityNotFound, ErrorResponse, ExecutorMetricSummary, ExecutorRuntimeMetric, IllegalParam, InternalServerErrorResponse, JobFormats, JobListView, JobRequestResponseFormat, JobRunView, LogSearchRequest, LogsSearchResponse, RegisterJobRequest, TaskHistoryReq, UpdateJobDescription, UserNotAuthenticated}
import web.models.formats.AuthResponseFormats

import scala.concurrent.{ExecutionContext, Future}

class JobReadController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    configuration: Configuration,
    jobService: JobService,
    sparkEventService: SparkEventService
)(
    implicit
    ex: ExecutionContext,
    system: ActorSystem,
    mat: Materializer
) extends InjectedController
    with I18nSupport
    with AuthResponseFormats
    with ErrorResponse
    with ClusterJsonFormat
    with JobFormats
    with JobRequestResponseFormat
    with SparkMetricFmt
    with SecuredWebRequestHandler {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[JobRunDetailReq, JobRunView]
  implicit val listJobsFlowTransfer   = MessageFlowTransformer.jsonMessageFlowTransformer[ListJobs, Seq[JobListView]]

  implicit val aggMetricsFlowTransformer =
    MessageFlowTransformer.jsonMessageFlowTransformer[AppStateWSReq, SparkAggMetrics]

  implicit val jobMetricsFlowTransformer =
    MessageFlowTransformer.jsonMessageFlowTransformer[AppStateWSReq, Seq[JobState]]

  implicit val execMetricsFlowTransformer =
    MessageFlowTransformer.jsonMessageFlowTransformer[AppStateWSReq, ExecutorMetricResponse]

  implicit val stageMetricsFlowTransformer =
    MessageFlowTransformer.jsonMessageFlowTransformer[SparkStageWSReq, Seq[StageState]]
  implicit val searchLogFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[SparkLogWSReq, LogsSearchResponse]

  implicit val executorListFlowTrans         = MessageFlowTransformer.jsonMessageFlowTransformer[ExecutorWSReq, Seq[ExecutorMetricSummary]]
  implicit val executorDetailMetricFlowTrans = MessageFlowTransformer.jsonMessageFlowTransformer[ExecutorWSReq, ExecutorRuntimeMetric]

  /**
    * List all the jobs
    * @return
    */
  def listJobs = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      jobService.listJobs(profile.orgId, profile.workspaceId).map { result =>

        result match {
          case Left(err) => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage)))
          case Right(jv) => Ok(Json.toJson(jv))
        }
//        result.fold(
//          err => BadRequest(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage))),
//          jv => Ok(Json.toJson(jv))
//        )
      }
    }
  }

  def listDeploymentJobs(clusterId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService
          .listJobsByCluster(profile.orgId, profile.workspaceId, clusterId)
          .map(jobs => Ok(Json.toJson(jobs)))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def registerJob = silhouette.UserAwareAction.async(validateJson[RegisterJobRequest]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService.registerJob(profile.orgId, profile.workspaceId, request.body).map(id => Created(Json.toJson(Map("jobId" -> id))))
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getDeploymentConfig(deploymentId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService
          .getDeploymentDetail(profile.workspaceId, deploymentId)
          .map {
            case None        => NotFound
            case Some(value) => Ok(Json.toJson(value))
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

  def newDeploymentJob = silhouette.UserAwareAction.async(validateJson[DeploymentJob]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService.saveDeploymentJob(profile.orgId, profile.workspaceId, request.body).map(id => Created(Json.toJson(Map("jobId" -> id))))
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def addDeploymentConfig(jobId: Long, clusterId: Long) = silhouette.UserAwareAction.async(validateJson[NewDeploymentRequest]) {
    implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
          jobService
            .addDeploymentConfig(profile.workspaceId, jobId, clusterId, request.body)
            .map(id => Created(Json.toJson(Map("deploymentId" -> id))))
        } else {
          Future.successful(Forbidden)
        }
      }
  }


  def updateDescription(jobId: Long) = silhouette.UserAwareAction.async(validateJson[UpdateJobDescription]) {
    implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
          jobService
            .updateDescription( jobId, profile.workspaceId, request.body.text)
            .map(updated => Ok(Json.toJson(Map("isUpdated" -> updated))))
        } else {
          Future.successful(Forbidden)
        }
      }
  }

  /**
    * Get the spark task id for the Job, which has been submitted using gx spark-submit
    */
  def getSparkTask(id: Long, runId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrg(request, memberService, userCache) { mv =>
      //validate the jobId has the correct view permission for logged in user
      //No need to validate the runId, as it's being joined with jobId when fetching the results
      val jobVal = jobService.updateJobCache(id, jobCache)
      if (mv.orgIds.contains(1)) {
        jobService.getSparkTask(id, runId).map {
          case Left(e) => BadRequest(Json.toJson(IllegalParam(request.path, mv.memberId, e.getMessage)))
          case Right(value) =>
            value match {
              case None        => NotFound(Json.toJson(EntityNotFound("Spark Task", request.path, mv.memberId)))
              case Some(value) => Ok(Json.parse(s"""{"taskId": ${value}}"""))
            }
        }
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }

  }

  /**
    * Fetch the job details if the user is logged in.
    * @param id
    * @return
    */
  def jobDetails(id: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, id, jobCache, jobService) { (mv, jv) =>
      jobService.find(id).map {
        case Some(v) => Ok(Json.toJson(v))
        case _       => NotFound(Json.toJson(EntityNotFound("JobDetail", request.path, mv.memberId)))
      }
    }
  }

  def listJobRuns(jobId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      jobService.listRuns(jobId).map(r => Ok(Json.toJson(r)))
    }
  }

  /**
    * Delete the job
    * @param id
    * @return
    */
  def deleteJob(id: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService
          .remove(id)
          .map(hasRemoved => Ok(Json.toJson(Map("jobDeleted" -> hasRemoved))))
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def ws = WebSocket.acceptOrResult[JobRunDetailReq, JobRunView] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(m)) =>
          Right(ActorFlow.actorRef(out => JobServiceClientActor.props(jobService, m.id.getOrElse(-1))(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsJobList: WebSocket = WebSocket.acceptOrResult[ListJobs, Seq[JobListView]] { request =>
    implicit val req: Request[AnyContentAsEmpty.type] = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(m)) =>
          val mv = memberService.getMemberValue(m.id.getOrElse(0), userCache)
          if (mv.orgIds.size > 0) {
            Right(ActorFlow.actorRef(out => JobServiceClientActor.props(jobService, mv.orgIds.head)(out)))
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

  def getJobSummary(projectId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService.getJobSummary(projectId).map {
          case None    => NotFound(Json.toJson(Map("message" -> "Job not found")))
          case Some(v) => Ok(Json.toJson(v))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getJobRunHistory(jobId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        jobService.getJobHistory(jobId).map { r =>
          Ok(Json.toJson(r))
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def updateProject(jobId: Long): Action[RegisterJobRequest] = silhouette.UserAwareAction.async(validateJson[RegisterJobRequest]) {
    implicit request =>
      handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, _) =>
        jobService
          .updateProject(mv.orgIds.head, jobId, request.body)
          .map { r =>
            Ok(Json.toJson(r))
          }
      }
  }

  /**
    * Fetch the job history if the user is logged in.
    * @param id
    * @return
    */
  def getJobRunDetail(id: Long, runId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, id, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        jobService.getJobRunDetail(id, runId).map { result =>
          result.fold(
            error => BadRequest(Json.toJson(IllegalParam(request.path, mv.memberId, error.getMessage))),
            jv => Ok(Json.toJson(jv))
          )
        }
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  /**
    * Fetch the task history if the user is logged in.
    * @param jobId
    * @return
    */
  def getJobTaskHistory(jobId: Long) = silhouette.UserAwareAction.async(validateJson[TaskHistoryReq]) { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      jobService.getTaskHistory(request.body.name, jobId).map { result =>
        result.fold(
          error => BadRequest(Json.toJson(IllegalParam(request.path, mv.memberId, error.getMessage))),
          jv => Ok(Json.toJson(jv))
        )
      }
    }
  }

  def getSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String) =
    silhouette.UserAwareAction.async { implicit request =>
      handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
        if (jv.runs.exists(_.runId == runId)) {
          sparkEventService.getSparkAggMetrics(jobId, runId, appAttemptId).map { result =>
            result.fold(
              error => BadRequest(Json.toJson(IllegalParam(request.path, mv.memberId, error.getMessage))),
              jv => Ok(Json.toJson(jv))
            )
          }
        } else {
          Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
        }
      }
    }

  def getSparkAggMetricsAll(jobId: Long, runId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        sparkEventService.getSparkAggMetricsAll(jobId, runId).map { result =>
          result.fold(
            error => {
              error match {
                case e: IllegalArgumentException => Ok(Json.toJson(EntityNotFound("Metrics", request.path, 1L, e.getMessage)))
                case e: Exception                => BadRequest(Json.toJson(IllegalParam(request.path, 1L, error.getMessage)))
              }
            },
            jv => Ok(Json.toJson(jv))
          )
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def getSparkStageMetrics(jobId: Long, runId: Long, appAttemptId: String, sparkJobId: Int) =
    silhouette.UserAwareAction.async { implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
          sparkEventService.getSparkStageMetrics(jobId, runId, appAttemptId, sparkJobId).map { result =>
            result.fold(
              error => BadRequest(Json.toJson(IllegalParam(request.path, 1L, error.getMessage))),
              jv => Ok(Json.toJson(jv))
            )
          }
        } else {
          Future.successful(Forbidden)
        }
      }
    }

  def wsSparkAggMetrics = WebSocket.acceptOrResult[AppStateWSReq, SparkAggMetrics] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => SparkEventSubscriber.props(subscriptionManager, FetchAggMetrics)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsSparkJobMetrics = WebSocket.acceptOrResult[AppStateWSReq, Seq[JobState]] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => SparkEventSubscriber.props(subscriptionManager, FetchJobMetrics)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsSparkStageMetrics = WebSocket.acceptOrResult[SparkStageWSReq, Seq[StageState]] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => SparkStageEventSubscriber.props(subscriptionManager)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsSparkExecutorMetrics = WebSocket.acceptOrResult[AppStateWSReq, ExecutorMetricResponse] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => SparkExecutorMetricSubscriber.props(subscriptionManager)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsSparkLogSearch = WebSocket.acceptOrResult[SparkLogWSReq, LogsSearchResponse] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => LogsIndexSearcher.props(sparkEventService)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsSparkListExecutorMetrics = WebSocket.acceptOrResult[ExecutorWSReq, Seq[ExecutorMetricSummary]] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => SparkRuntimeMetricsSub.props(sparkEventService)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def wsSparkDetailedExecutorMetrics = WebSocket.acceptOrResult[ExecutorWSReq, ExecutorRuntimeMetric] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
      }
      .map {
        case HandlerResult(r, Some(_)) =>
          Right(ActorFlow.actorRef(out => SparkRuntimeMetricsSub.props(sparkEventService)(out)))
        case HandlerResult(r, None) => Left(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
      .recover {
        case e: Exception =>
          val result = InternalServerError(e.getMessage)
          Left(result)
      }
  }

  def getExecutorMetrics(jobId: Long, runId: Long, attemptId: String) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        sparkEventService
          .getExecutorMetrics(runId, attemptId)
          .map(opt =>
            opt match {
              case None        => NotFound(Json.toJson(EntityNotFound("Spark Executor metrics", request.path, mv.memberId)))
              case Some(value) => Ok(Json.toJson(value))
          })
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def getExecutorRuntimeMetrics(jobId: Long, runId: Long, executorId: String) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        sparkEventService
          .getExecutorRuntimeMetrics(jobId, runId, executorId)
          .map(opt =>
            opt match {
              case None        => NotFound(Json.toJson(EntityNotFound("Executor runtime metrics", request.path, mv.memberId)))
              case Some(value) => Ok(Json.toJson(value))
          })
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def listExecutorSummaries(jobId: Long, runId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        sparkEventService.listExecutorSummaries(jobId, runId).map(value => Ok(Json.toJson(value)))
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }

  }

  def getSparkLogs(jobId: Long, runId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        val timezone   = request.headers.get(Headers.USER_TIMEZONE)
        val lastOffset = request.headers.get(Headers.LAST_OFFSET).map(_.toLong)
        sparkEventService.getLogs(jobId, runId, timezone, lastOffset.getOrElse(0L)).map(r => Ok(Json.toJson(r)))
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def searchSparkLogs(jobId: Long, runId: Long) = silhouette.UserAwareAction.async(validateJson[LogSearchRequest]) { implicit request =>
    handleRequestWithOrgAndJobs(request, memberService, userCache, jobId, jobCache, jobService) { (mv, jv) =>
      if (jv.runs.exists(_.runId == runId)) {
        val timezone = request.headers.get(Headers.USER_TIMEZONE)
        sparkEventService.searchLogs(jobId, runId, timezone, request.body).map(r => Ok(Json.toJson(r)))
      } else {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }
}

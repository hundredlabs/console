package web.controllers.api

import akka.actor.ActorRef
import akka.stream.Materializer
import com.gigahex.commons.constants.Headers
import com.gigahex.commons.events.ApplicationStarted
import com.gigahex.commons.models.{AddMetricRequest, ClusterIdResponse, ClusterState, DeploymentActionUpdate, DeploymentUpdate, GxAppState, NewCluster, NewExecutor, RunStatus}
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.api.util.Clock
import controllers.AssetsFinder
import javax.inject.{Inject, Named}
import web.actors.AlertsManager.NotifyScheduler
import web.models.{ActionForbidden, AuthRequestsJsonFormatter, ClusterJsonFormat, DeploymentJsonFormat, EventsFormat, InternalServerErrorResponse, JobFormats, JobModified, JobRequestResponseFormat, OrgResponse, OrgWithKeys, SignInResponse, UserNotAuthenticated, WorkspaceId, WorkspaceResponse}
import web.models.common.JobErrorJson
import web.providers.{APICredentialsProvider, WorkspaceAPICredentialsProvider}
import web.services.{ClusterService, DeploymentService, JobService, SparkEventService}
import web.utils.DateUtil
import play.api.{Configuration, Logging}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents, InjectedController, Request}
import play.cache.NamedCache
import utils.auth.{APIJwtEnv, WorkspaceAPIJwtEnv}
import web.controllers.handlers.SecuredAPIReqHander
import web.models.formats.AuthResponseFormats

import scala.concurrent.{ExecutionContext, Future}

class WorkspaceApiController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[WorkspaceAPIJwtEnv],
    credentialsProvider: WorkspaceAPICredentialsProvider,
    sparkEventService: SparkEventService,
    deploymentService: DeploymentService,
    clusterService: ClusterService,
    configuration: Configuration,
    clock: Clock
)(
    implicit
    assets: AssetsFinder,
    mat: Materializer,
    ex: ExecutionContext
) extends InjectedController
    with I18nSupport
    with AuthRequestsJsonFormatter
    with AuthResponseFormats
    with JobRequestResponseFormat
    with ClusterJsonFormat
    with JobFormats
    with JobErrorJson
    with Logging
    with EventsFormat
    with SecuredAPIReqHander {

  implicit val runStatusFmt            = Json.formatEnum(RunStatus)
  implicit val deploymentUpdateFmt     = Json.format[DeploymentUpdate]
  implicit val deploymentLogsUpdateFmt = Json.format[DeploymentActionUpdate]

  def validateJson[A: Reads]: BodyParser[A] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )
  def apiLogin: Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    val clientKey = request.headers.get(Headers.AUTH_ACCESS_KEY)
    val message   = request.headers.get(Headers.AUTH_REQ_ACTION).flatMap(x => clientKey.map(k => k + "," + x))
    val signature = request.headers.get(Headers.AUTH_CLIENT_SIGN)
    (clientKey, message, signature) match {
      case (Some(key), Some(msg), Some(sign)) =>
        credentialsProvider
          .authenticate(sign, msg, key)
          .flatMap(o => authenticateWorkspace(o))
          .recover {
            case e: Exception => Unauthorized(Json.toJson(SignInResponse(false, "", message = e.getMessage)))
          }
      case _ => Future.successful(Unauthorized(Json.toJson(SignInResponse(false, "", message = "Missing auth headers"))))
    }

  }

  private def authenticateWorkspace(w: WorkspaceId)(implicit request: Request[_]): Future[AuthenticatorResult] = {
    val result = Ok(Json.toJson(WorkspaceResponse(w.id, w.name)))
    silhouette.env.authenticatorService
      .create(LoginInfo(APICredentialsProvider.ID, w.key))
      .map(authenticator => authenticator)
      .flatMap { authenticator =>
        //silhouette.env.eventBus.publish(LoginEvent(user, request))
        silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
          silhouette.env.authenticatorService.embed(v, result)
        }
      }
  }

  /**
    * This method persists the JVM metrics being received from Spark executors
    * @return
    */
  def runtimeExecutorsMetric =
    silhouette.UserAwareAction.async(validateJson[AddMetricRequest]) { implicit request =>
      val runIdOpt = request.headers.get(Headers.GIGAHEX_JOB_RUN_ID)

      request.identity match {
        case None => Future.successful(Forbidden)
        case Some(WorkspaceId(id, _, _, _, _)) =>
          sparkEventService
            .saveRuntimeMetrics(id, runIdOpt.getOrElse("0").toLong, request.body)
            .map(r => Ok(Json.parse(s"""{"update": ${r}}""")))
      }
    }

  /**
    * Every new executor is saved
    * @return
    */
  def newSparkExecutorMetric =
    silhouette.UserAwareAction.async(validateJson[NewExecutor]) { implicit request =>
      request.identity match {
        case None => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
        case Some(_) =>
          val result = sparkEventService.addExecutorMetric(request.body)
          result.map(r => Ok(Json.parse(s"""{"executorAdded": ${r}}""")))
      }
    }

  def updateDeploymentLogs = silhouette.UserAwareAction.async(validateJson[DeploymentActionUpdate]) { implicit request =>
    request.identity match {
      case None => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      case Some(_) =>
        val update = request.body
        val result = deploymentService.updateActionStatus(update.runId, update.actionId, update.status, update.logs, configuration)
        result.map(r => Ok(Json.parse(s"""{"executorAdded": ${r}}""")))
    }
  }

  def updateSparkAppStatus(runId: Long) = silhouette.UserAwareAction.async(validateJson[ApplicationStarted]) { implicit request =>
    request.identity match {
      case None => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      case Some(_) =>
        val tz = request.headers.get(Headers.USER_TIMEZONE)
        sparkEventService
          .appStarted(runId, request.body, tz.getOrElse("GMT"))
          .map(r => Ok(Json.parse(s"""{"updated": ${r}}""")))

    }
  }

  def pushSparkMetrics: Action[GxAppState] =
    silhouette.UserAwareAction.async(validateJson[GxAppState]) { implicit request =>
      request.identity match {
        case None => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
        case Some(_) =>
          val tz     = request.headers.get(Headers.USER_TIMEZONE).getOrElse("GMT")
          val result = sparkEventService.publishAppState(request.body.runId, request.body.metric, tz)
          result.map(r => Ok(Json.parse(s"""{"lastJobUpdated": $r}""")))
      }
    }

  def registerCluster: Action[NewCluster] = silhouette.UserAwareAction.async(validateJson[NewCluster]) { implicit request =>
    request.identity match {
      case None => Future.successful(Forbidden)
      case Some(WorkspaceId(id, _, _, _, _)) =>
        for {
          orgId <- clusterService.getOrgId(id)
          result <- orgId match {
            case None => Future.successful(BadRequest(Json.toJson(Map("error" -> "No org found"))))
            case Some(oId) =>
              clusterService.addCluster(oId, id, request.body).map { response =>
                response.fold(
                  ex => BadRequest(Json.toJson(Map("error" -> ex.getMessage))),
                  clusterId => Created(Json.toJson(ClusterIdResponse(clusterId)))
                )
              }
          }
        } yield result

    }
  }

  def getDeploymentWork(clusterId: Long) = silhouette.UserAwareAction.async { implicit request =>
    request.identity match {
      case None => Future.successful(Forbidden)
      case Some(WorkspaceId(id, _, _, _, _)) =>
        deploymentService.getDeploymentRunInstance(id, clusterId).map {
          case None        => NotFound
          case Some(value) => Ok(Json.toJson(value))
        }
    }
  }

  /**
    * Update the deployment status once the work completes by the agent running in the cluster/host machine
    * @return
    */
  def updateDeploymentRun = silhouette.UserAwareAction.async(validateJson[DeploymentUpdate]) { implicit request =>
    request.identity match {
      case None => Future.successful(Forbidden)
      case Some(WorkspaceId(id, _, _, _, _)) =>
        deploymentService.updateDeploymentRun(request.body.runId, request.body.status).map { r =>
          Ok(Json.parse(s"""{"deploymentJobUpdated": ${r}}"""))
        }
    }

  }

  def saveClusterState: Action[ClusterState] = silhouette.UserAwareAction.async(validateJson[ClusterState]) { implicit request =>
    request.identity match {
      case None => Future.successful(Forbidden)
      case Some(WorkspaceId(id, _, _, _, _)) =>
        clusterService.saveClusterState(id, request.body).map { saved =>
          if (saved) Ok else BadRequest
        }
    }
  }

}

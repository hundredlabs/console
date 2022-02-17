package web.controllers.api

import akka.actor.ActorRef
import com.gigahex.commons.constants.Headers
import com.gigahex.commons.models.ErrorType
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util.{Clock, Credentials}
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import controllers.AssetsFinder
import javax.inject.{Inject, Named, Singleton}
import play.api.cache.SyncCacheApi
import play.api.http.HeaderNames
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Reads, _}
import play.api.mvc.{ControllerComponents, InjectedController, Request, RequestHeader}
import play.api.{Configuration, Logging}
import play.cache.NamedCache
import utils.auth.APIJwtEnv
import web.controllers.handlers.SecuredAPIReqHander
import web.models.common.JobErrorJson
import web.models._
import web.models.formats.AuthResponseFormats
import web.providers.APICredentialsProvider
import web.services.JobService
import web.utils.DateUtil

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JobApiController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[APIJwtEnv],
    credentialsProvider: APICredentialsProvider,
    jobService: JobService,
    configuration: Configuration,
    clock: Clock
)(
    implicit
    assets: AssetsFinder,
    ex: ExecutionContext
) extends InjectedController
    with I18nSupport
    with AuthRequestsJsonFormatter
    with AuthResponseFormats
    with JobRequestResponseFormat
    with JobFormats
    with JobErrorJson
    with Logging
    with SecuredAPIReqHander {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )
  def apiLogin = silhouette.UnsecuredAction.async { implicit request =>
    val signInReq = request.body
    val clientKey = request.headers.get(Headers.AUTH_ACCESS_KEY)
    val message   = request.headers.get(Headers.AUTH_REQ_ACTION).flatMap(x => clientKey.map(k => k + "," + x))
    val signature = request.headers.get(Headers.AUTH_CLIENT_SIGN)
    (clientKey, message, signature) match {
      case (Some(key), Some(msg), Some(sign)) =>
        credentialsProvider
          .authenticate(sign, msg, key)
          .flatMap(o => authenticateOrg(o))
          .recover {
            case e: Exception => Unauthorized(Json.toJson(SignInResponse(false, "", message = e.getMessage)))
          }
      case _ => Future.successful(Unauthorized(Json.toJson(SignInResponse(false, "", message = "Missing auth headers"))))
    }

  }

  private[this] def getCredentials(request: RequestHeader): Option[Credentials] = {
    request.headers.get(HeaderNames.AUTHORIZATION) match {
      case Some(header) if header.startsWith("Basic ") =>
        Base64.decode(header.replace("Basic ", "")).split(":", 2) match {
          case credentials if credentials.length == 2 => Some(Credentials(credentials(0), credentials(1)))
          case _                                      => None
        }
      case _ => None
    }
  }

  def submitJob(id: Long) = silhouette.UserAwareAction.async(validateJson[TaskMeta]) { implicit request =>
    handleRequestWithJob(request, id, jobService, jobCache) {
      case (_, jv) =>
        jobService.addJobRun(jv.jobId, request.body, request.headers.get(Headers.USER_TIMEZONE)).map { r =>
          //Update the cache
          jobService.updateJobCache(id, jobCache, true)
          Ok(Json.toJson(r))
        }
    }
  }

  def getProjectByName = silhouette.UserAwareAction.async(validateJson[ProjectByName]) { implicit request =>
    handleRequest(request) { org =>
      jobService.getProjectByName(org.orgId, request.body.name).map { r =>
        r match {
          case None        => NotFound
          case Some(value) => Ok(Json.parse(s"""{"projectId": ${value}}"""))
        }

      }
    }
  }

  def updateJobStatus(jobId: Long, runId: Long) = silhouette.UserAwareAction.async(validateJson[UpdateJobStatus]) { implicit request =>
    handleRequestWithJob(request, jobId, jobService, jobCache) {
      case (_, jv) =>
        if (jv.runs.find(run => run.runId == runId).isDefined) {
          val runSnip         = jv.runs.find(_.runId == runId).get
          val timeLimitInsecs = configuration.get[Int]("metrics.maxTimeInSecs")
          val forbiddenResponse = Forbidden(Json.toJson(
            ActionForbidden(request.path, s"Runtime of the application exceeded the limit of ${DateUtil.formatInterval(timeLimitInsecs)}")))
          val tz = request.headers.get(Headers.USER_TIMEZONE).getOrElse("GMT")
          runSnip.status match {
            case com.gigahex.commons.models.RunStatus.TimeLimitExceeded => Future.successful(forbiddenResponse)
            case _                                                      => jobService.updateJobStatus(request.body.status, runId, tz).map(r => Ok(Json.toJson(r)))
          }

        } else {
          Future.successful(Forbidden(Json.toJson(JobModified(jobId, false, "No matching run found for this job."))))
        }
    }
  }

  def publishError(jobId: Long, runId: Long, attemptId: String) = silhouette.UserAwareAction.async(validateJson[ErrorType]) {
    implicit request =>
      handleRequestWithJob(request, jobId, jobService, jobCache) {
        case (_, jv) =>
          if (jv.runs.exists(run => run.runId == runId)) {
            jobService.saveError(runId, attemptId, request.body).map(r => Ok(Json.toJson(r)))
          } else {
            Future.successful(Forbidden(Json.toJson(JobModified(jobId, false, "No matching run found for this job."))))
          }
      }
  }

  def updateTaskStatus(jobId: Long, runId: Long, seqId: Int) =
    silhouette.UserAwareAction.async(validateJson[UpdateJobStatus]) { implicit request =>
      handleRequestWithJob(request, jobId, jobService, jobCache) {
        case (org, jv) =>
          jobService
            .updateTaskStatus(org.orgId, jv.jobId, runId, seqId, request.body.status)
            .map(r => Ok(Json.toJson(r)))
      }
    }

  private def authenticateOrg(org: OrgWithKeys)(implicit request: Request[_]): Future[AuthenticatorResult] = {
    val result = Ok(Json.toJson(OrgResponse(org.orgId, org.orgName)))
    silhouette.env.authenticatorService
      .create(LoginInfo(APICredentialsProvider.ID, org.key))
      .map {
        case authenticator => authenticator
      }
      .flatMap { authenticator =>
        //silhouette.env.eventBus.publish(LoginEvent(user, request))
        silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
          silhouette.env.authenticatorService.embed(v, result)
        }
      }
  }

}

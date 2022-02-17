package web.controllers.spark

import akka.util.Timeout
import com.gigahex.commons.constants.Headers
import com.gigahex.commons.models.{AddMetricRequest, DriverLogs, RunStatus}
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.util.Clock
import controllers.AssetsFinder
import javax.inject.{Inject, Singleton}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{Action, ControllerComponents, InjectedController}
import play.api.{Configuration, Logging}
import play.cache.NamedCache
import utils.auth.APIJwtEnv
import web.controllers.handlers.SecuredAPIReqHander
import web.models._
import web.models.formats.AuthResponseFormats
import web.services.{JobService, SparkEventService}
import web.utils.{DateUtil, LogParser}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SparkEventsController @Inject()(
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[APIJwtEnv],
    jobService: JobService,
    sparkEventService: SparkEventService,
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
    with EventsFormat
    with Logging
    with SecuredAPIReqHander {

  def validateJson[A: Reads] = {

    parse.json.validate(
      _.validate[A].asEither.left.map { e =>
        BadRequest(JsError.toJson(e))
      }
    )
  }
  implicit val timeout = Timeout(2.second)

  def saveDriverLogs: Action[DriverLogs] = silhouette.UserAwareAction.async(validateJson[DriverLogs]) { implicit request =>
    request.identity match {
      case None => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      case Some(_) =>
        val jobHeaders     = Set(Headers.GIGAHEX_JOB_ID, Headers.GIGAHEX_JOB_RUN_ID)
        val checkedHeaders = request.headers.keys.intersect(jobHeaders)
        if (checkedHeaders.size == 2) {

          val jobId = request.headers.get(Headers.GIGAHEX_JOB_ID).get.toLong
          val runId = request.headers.get(Headers.GIGAHEX_JOB_RUN_ID).get.toLong

          val jv = jobService.updateJobCache(jobId, jobCache)
          if (jv.runs.count(_.runId == runId) > 0) {
            val runSnip         = jv.runs.find(_.runId == runId).get
            val timeLimitInsecs = configuration.get[Int]("logs.maxTimeInSecs")
            val forbiddenResponse = Forbidden(
              Json.toJson(
                ActionForbidden(request.path, s"Runtime of the application exceeded the limit of ${DateUtil.formatInterval(timeLimitInsecs)}")))
            runSnip.status match {
              case com.gigahex.commons.models.RunStatus.TimeLimitExceeded => Future.successful(forbiddenResponse)
              case _ if (System.currentTimeMillis() / 1000 - runSnip.startedTimestamp > timeLimitInsecs) =>
                val tz = request.headers.get(Headers.USER_TIMEZONE).getOrElse("GMT")
                jobService.updateJobStatus(RunStatus.TimeLimitExceeded.toString, runId, tz)
                jobService.updateJobCache(jobId, jobCache, forceUpdate = true)
                Future.successful(forbiddenResponse)
              case _ =>
                val filteredLogs = request.body.lines.map(l => LogParser.parse(l)).filter(_.isDefined).map(_.get)
                val result       = sparkEventService.indexLogs(jobId, runId, filteredLogs)
                result.map(r => Ok(Json.parse(s"""{"logsLen": $r}""")))
            }
          } else {
            Future.successful(Forbidden(Json.toJson(ActionForbidden(request.path))))
          }

        } else {
          Future.successful(BadRequest(Json.parse(s"""{"logsLen": 0}""")))
        }

    }
  }


  /**
    * This method persists the JVM metrics being received from Spark executors
    * @return
    */
  def runtimeExecutorsMetric =
    silhouette.UserAwareAction.async(validateJson[AddMetricRequest]) { implicit request =>
      val jobIdOpt = request.headers.get(Headers.GIGAHEX_JOB_ID)
      val runIdOpt = request.headers.get(Headers.GIGAHEX_JOB_RUN_ID)

      (jobIdOpt, runIdOpt) match {
        case (Some(jIdStr), Some(rIdStr)) =>
          handleRequestWithJob(request, jIdStr.toLong, jobService, jobCache) { (org, jv) =>
            if (jv.runs.find(run => run.runId == rIdStr.toLong).isDefined) {

              val runSnip         = jv.runs.find(_.runId == rIdStr.toLong).get
              val timeLimitInsecs = configuration.get[Int]("metrics.maxTimeInSecs")
              val forbiddenResponse = Forbidden(
                Json.toJson(
                  ActionForbidden(request.path, s"Runtime of the application exceeded the limit of ${DateUtil.formatInterval(timeLimitInsecs)}")))
              runSnip.status match {
                case com.gigahex.commons.models.RunStatus.TimeLimitExceeded => Future.successful(forbiddenResponse)
                case _ if (System.currentTimeMillis() / 1000 - runSnip.startedTimestamp > timeLimitInsecs) =>
                  val tz = request.headers.get(Headers.USER_TIMEZONE).getOrElse("GMT")
                  jobService.updateJobStatus(RunStatus.TimeLimitExceeded.toString, rIdStr.toLong, tz)
                  jobService.updateJobCache(jIdStr.toLong, jobCache, forceUpdate = true)
                  Future.successful(forbiddenResponse)
                case _ =>
                  sparkEventService
                    .saveRuntimeMetrics(jIdStr.toLong, rIdStr.toLong, request.body)
                    .map(r => Ok(Json.parse(s"""{"update": ${r}}""")))
              }

            } else {
              Future.successful(Forbidden(Json.toJson(JobModified(jIdStr.toLong, success = false, "No matching task found for this Spark metrics."))))
            }
          }
        case _ => Future.successful(BadRequest)
      }

    }





}

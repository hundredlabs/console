package web.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{GxAppState, GxApplicationMetricReq, RunStatus}
import web.actors.EmailSender.{MailSent, SendAlert}
import web.actors.JobAlertScheduler.{AlertDetail, AlertKey, CheckAppStatus}
import web.models.{AlertConfig, SparkMetric}
import web.services.SparkEventService
import play.api.libs.mailer.MailerClient

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class JobAlertScheduler(alertConfig: AlertConfig, eventService: SparkEventService, mailerClient: MailerClient, alertEmailSender: String)
    extends Actor
    with ActorLogging
    with AlertRulesHandler {

  implicit val ec                   = context.dispatcher
  val emailDispatcher: ActorRef = context.actorOf(EmailSender.props(mailerClient, alertEmailSender, alertConfig.subscribers))

  override def preStart(): Unit = {
    log.info(s"Starting the job alert scheduler for ${alertConfig.name}")
  }


  private def manageAlerts(alertHistory: Map[AlertKey, AlertDetail], appStatusWatcher: Option[Cancellable] = None): Receive = {
    case GxAppState(runId, metric) =>
      val generatedAlerts = generateAlerts(runId, metric)
        val jobId = 0L
      val alerts = generatedAlerts.filter { p =>
        alertHistory
          .get(AlertKey(0L, runId,  p.attemptId, p.metric))
          .isEmpty
      }
      alerts.foreach { a =>

        emailDispatcher ! SendAlert(jobId, runId,  metric.appName, a)
      }

      val newAlerts = alerts.map(a => AlertKey(jobId, runId,  a.attemptId, a.metric) -> a)
      val newAppStatusWatcher = if (metric.status.equalsIgnoreCase(RunStatus.Completed.toString) && appStatusWatcher.isEmpty) {
        val watcher = context.system.scheduler.schedule(1.seconds, 2.seconds, self, CheckAppStatus(jobId, runId, metric))
        Some(watcher)
      } else {
        if ((metric.status.equalsIgnoreCase(RunStatus.Succeeded.toString) || metric.status.equalsIgnoreCase(RunStatus.Failed.toString)) && (appStatusWatcher.isDefined)) {

          appStatusWatcher.get.cancel()
          None
        } else appStatusWatcher
      }
      context.become(manageAlerts(alertHistory = alertHistory ++ newAlerts, newAppStatusWatcher))

    case CheckAppStatus(jobId, runId, metricReq) =>
      eventService.getSparkAppStatus(jobId, runId,  metricReq.appId).onComplete {
        case Failure(exception) => logger.error(s"Failed while fetching the app status - ${exception.getMessage}")
        case Success(value) =>
          value match {
            case None        => logger.info("No app found")
            case Some(value) => self ! GxAppState(runId, metricReq.copy(status = value.toString))
          }
      }

    case MailSent(id, success) => log.info(s"Mail sent - $success, with id - ${id.getOrElse("...")}")

  }

  private def generateAlerts(jobRunId: Long, metric: GxApplicationMetricReq): Seq[AlertDetail] = {
    alertConfig.rule.metric match {
        case web.models.SparkMetric.AppRuntime           => handleAppRuntime(alertConfig.rule, metric)
        case web.models.SparkMetric.TotalExecutorRuntime => handleTotalExecRuntime(alertConfig.rule, metric)
        case web.models.SparkMetric.TotalJVMGCtime       => handleTotalJVMGCtime(alertConfig.rule, metric)
        case web.models.SparkMetric.MaxTaskRuntime       => handleMaxTaskRuntime(alertConfig.rule, metric)
        case web.models.SparkMetric.AppStatus            => handleAppStatus(alertConfig.rule, metric)
      }

  }
  override def receive: Receive = manageAlerts(Map.empty)

}

object JobAlertScheduler {

  case class AlertKey(jobId: Long, jobRunId: Long,  attemptId: String, metric: SparkMetric.Value)
  case class AlertDetail(attemptId: String, metric: SparkMetric.Value, alertMessage: String, appFinalStatus: Option[AppFinalStatus] = None)
  case class CheckAppStatus(jobId: Long, runId: Long, metricReq: GxApplicationMetricReq)
  case class AppFinalStatus(status: RunStatus)
  def props(alertConfig: AlertConfig, eventService: SparkEventService, mailerClient: MailerClient, alertEmailSender: String): Props =
    Props(new JobAlertScheduler(alertConfig, eventService, mailerClient, alertEmailSender))

}

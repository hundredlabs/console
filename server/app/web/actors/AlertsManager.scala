package web.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import com.gigahex.commons.models.GxAppState
import javax.inject.Inject
import web.actors.AlertsManager.{AlertJobInfo, DeleteAlertConfig, NewAlertConfig, NotifyScheduler, RegenerateAlertScheduler, UpdateAlertConfig}
import web.models.{AlertConfig, SparkJobRunInstance}
import web.services.{AlertEngine, SparkEventService}
import play.api.Configuration
import play.api.libs.mailer.MailerClient
import scalikejdbc.config.DBs

import scala.util.{Failure, Success}

class AlertsManager @Inject()(eventService: SparkEventService, alertEngine: AlertEngine, mailerClient: MailerClient, configuration: Configuration)
    extends Actor
    with ActorLogging {

  implicit val ec = context.dispatcher

  override def preStart(): Unit = {
    DBs.setupAll()
    alertEngine.listAll.onComplete {
      case Failure(exception) => log.error(s"Unable to initialize the alerts - ${exception.getMessage}")
      case Success(alertConfigs) =>
        log.info(s"Found alert configs - ${alertConfigs.size}")
        alertConfigs.foreach(config => self ! NewAlertConfig(config))
    }
  }

  private[this] def manageAlertSchedulers(schedulers: Map[AlertJobInfo, ActorRef]): Receive = {
    case NewAlertConfig(config) =>
      log.info(s"New alert rule configured with id - ${config.alertId}")
      val alertScheduler = getAlertsScheduler(config.jobId, config.alertId)
      alertScheduler match {
        case None =>
          val newScheduler = getAlertsScheduler(config, eventService)
          context.become(
            manageAlertSchedulers(schedulers = schedulers ++ Map(AlertJobInfo(config.jobId, config.alertId) -> newScheduler)))
        case Some(oldScheduler) =>
          context.watchWith(oldScheduler, RegenerateAlertScheduler(config))
          oldScheduler ! PoisonPill
      }


    case RegenerateAlertScheduler(config) =>
      log.info(s"Recreated scheduler id - ${config.alertId}")
      val alertScheduler = getAlertsScheduler(config, eventService)
      context.become(
        manageAlertSchedulers(schedulers = schedulers ++ Map(AlertJobInfo(config.jobId, config.alertId) -> alertScheduler)))

    case UpdateAlertConfig(config) =>
      //get the existing and kill it
      val oldScheduler = getAlertsScheduler(config.jobId, config.alertId)
      oldScheduler match {
        case None =>
          self ! NewAlertConfig(config)
        case Some(oldAlertScheduler) =>
          log.info("Killing and regenerating the alert scheduler")
          context.watchWith(oldAlertScheduler, RegenerateAlertScheduler(config))
          oldAlertScheduler ! PoisonPill

      }

    case DeleteAlertConfig(jobId, alertId) =>
      //get the existing and kill it

      val mayBeScheduler = getAlertsScheduler(jobId, alertId)
      mayBeScheduler match {
        case None => log.info("No scheduler found for deletion")
        case Some(scheduler) =>
          log.info("Killing alert scheduler")
          scheduler ! PoisonPill
          context.become(manageAlertSchedulers(schedulers = schedulers.filter {
            case (info, _) => !(info.jobId == jobId && info.alertId == alertId)
          }))

      }

    case NotifyScheduler(jobId, appState) =>
      schedulers.filter {
        case (info, _) => info.jobId == jobId
      } foreach {
        case (i, ref) =>
          ref ! appState
      }
  }

  override def receive: Receive = manageAlertSchedulers(Map.empty)

  private[this] def getAlertsScheduler(jobId: Long, alertId: Long): Option[ActorRef] = {
    val actorName = s"alert-scheduler-${jobId}-${alertId}"
    context.child(actorName)
  }

  private[this] def getAlertsScheduler(config: AlertConfig, eventService: SparkEventService): ActorRef = {
    val actorName = s"alert-scheduler-${config.jobId}-${config.alertId}"
    context.child(actorName) match {
      case None        => context.actorOf(JobAlertScheduler.props(config, eventService, mailerClient, configuration.underlying.getString("alert.email")), actorName)
      case Some(actor) => actor
    }
  }

}

object AlertsManager {
  case class AlertJobInfo(jobId: Long, alertId: Long)
  case class RegenerateAlertScheduler(config: AlertConfig)
  case class NotifyScheduler(jobId: Long, appState: GxAppState)
  case class NewAlertConfig(config: AlertConfig)
  case class UpdateAlertConfig(config: AlertConfig)
  case class DeleteAlertConfig(jobId: Long, alertId: Long)
  def props(eventService: SparkEventService, alertEngine: AlertEngine, mailerClient: MailerClient, configuration: Configuration): Props =
    Props(new AlertsManager(eventService, alertEngine, mailerClient, configuration))
}

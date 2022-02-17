package web.actors

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.gigahex.commons.events._
import javax.inject.Inject
import web.actors.EventSubscriptionManager.{
  AddEventSubscriber,
  AddExecutorMetricSubscriber,
  AddStageEventSubscriber,
}
import web.actors.SparkEventsPublisher.{ExecMetricSubscriberAdded, JobSubscriberAdded, StageSubscriberAdded, TaskSubscriberAdded}
import web.services.MonitorResolver.UpdateAppMetrics
import web.services.{MonitorResolver, SparkEventService}

class EventSubscriptionManager @Inject()(eventService: SparkEventService) extends Actor with ActorLogging {


  override def receive: Receive = {
    case message: UpdateAppMetrics =>
      getEventPublisherActor(message.jobId, message.runId, message.taskEvent.appAttemptId) ! message.taskEvent

    case event: AddStageEventSubscriber =>
      getEventPublisherActor(event.jobId, event.runId, event.appAttemptId) ! StageSubscriberAdded(
        event.subId,
        sender(),
        event.sparkParentJobId
      )

    case event: AddExecutorMetricSubscriber =>
      getEventPublisherActor(event.jobId, event.runId, event.appAttemptId) ! ExecMetricSubscriberAdded(
        event.subId,
        sender()
      )

    case event: AddEventSubscriber =>
      val publisher = getEventPublisherActor(event.jobId, event.runId, event.appAttemptId)
      event.eventType match {
        case FetchAggMetrics => publisher ! TaskSubscriberAdded(s"task-${new Date().getTime}", sender())
        case FetchJobMetrics => publisher ! JobSubscriberAdded(s"job-${new Date().getTime}", sender())
      }

  }

  private def getEventPublisherActor(jobId: Long, runId: Long, appAttemptId: String): ActorRef = {
    val pubId = EventSubscriptionManager.buildPublisherId(jobId, runId, appAttemptId)
    context.child(pubId) match {
      case None =>
        context.actorOf(
          SparkEventsPublisher.props(jobId, runId, appAttemptId, eventService),
          pubId
        )
      case Some(ref) => ref
    }
  }

}

object EventSubscriptionManager {
  trait EventMessage {
    val jobId: Long
    val runId: Long
    val appAttemptId: String

    def getPublisherId: String = buildPublisherId(jobId, runId, appAttemptId)
  }

  sealed trait SparkMeta {
    val event: SparkEvents
  }
  //The below 3 messages are being sent by the controller layer, post updating the status in the database
  case class EventAppStarted(jobId: Long, runId: Long, appAttemptId: String, event: ApplicationStarted) extends EventMessage with SparkMeta
  case class EventAppEnded(jobId: Long, runId: Long, appAttemptId: String, event: ApplicationEnded)     extends EventMessage with SparkMeta
  case class EventJobStarted(jobId: Long, runId: Long, appAttemptId: String, event: JobStarted)         extends EventMessage with SparkMeta
  case class EventJobCompleted(jobId: Long, runId: Long, appAttemptId: String, event: JobEnded)         extends EventMessage with SparkMeta
  case class EventStageCompleted(jobId: Long, runId: Long, appAttemptId: String, event: StageCompleted) extends EventMessage with SparkMeta
  case class EventStageStarted(jobId: Long, runId: Long, appAttemptId: String, event: StageSubmitted)   extends EventMessage with SparkMeta

  case class EventTaskStarted(jobId: Long, runId: Long, appAttemptId: String, event: TaskStarted)     extends EventMessage with SparkMeta
  case class EventTaskCompleted(jobId: Long, runId: Long, appAttemptId: String, event: TaskCompleted) extends EventMessage with SparkMeta
  //This message is being sent by the actor created by the websocket
  case class AddEventSubscriber(jobId: Long, runId: Long, appAttemptId: String, eventType: EventType)
  case class AddStageEventSubscriber(jobId: Long, runId: Long, appAttemptId: String, sparkParentJobId: Int, subId: String)
  case class AddExecutorMetricSubscriber(jobId: Long, runId: Long, appAttemptId: String, subId: String)

  def buildPublisherId(jobId: Long, runId: Long, appAttemptId: String): String =
    s"publisher-${jobId}-${runId}-${appAttemptId}"

  def buildMetricsAggId(jobId: Long, runId: Long, appAttemptId: String): String =
    s"metrics-${jobId}-${runId}-${appAttemptId}"

  def buildBuncherId(jobId: Long, runId: Long, appAttemptId: String): String =
    s"buncher-${jobId}-${runId}-${appAttemptId}"
  def props(eventService: SparkEventService): Props = Props(new MonitorResolver(eventService))
}

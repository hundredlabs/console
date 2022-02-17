package web.services

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.gigahex.commons.events.TaskCompleted
import web.actors.SparkEventsPublisher
import web.services.MonitorResolver.UpdateAppMetrics

class MonitorResolver(eventService: SparkEventService) extends Actor with ActorLogging{

  override def preStart(): Unit = {
    log.info("Starting the Monitor resolver")
  }

  override def receive: Receive = {
    case message: UpdateAppMetrics =>
      context.child(message.actorName) match {
        case None =>
          val metricsActor = context.actorOf(
            SparkEventsPublisher.props(message.jobId,
                                         message.runId,

                                         message.taskEvent.appAttemptId,
                                         eventService))

          metricsActor ! message.taskEvent
        case Some(ref) => ref ! message.taskEvent
      }
  }

}

object MonitorResolver {
  case class UpdateAppMetrics(jobId: Long, runId: Long,  taskEvent: TaskCompleted) {
    val actorName: String = s"monitor-${jobId}-${runId}-${taskEvent.appAttemptId}"
  }

  case class AddEventSubscriber(subscriber: ActorRef, jobId: Long, runId: Long, appAttemptId: String)

  case class ShutdownMetrics()
  def props(eventService: SparkEventService): Props = Props(new MonitorResolver(eventService))
}

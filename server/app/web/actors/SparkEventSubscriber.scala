package web.actors

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import web.models.spark.{AppStateWSReq, ExecutorMetricResponse, JobState, SparkAggMetrics, SparkStageWSReq, StageState}
import web.actors.EventSubscriptionManager.{AddEventSubscriber, AddExecutorMetricSubscriber, AddStageEventSubscriber}
import web.actors.SparkEventSubscriber.{SparkJobEvent, SparkStageEvent, SubscriptionRequest}

sealed trait EventType
case object FetchAggMetrics   extends EventType
case object FetchJobMetrics   extends EventType


class SparkEventSubscriber(subscriptionManager: ActorRef, eventType: EventType, out: ActorRef)
    extends Actor
    with ActorLogging {


  override def receive: Receive = {
    case AppStateWSReq(jobId, runId,  appAttemptId, _) =>
      subscriptionManager ! AddEventSubscriber(jobId.toLong, runId.toLong, appAttemptId, eventType)

    case message: SparkAggMetrics =>
      eventType match {
        case FetchAggMetrics =>
          out ! message
        case x: EventType => log.info(s"${x.toString} - agg metrics updated ")
      }

    case SparkJobEvent(_, jobs) =>
      eventType match {
        case FetchJobMetrics => out ! jobs
        case _               => log.info("Job state fetched")
      }

  }

}

object SparkEventSubscriber {
  case class SubscriptionRequest(jobId: Long, runId: Long,  appAttemptId: String)
  case class SparkJobEvent(appAttemptId: String, jobs: Seq[JobState])
  case class SparkStageEvent(appAttemptId: String, stages: Seq[StageState])
  def props(subscriptionManager: ActorRef, eventType: EventType)(out: ActorRef): Props =
    Props(new SparkEventSubscriber(subscriptionManager, eventType, out))
}

class SparkStageEventSubscriber(subscriptionManager: ActorRef, out: ActorRef) extends Actor with ActorLogging {

  val subId = s"stage-${new Date().getTime}"
  override def receive: Receive = {
    case SparkStageWSReq(jobId, runId,  appAttemptId, sparkJobId) =>
      subscriptionManager ! AddStageEventSubscriber(jobId.toLong,
                                                    runId.toLong,

                                                    appAttemptId,
                                                    sparkJobId,
                                                    subId)

    case SparkStageEvent(_, stages) => out ! stages

  }

}

object SparkStageEventSubscriber {
  def props(subscriptionManager: ActorRef)( out: ActorRef): Props = Props(new SparkStageEventSubscriber(subscriptionManager, out))
}

class SparkExecutorMetricSubscriber(subscriptionManager: ActorRef, out: ActorRef) extends Actor with ActorLogging {
  val subId = s"stage-${new Date().getTime}"
  override def receive: Receive = {
    case AppStateWSReq(jobId, runId,  appAttemptId, _) =>

      subscriptionManager ! AddExecutorMetricSubscriber(jobId.toLong,
        runId.toLong,

        appAttemptId,
        subId)

    case x: ExecutorMetricResponse => {
      out ! x
    }

  }
}

object SparkExecutorMetricSubscriber {
  def props(subscriptionManager: ActorRef)( out: ActorRef): Props = Props(new SparkExecutorMetricSubscriber(subscriptionManager, out))
}

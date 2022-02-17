package web.actors

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, PoisonPill, Props}
import web.actors.EventSubscriptionManager.{EventMessage, EventStageCompleted, EventStageStarted}
import web.actors.SparkEventSubscriber.{SparkJobEvent, SparkStageEvent}
import web.actors.SparkEventsPublisher.{
  CheckForSubscribers,
  ExecMetricSubscriberAdded,
  JobSubscriberAdded,
  StageSubscriberAdded,
  TaskSubscriberAdded,
  TerminatedExecWebSubscriber,
  TerminatedJobWebSubscriber,
  TerminatedStageWebSubscriber,
  TerminatedTaskWebSubscriber,
  UpdateExecutorMetricSubscribers,
  UpdateJobSubscribers,
  UpdateStageEventSubscribers,
  UpdateTaskEventSubscribers
}
import web.services.SparkEventService

import scala.util.{Failure, Success}
import concurrent.duration._

class SparkEventsPublisher(jobId: Long, jobRunId: Long,  appAttemptId: String, eventService: SparkEventService)
    extends Actor
    with ActorLogging {

  implicit val ec               = context.dispatcher
  override def receive: Receive = manageAppState(Map(), Map(), Map(), Map())

  context.system.scheduler.scheduleWithFixedDelay(2.seconds, 5.seconds, self, UpdateJobSubscribers)
  context.system.scheduler.scheduleWithFixedDelay(2.seconds, 5.seconds, self, UpdateStageEventSubscribers)
  context.system.scheduler.scheduleWithFixedDelay(2.seconds, 5.seconds, self, UpdateTaskEventSubscribers)
  context.system.scheduler.scheduleWithFixedDelay(2.seconds, 5.seconds, self, UpdateExecutorMetricSubscribers)
  context.system.scheduler.scheduleWithFixedDelay(5.seconds, 10.seconds, self, CheckForSubscribers)

  def manageAppState(taskEventSubscribers: Map[String, ActorRef],
                     jobEventSubscribers: Map[String, ActorRef],
                     stageEventSubscribers: Map[String, (Int, ActorRef)],
                     executorMetricSubscribers: Map[String, ActorRef]): Receive = {

    case CheckForSubscribers =>
      if (taskEventSubscribers.size == 0 && jobEventSubscribers.size == 0 && stageEventSubscribers.size == 0 && executorMetricSubscribers.size == 0)
        self ! PoisonPill

    case UpdateJobSubscribers =>
      if (jobEventSubscribers.nonEmpty) {

      }
    case message: EventMessage if message.isInstanceOf[EventStageCompleted] || message.isInstanceOf[EventStageStarted] =>
      self ! UpdateStageEventSubscribers

    case TerminatedJobWebSubscriber(subId) =>
      context.become(
        manageAppState(
          taskEventSubscribers,
          jobEventSubscribers.filter(p => !p._1.contentEquals(subId)),
          stageEventSubscribers,
          executorMetricSubscribers
        ))

    case TerminatedTaskWebSubscriber(subId) =>
      context.become(
        manageAppState(
          taskEventSubscribers.filter(p => !p._1.contentEquals(subId)),
          jobEventSubscribers,
          stageEventSubscribers,
          executorMetricSubscribers
        ))
    case TerminatedExecWebSubscriber(subId) =>
      context.become(
        manageAppState(
          taskEventSubscribers,
          jobEventSubscribers,
          stageEventSubscribers,
          executorMetricSubscribers.filter(p => !p._1.contentEquals(subId))
        ))

    case TerminatedStageWebSubscriber(subId) =>
      context.become(
        manageAppState(
          taskEventSubscribers,
          jobEventSubscribers,
          stageEventSubscribers.filter(p => !p._1.contentEquals(subId)),
          executorMetricSubscribers
        ))

    case message: JobSubscriberAdded =>
      jobEventSubscribers.get(message.subId) match {
        case None    => context.watchWith(message.webClient, TerminatedJobWebSubscriber(message.subId))
        case Some(_) => log.debug("Already watched this job webclient")
      }
      context.become(
        manageAppState(
          taskEventSubscribers,
          jobEventSubscribers = jobEventSubscribers ++ Map(message.subId -> message.webClient),
          stageEventSubscribers,
          executorMetricSubscribers
        ))

    case message: StageSubscriberAdded =>
      stageEventSubscribers.get(message.subId) match {
        case None    => context.watchWith(message.webClient, TerminatedStageWebSubscriber(message.subId))
        case Some(_) => log.debug("Already watched this stage webclient")
      }
      context.become(
        manageAppState(
          taskEventSubscribers,
          jobEventSubscribers,
          stageEventSubscribers = stageEventSubscribers ++ Map(message.subId -> (message.sparkJobId, message.webClient)),
          executorMetricSubscribers
        ))

    case message: TaskSubscriberAdded =>
      stageEventSubscribers.get(message.subId) match {
        case None    => context.watchWith(message.webClient, TerminatedTaskWebSubscriber(message.subId))
        case Some(_) => log.debug("Already watched this stage webclient")
      }
      context.become(
        manageAppState(
          taskEventSubscribers = taskEventSubscribers ++ Map(message.subId -> message.webClient),
          jobEventSubscribers,
          stageEventSubscribers,
          executorMetricSubscribers
        ))

    case message: ExecMetricSubscriberAdded =>
      stageEventSubscribers.get(message.subId) match {
        case None    => context.watchWith(message.webClient, TerminatedExecWebSubscriber(message.subId))
        case Some(_) => log.debug("Already watched this stage webclient")
      }
      context.become(
        manageAppState(
          taskEventSubscribers,
          jobEventSubscribers,
          stageEventSubscribers,
          executorMetricSubscribers = executorMetricSubscribers ++ Map(message.subId -> message.webClient)
        ))

    case UpdateStageEventSubscribers =>
      val subsWithJobIds = stageEventSubscribers.values.toList.groupBy(_._1).mapValues(x => x.map(_._2))

      subsWithJobIds.keySet.foreach { sparkJobId =>
        eventService
          .getSparkStageMetrics(jobId, jobRunId,  appAttemptId, sparkJobId)
          .onComplete {
            case Failure(e) => log.error(s"Failed updating the stage state : ${e.getMessage}")
            case Success(stageState) =>
              stageState.toOption match {
                case None => log.error(s"Failed updating the job state")
                case Some(stages) =>
                  subsWithJobIds.get(sparkJobId) match {
                    case None       => log.warning("No subscribers found for stage events")
                    case Some(subs) => subs.foreach(sub => sub ! SparkStageEvent(appAttemptId, stages))
                  }
              }
          }
      }

    case UpdateExecutorMetricSubscribers =>
      if (executorMetricSubscribers.nonEmpty) {
        eventService.getExecutorMetrics(jobRunId, appAttemptId).onComplete {
          case Failure(e) => log.warning(s"Failed updating the executor metrics state : ${e.getMessage}")
          case Success(execMetricsOpt) =>
            execMetricsOpt match {
              case None        => log.warning(s"Metrics not found for the given input")
              case Some(execM) => executorMetricSubscribers.values.foreach(sub => sub ! execM)
            }
        }
      }

    case UpdateTaskEventSubscribers =>
      if (taskEventSubscribers.nonEmpty) {
        eventService.getSparkAggMetrics(jobId, jobRunId,  appAttemptId).onComplete {
          case Failure(e) => log.warning(s"Failed updating the agg metrics state : ${e.getMessage}")
          case Success(aggMetricsOpt) =>
            aggMetricsOpt.toOption match {
              case None             => log.warning(s"Failed updating the metrics state")
              case Some(aggMetrics) => taskEventSubscribers.values.foreach(sub => sub ! aggMetrics)
            }
        }
      }

  }

}

object SparkEventsPublisher {

  case class JobSubscriberAdded(subId: String, webClient: ActorRef)
  case class StageSubscriberAdded(subId: String, webClient: ActorRef, sparkJobId: Int)
  case class TaskSubscriberAdded(subId: String, webClient: ActorRef)
  case class ExecMetricSubscriberAdded(subId: String, webClient: ActorRef)

  case class TerminatedJobWebSubscriber(subId: String)
  case class TerminatedTaskWebSubscriber(subId: String)
  case class TerminatedStageWebSubscriber(subId: String)
  case class TerminatedExecWebSubscriber(subId: String)
  case object UpdateStageEventSubscribers
  case object UpdateTaskEventSubscribers
  case object UpdateJobSubscribers
  case object CheckForSubscribers
  case object UpdateExecutorMetricSubscribers

  def props(jobId: Long, jobRunId: Long, appAttemptId: String, eventService: SparkEventService): Props =
    Props(new SparkEventsPublisher(jobId, jobRunId,  appAttemptId, eventService))

}

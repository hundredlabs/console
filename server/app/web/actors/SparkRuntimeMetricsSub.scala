package web.actors


import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import web.actors.SparkRuntimeMetricsSub.{CheckDetailedExecutorMetrics, CheckExecutorMetrics}
import web.models.spark.ExecutorWSReq
import web.services.SparkEventService

import concurrent.duration._
import scala.util.{Failure, Success}

class SparkRuntimeMetricsSub(sparkEventService: SparkEventService, out: ActorRef) extends Actor with ActorLogging {
  implicit val dispatcher = context.system.dispatcher



  def fetchExecutorMetrics(jobId: Long, runId: Long, attemptId: String, executorId: Option[String]): Receive = {
    case ExecutorWSReq(jobId, runId,  appAttemptId, execId) => context.become(fetchExecutorMetrics(jobId.toLong,
      runId.toLong,
       appAttemptId, execId))

    case CheckExecutorMetrics =>
      sparkEventService.listExecutorSummaries(jobId, runId).onComplete {
        case Failure(exception) => log.error("Failed listing executor metrics. " + exception.getMessage)
      case Success(value) => out ! value
      }

    case CheckDetailedExecutorMetrics =>

    if(executorId.isDefined){
      sparkEventService.getExecutorRuntimeMetrics(jobId, runId, executorId.get).onComplete {
        case Failure(e) => log.error(s"Failed fetching details of executor runtime metrics - ${e.getMessage}")
        case Success(value) => if(value.isDefined){
          out ! value.get
        }
      }
    }
  }

  def waiting(): Receive = {
    case ExecutorWSReq(jobId, runId,  appAttemptId, execId) =>
      if(execId.isDefined){
         context.system.scheduler.scheduleWithFixedDelay(3.seconds, 2.seconds, self, CheckDetailedExecutorMetrics)
      } else {
        context.system.scheduler.scheduleWithFixedDelay(3.seconds, 2.seconds, self, CheckExecutorMetrics)
      }
      context.become(fetchExecutorMetrics(jobId.toLong,
      runId.toLong,
     appAttemptId, execId))
  }

  override def receive: Receive = waiting()

}

object SparkRuntimeMetricsSub {
  case object CheckExecutorMetrics
  case object CheckDetailedExecutorMetrics
  def props(sparkEventService: SparkEventService)( out: ActorRef): Props = Props(new SparkRuntimeMetricsSub(sparkEventService, out))
}

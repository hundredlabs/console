package web.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import web.actors.LogsIndexSearcher.{CheckSearchLog, SearchLogs}
import web.models.LogSearchRequest
import web.models.spark.SparkLogWSReq
import web.services.SparkEventService

import concurrent.duration._
import scala.util.{Failure, Success}

class LogWSClient

class LogsIndexSearcher(sparkEventService: SparkEventService, webClient: ActorRef) extends Actor with ActorLogging {

  implicit val dispatcher = context.system.dispatcher
  context.system.scheduler.schedule(2.seconds, 750.millis, self, CheckSearchLog)

  def searchLogs(request: LogSearchRequest, jobId: Long, runId: Long, tz: Option[String]): Receive = {
    case SparkLogWSReq(jobId, taskId, tz, searchRequest) => context.become(searchLogs(searchRequest, jobId.toLong, taskId.toLong, Some(tz)))

    case CheckSearchLog =>
      log.info("Checking for the search log")
      sparkEventService.searchLogs(jobId, runId, tz, request).onComplete {
      case Failure(exception)                      => log.error(s"Unable to fetch the logs - ${exception.getMessage}")
      case Success(result) if result.logs.size > 0 => webClient ! result
      case Success(result)                                       => log.info(s"Result size - ${result.logs.size} for ts : ${request.lastOffset}")
    }
  }

  def waiting: Receive = {
    case SparkLogWSReq(jobId, runId, tz, searchRequest) => context.become(searchLogs(searchRequest, jobId.toLong, runId.toLong, Some(tz)))
    case SearchLogs(request, jobId, runId, tz) => context.become(searchLogs(request, jobId, runId, tz))
    case _ => log.warning("Invalid message for the LogIndexSearcher")
  }
  override def receive: Receive = waiting
}

object LogsIndexSearcher {
  case class SearchLogs(request: LogSearchRequest, jobId: Long, runId: Long, tz: Option[String])
  case object CheckSearchLog

  def props(sparkEventService: SparkEventService)(webClient: ActorRef): Props =
    Props(new LogsIndexSearcher(sparkEventService, webClient))
}

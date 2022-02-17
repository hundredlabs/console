package web.actors

import java.time.{ZoneId, ZonedDateTime}
import java.util.TimeZone

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import javax.inject.{Inject, Named}
import web.actors.clusters.ClusterStatusChecker
import web.services.{ClusterService, JobService}
import web.utils.DateUtil
import play.api.Configuration

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class JobRunMonitor @Inject()(actorSystem: ActorSystem,
                              @Named("job-run-fetcher") jobRunFetcher: ActorRef,
                              )(implicit executionContext: ExecutionContext) {


  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 3.second, interval = 10.seconds,
    receiver = jobRunFetcher, message = JobRunStatusFetcher.FetchJobRunStatus)


}

class JobRunStatusFetcher  @Inject()(jobService: JobService, config: Configuration) extends Actor with ActorLogging {
  implicit val dispatcher = context.dispatcher

  override def preStart(): Unit = {
    log.info("Started job run status checker")
  }

  override def receive: Receive = {
    case JobRunStatusFetcher.FetchJobRunStatus =>
      val now = DateUtil.now.toEpochSecond
      val maxWaitTime = config.get[Int]("cluster.idleTimeout")
      jobService.fetchTimelimitExceededJobRuns().onComplete {
        case Failure(exception) => log.error(s"Failed fetching the job runs - ${exception.getMessage}")
        case Success(runIds) => runIds.foreach{ id =>
          jobService.updateJobStatus(RunStatus.TimeLimitExceeded.toString, id, TimeZone.getTimeZone(ZoneId.of("")).getID)
        }
      }

//      clusterService.listLastPingTimestamps(ClusterStatus.RUNNING).onComplete {
//        case Failure(exception) => log.error(s"Failed checking the status of the clusters - ${exception.getMessage}")
//        case Success(clusters) =>  clusters
//          .foreach { cv =>
//
//            if(now - cv.lastPingTS.toEpochSecond > maxWaitTime){
//              log.info(s"making the cluster inactive with id - ${cv.id}")
//              clusterService.inactivateCluster(cv.id)
//            }
//
//          }
//      }
  }

}

object JobRunStatusFetcher {
  case object FetchJobRunStatus
}
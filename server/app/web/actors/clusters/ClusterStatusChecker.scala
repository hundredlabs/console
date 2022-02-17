package web.actors.clusters

import akka.actor.{Actor, ActorLogging}
import com.gigahex.commons.models.ClusterStatus
import javax.inject.Inject
import web.services.ClusterService
import web.utils.DateUtil
import play.api.Configuration

import scala.util.{Failure, Success}

class ClusterStatusChecker  @Inject()(clusterService: ClusterService, config: Configuration) extends Actor with ActorLogging {
  implicit val dispatcher = context.dispatcher

  override def preStart(): Unit = {
    log.info("Started cluster status checker")
  }

  override def receive: Receive = {
    case ClusterStatusChecker.FetchClusterStatus =>
      val now = DateUtil.now.toEpochSecond
      val maxWaitTime = config.get[Int]("cluster.idleTimeout")

     clusterService.listLastPingTimestamps(ClusterStatus.RUNNING).onComplete {
       case Failure(exception) => log.error(s"Failed checking the status of the clusters - ${exception.getMessage}")
       case Success(clusters) =>  clusters
         .foreach { cv =>

          if(now - cv.lastPingTS.toEpochSecond > maxWaitTime){
            log.info(s"making the cluster inactive with id - ${cv.id}")
            clusterService.inactivateCluster(cv.id)
          }

       }
     }
  }

}

object ClusterStatusChecker {
  case object FetchClusterStatus
}

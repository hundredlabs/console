package web.actors.clusters

import akka.actor.{Actor, ActorLogging}
import javax.inject.Inject
import web.actors.clusters.ClusterManager.{DeleteLocalCluster, InitClusterManagers, StartLocalCluster, StopLocalCluster}
import web.models.cluster.{ClusterPackage, HadoopPackage, KafkaClusterInfo, KafkaPackage, LocalSparkConfig, SparkPackage}
import web.services.ClusterService
import play.api.libs.ws.WSClient
import akka.pattern._
import akka.util.Timeout
import com.gigahex.commons.models.ClusterStatus
import play.api.Configuration

import concurrent.duration._
import scala.util.{Failure, Success}

class ClusterManager @Inject()(configuration: Configuration, clusterService: ClusterService, ws: WSClient)
    extends Actor
    with ActorLogging
    with ClusterActorBuilder {
  implicit val dispatcher = context.dispatcher
  implicit val timeout    = Timeout(10.seconds)

  context.system.scheduler.scheduleOnce(5.seconds, self, InitClusterManagers)

  override def receive: Receive = {

    case InitClusterManagers =>
      clusterService
        .listClusterPackages()
        .map { packages =>
          packages.map {
            case pkg: KafkaPackage => getKafkaClusterManager(context, clusterService, ws, pkg, configuration)
            case pkg: SparkPackage => getSparkClusterManager(context, clusterService, ws, pkg, configuration)
            case pkg: HadoopPackage => getHDFSClusterManager(context, clusterService, ws, pkg, configuration)
          }
        }
        .onComplete {
          case Failure(exception) => log.error(s"Failed fetching the existing cluster: ${exception.getMessage}")
          case Success(actors)    => actors.foreach(actor => actor ! ServiceMessages.InitClusterState)
        }

    case StartLocalCluster(ckp) =>
      log.info(s"CM: starting ${ckp.clusterId}")
      val cm = ckp match {
        case pkg: KafkaPackage => getKafkaClusterManager(context, clusterService, ws, pkg, configuration)
        case pkg: SparkPackage => getSparkClusterManager(context, clusterService, ws, pkg, configuration)
        case pkg: HadoopPackage => getHDFSClusterManager(context, clusterService, ws, pkg, configuration)
      }
      cm.ask(ServiceMessages.StartCluster).pipeTo(sender())

    case StopLocalCluster(cId) =>
      context.child(s"scm-${cId}") match {
        case None      => sender() ! ClusterStatus.INACTIVE
        case Some(scm) =>
          log.info(s"Stopping the cluster actor: ${cId}")
          scm ? ServiceMessages.StopCluster pipeTo sender()
      }

    case DeleteLocalCluster(cId) =>
      context.child(s"scm-${cId}") match {
        case None      => sender() ! Right(true)
        case Some(scm) => scm ? ServiceMessages.DeleteCluster pipeTo sender()
      }

  }

}

object ClusterManager {
  case object InitSparkClusters
  case object InitClusterManagers
  case class StartLocalCluster(ckp: ClusterPackage)
  case class StartLocalSpark(config: LocalSparkConfig)
  case class StartLocalKafka(workspaceId: Long, config: KafkaClusterInfo)
  case class StopLocalCluster(clusterId: Long)
  case class DeleteLocalCluster(clusterId: Long)

}

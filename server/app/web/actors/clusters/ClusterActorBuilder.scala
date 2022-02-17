package web.actors.clusters

import akka.actor.{ActorContext, ActorRef}
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT.HANDLE
import web.actors.clusters.hadoop.LocalHDFSManager
import web.actors.clusters.kafka.LocalKafkaManager
import web.actors.clusters.spark.LocalSparkManager
import web.models.cluster.{HadoopPackage, KafkaPackage, SparkPackage}
import web.services.ClusterService
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.sys.process.Process

trait ClusterActorBuilder {

  def getKafkaClusterManager(context: ActorContext,
                             clusterService: ClusterService,
                             ws: WSClient,
                             pkg: KafkaPackage,
                             configuration: Configuration): ActorRef = {
    context.child(s"scm-${pkg.clusterId}") match {
      case None =>
        context.actorOf(LocalKafkaManager.props(configuration, pkg: KafkaPackage, clusterService, ws), s"scm-${pkg.clusterId}")
      case Some(actor) => actor
    }

  }

  def getSparkClusterManager(context: ActorContext,
                             clusterService: ClusterService,
                             ws: WSClient,
                             pkg: SparkPackage,
                             configuration: Configuration): ActorRef = {
    context.child(s"scm-${pkg.clusterId}") match {
      case None =>
        context.actorOf(LocalSparkManager.props(configuration, pkg, clusterService, ws), s"scm-${pkg.clusterId}")
      case Some(actor) => actor
    }

  }

  def getHDFSClusterManager(context: ActorContext,
                            clusterService: ClusterService,
                            ws: WSClient,
                            pkg: HadoopPackage,
                            configuration: Configuration): ActorRef = {
    context.child(s"scm-${pkg.clusterId}") match {
      case None =>
        context.actorOf(LocalHDFSManager.props(configuration, pkg, clusterService, ws), s"scm-${pkg.clusterId}")
      case Some(actor) => actor
    }

  }


}

package web.actors.clusters.kafka

import java.net.UnknownHostException

import akka.actor.{Actor, ActorLogging, Cancellable, PoisonPill, Props}
import akka.pattern._
import com.gigahex.commons.constants.AppSettings
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import web.actors.clusters.kafka.LocalKafkaManager.{DownloadKafka, StartKafkaProcesses}
import web.actors.clusters.spark.LocalSparkManager.UpdateDownloadProgress
import web.actors.clusters.{ClusterActorBuilder, InstallationManager, PIDMonitor, ServiceMessages}
import web.models.cloud.ClusterProcess
import web.models.cluster.{KafkaPackage, KafkaProcesses}
import web.services.ClusterService
import web.utils.MetricConverter
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class LocalKafkaManager(appConfig: Configuration, pkg: KafkaPackage, clusterService: ClusterService, ws: WSClient)
    extends Actor
    with ActorLogging
    with ClusterActorBuilder {

  private implicit val dispatcher: ExecutionContextExecutor = context.dispatcher
  private val rootDir     = s"${appConfig.get[String]("gigahex.packages")}/packages"

  private val kpm         = KafkaProcessManager(s"$rootDir/kafka_${pkg.scalaVersion}-${pkg.version}")

  private def getDownloadUrl(version: String, scalaVersion: String) = s"${AppSettings.kakfaCDN}/$version/kafka_$scalaVersion-$version.tgz"

  private def handleClusterCommands(status: ClusterStatus,
                                    progress: String,
                                    downloadChecker: Cancellable,
                                    processes: Seq[ClusterProcess]): Receive = {

    case ServiceMessages.StartCluster =>
      val result = status match {
        case com.gigahex.commons.models.ClusterStatus.NEW =>
          self ! DownloadKafka
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING)
            .map(_ => ClusterStatus.DOWNLOADING)

         case ClusterStatus.TERMINATED_WITH_ERRORS =>
           self ! DownloadKafka
           clusterService
             .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING)
             .map(_ => ClusterStatus.DOWNLOADING)


        case ClusterStatus.TERMINATED =>
          log.info("Starting the cluster after it was terminated")
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING)
            .map(_ => self ! StartKafkaProcesses)
            .map(_ => ClusterStatus.STARTING)

        case ClusterStatus.INACTIVE =>
          self ! StartKafkaProcesses
          Future.successful(ClusterStatus.STARTING)
        case x => Future.successful(x)
      }
      result.pipeTo(sender())

    case ServiceMessages.StopCluster =>
      context.children.foreach(a => a ! PoisonPill)
      clusterService
        .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATING)
        .map(_ => ClusterStatus.TERMINATING)
        .pipeTo(sender())
      kpm.stopKafkaProcesses(pkg.clusterId, clusterService) onComplete {
        case Success(_) =>
          context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes))
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED)
        case Failure(e) =>
          context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes))
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED_WITH_ERRORS, Some(e.getMessage))
      }

    case StartKafkaProcesses =>
      kpm
        .onZkStart(pid => {
          context.actorOf(
            PIDMonitor.props(pkg.clusterId, pid, KafkaProcesses.ZOOKEEPER, kpm.getCmd(KafkaProcesses.ZOOKEEPER), 1, 10, clusterService))
          clusterService.updateClusterProcess(pkg.clusterId, KafkaProcesses.ZOOKEEPER, RunStatus.Running, "", Some(pid))
        })
        .withKafkaPID(pid => {
          context.actorOf(
            PIDMonitor.props(pkg.clusterId, pid, KafkaProcesses.KAFKA_SERVER, kpm.getCmd(KafkaProcesses.KAFKA_SERVER), 1, 10, clusterService))
          clusterService.updateClusterProcess(pkg.clusterId, KafkaProcesses.KAFKA_SERVER, RunStatus.Running, "", Some(pid))
        })
        .onKafkaServerStart(() => {
          clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.RUNNING)
          context.become(handleClusterCommands(ClusterStatus.STARTING, progress, downloadChecker, processes = processes))
        })
        .startKafkaProcesses

    case ServiceMessages.DeleteCluster =>
      kpm.deleteKafkaPackages(s"$rootDir/kafka_${pkg.scalaVersion}-${pkg.version}") match {
        case Failure(exception) => sender() ! Left(exception)
        case Success(_)         => sender() ! Right(true)
      }

    case UpdateDownloadProgress =>
      if (progress.length != 0 && status == ClusterStatus.DOWNLOADING) {
        val splits          = progress.split("/")
        val bytesDownloaded = splits(0).toLong
        val totalBytes      = splits(1).toLong
        clusterService.updateDownloadProgress(pkg.clusterId, pkg.workspaceId, s"$bytesDownloaded/$totalBytes")
        log.info(
          s"Kafka download progress: ${MetricConverter.toReadableSize(bytesDownloaded)}/${MetricConverter.toReadableSize(totalBytes)}")
      }

    case ServiceMessages.UpdateFromProcess(name, processStatus) =>
      val updatedProcesses = processes.map { p =>
        if (p.name.equals(name)) {
          p.copy(status = processStatus)
        } else p
      }
      if (processStatus == RunStatus.Stopped) {
        sender() ! PoisonPill
      }
      if (processes.size == updatedProcesses.count(_.status == RunStatus.Running)) {
        clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.RUNNING)
        context.become(handleClusterCommands(ClusterStatus.RUNNING, progress, downloadChecker, processes = updatedProcesses))
      } else if (processes.size == updatedProcesses.count(_.status == RunStatus.Stopped)) {
        clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED)
        context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes = updatedProcesses))
      }



    case ServiceMessages.InitClusterState =>
      clusterService.getKafkaCluster(pkg.clusterId, pkg.workspaceId).onComplete {
        case Failure(exception) => log.error(exception.getMessage)
        case Success(info) =>
          info match {
            case None => log.warning(s"Cluster: ${pkg.clusterId} not found")
            case Some(v) =>
              if (v.status == ClusterStatus.RUNNING && context.children.toList.isEmpty) {
                v.processes.foreach { p =>
                  context.actorOf(PIDMonitor.props(pkg.clusterId, p.pid.getOrElse(-1L), p.name, kpm.getCmd(p.name), 0, 10, clusterService),
                                  p.name)
                }
              }
          }
      }

    case DownloadKafka =>
      val im = new InstallationManager(
        downloadUrl = getDownloadUrl(pkg.version, pkg.scalaVersion),
        downloadDir = rootDir,
        installPath = rootDir,
        ws
      )
      val updater = context.system.scheduler.scheduleWithFixedDelay(2.seconds, 2.seconds, self, UpdateDownloadProgress)
      clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING).onComplete {
        case Failure(exception) => log.error(exception.getMessage)
        case Success(_) =>
          im.download(progress => {
              context.become(handleClusterCommands(ClusterStatus.DOWNLOADING, progress, updater, processes))
            })
            .onComplete {
              case Failure(exception) =>
                 val errorMsg = exception match {
                   case e: UnknownHostException => s"Failed connecting with the remote package repository - ${e.getMessage}"
                   case e : Exception => s"Failed downloading the package - ${e.getMessage}"
                 }
                log.error(s"Failed downloading - ${exception.getClass.getName} - $errorMsg")
                clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED_WITH_ERRORS, Some(errorMsg))
                context.become(handleClusterCommands(ClusterStatus.TERMINATED_WITH_ERRORS, progress, updater, processes))
              case Success(installDir) =>

                im.setFilesExecutable(s"$installDir/kafka_${pkg.scalaVersion}-${pkg.version}/bin", ".sh")
                im.modifyConfig(s"/kafka_${pkg.scalaVersion}-${pkg.version}/config/server.properties")(line => {
                  if(line.startsWith("log.dirs")){
                    s"log.dirs=$installDir/kafka_${pkg.scalaVersion}-${pkg.version}/kafka-logs"
                  } else line
                })
                im.modifyConfig(s"/kafka_${pkg.scalaVersion}-${pkg.version}/config/zookeeper.properties")(line => {
                  if(line.startsWith("dataDir")){
                    s"dataDir=$installDir/kafka_${pkg.scalaVersion}-${pkg.version}/zk-data"
                  } else line
                })
                clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING).onComplete {
                  case Failure(e) => log.warning(e.getMessage)
                  case Success(_) => self ! StartKafkaProcesses
                }

            }
      }

  }

  override def receive: Receive =
    handleClusterCommands(pkg.status, "", new Cancellable {
      override def cancel(): Boolean = true

      override def isCancelled: Boolean = true
    }, pkg.processes)

}

object LocalKafkaManager {

  def props(appConfig: Configuration, pkg: KafkaPackage, clusterService: ClusterService, ws: WSClient): Props =
    Props(new LocalKafkaManager(appConfig, pkg, clusterService, ws))

  case object StartKafkaProcesses
  case object DownloadKafka

}

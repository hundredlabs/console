package web.actors.clusters.hadoop

import java.net.UnknownHostException
import java.nio.file.{Files, Paths, StandardCopyOption}

import akka.actor.{Actor, ActorLogging, Cancellable, PoisonPill, Props}
import com.gigahex.commons.constants.AppSettings
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import web.actors.clusters.hadoop.LocalHDFSManager.{DownloadHadoop, StartHDFS}
import web.actors.clusters.{ClusterActorBuilder, InstallationManager, PIDMonitor, ServiceMessages}
import web.actors.clusters.spark.LocalSparkManager.UpdateDownloadProgress
import web.models.cloud.ClusterProcess
import web.models.cluster.{HDFSProcesses, HadoopPackage}
import akka.pattern._

import scala.concurrent.duration._
import web.services.ClusterService
import web.utils.MetricConverter
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.util.{Failure, Success}

class LocalHDFSManager(appConfig: Configuration, pkg: HadoopPackage, clusterService: ClusterService, ws: WSClient)
  extends Actor
    with ActorLogging
    with ClusterActorBuilder {

  implicit val dispatcher = context.dispatcher
  private val rootDir     = s"${appConfig.get[String]("gigahex.packages")}/packages"
  private val hdfsHomeDir = s"$rootDir/hadoop-${pkg.version}"
  private val hpm         = HDFSProcessManager(hdfsHomeDir, pkg.clusterId, clusterService)
  private val softLinkDir = AppSettings.getBinDir(appConfig.get[String]("gigahex.packages"))

  private def getDownloadUrl(version: String) = s"${AppSettings.hadoopCDN}/common/hadoop-$version.tar.gz"


  private def handleClusterCommands(status: ClusterStatus,
                                    progress: String,
                                    downloadChecker: Cancellable,
                                    processes: Seq[ClusterProcess]): Receive = {

    case ServiceMessages.StartCluster =>
      val result = status match {
        case com.gigahex.commons.models.ClusterStatus.NEW =>
          self ! DownloadHadoop
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING)
            .map(_ => ClusterStatus.DOWNLOADING)

        case ClusterStatus.TERMINATED_WITH_ERRORS =>
          self ! StartHDFS(false)
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING)
            .map(_ => ClusterStatus.STARTING)

        case ClusterStatus.FAILED_WHEN_DOWNLOADING =>
          self ! DownloadHadoop
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING)
            .map(_ => ClusterStatus.DOWNLOADING)


        case ClusterStatus.TERMINATED =>
          log.info("Starting the cluster after it was terminated")
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING)
            .map(_ => self ! StartHDFS(false))
            .map(_ => ClusterStatus.STARTING)

        case ClusterStatus.INACTIVE =>
          self ! StartHDFS(false)
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
      clusterService.getHDFSCluster(pkg.clusterId, pkg.workspaceId) onComplete {
        case Success(Some(hdfsClusterInfo)) =>
          hpm.stopHDFSProcesses(pkg.clusterId, hdfsClusterInfo.processes)
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED)
          context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes))

        case Failure(e) =>
          context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes))
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED_WITH_ERRORS, Some(e.getMessage))
        case _ => log.warning(s"Cluster not found - ${pkg.clusterId}")
      }


    case StartHDFS(init) =>
      hpm.onNNStart(pid => {
        context.actorOf(
          PIDMonitor.props(pkg.clusterId, pid, HDFSProcesses.NAME_NODE, hpm.getCmd(HDFSProcesses.NAME_NODE), 1, 10, clusterService))
         clusterService.updateClusterProcess(pkg.clusterId, HDFSProcesses.NAME_NODE, RunStatus.Running, "", Some(pid))
      })
        .onDNStart(pid => {
          context.actorOf(
            PIDMonitor.props(pkg.clusterId, pid, HDFSProcesses.DATA_NODE, hpm.getCmd(HDFSProcesses.DATA_NODE), 1, 10, clusterService))
            clusterService.updateClusterProcess(pkg.clusterId, HDFSProcesses.DATA_NODE, RunStatus.Running, "", Some(pid))
        })
        .onHdfsStart(() => {
          clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.RUNNING)
          context.become(handleClusterCommands(ClusterStatus.STARTING, progress, downloadChecker, processes = processes))
        })
        .startHadoop(init)


    case ServiceMessages.DeleteCluster =>
      hpm.deletePackages(System.getProperty("user.home")) match {
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
          s"Hadoop download progress: ${MetricConverter.toReadableSize(bytesDownloaded)}/${MetricConverter.toReadableSize(totalBytes)}")
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
      clusterService.getHDFSCluster(pkg.clusterId, pkg.workspaceId).onComplete {
        case Failure(exception) => log.error(exception.getMessage)
        case Success(info) =>
          info match {
            case None => log.warning(s"Cluster: ${pkg.clusterId} not found")
            case Some(v) =>
              if (v.status == ClusterStatus.RUNNING && context.children.toList.isEmpty) {
                v.processes.foreach { p =>
                  context.actorOf(PIDMonitor.props(pkg.clusterId, p.pid.getOrElse(-1L), p.name, hpm.getCmd(p.name), 0, 10, clusterService),
                    p.name)
                }
              }
          }
      }

    case DownloadHadoop =>
      val im = new InstallationManager(
        downloadUrl = getDownloadUrl(pkg.version),
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
          }, false)
            .onComplete {
              case Failure(exception) =>
                val errorMsg = exception match {
                  case e: UnknownHostException => s"Failed connecting with the remote package repository - ${e.getMessage}"
                  case e : Exception => s"Failed downloading the package - ${e.getMessage}"
                }
                log.error(s"Failed downloading - ${exception.getClass.getName} - $errorMsg")
                clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.FAILED_WHEN_DOWNLOADING, Some(errorMsg))
                context.become(handleClusterCommands(ClusterStatus.FAILED_WHEN_DOWNLOADING, progress, updater, processes))
              case Success(tarFilePath) =>
                clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING)
                context.become(handleClusterCommands(ClusterStatus.STARTING, progress, updater, processes))

                im.extractTar(tarFilePath,rootDir)
                im.setFilesExecutable(s"${hdfsHomeDir}/bin", "dfs")
                im.setFilesExecutable(s"${hdfsHomeDir}/bin", "hadoop")
                val srcHDFSSiteXML = getClass.getResource("/hdfs-configs/hdfs-site.xml").getPath
                val srcCoreSiteXML = getClass.getResource("/hdfs-configs/core-site.xml").getPath
                Files.copy(Paths.get(srcHDFSSiteXML), Paths.get(s"${hdfsHomeDir}/etc/hadoop/hdfs-site.xml"), StandardCopyOption.REPLACE_EXISTING)
                Files.copy(Paths.get(srcCoreSiteXML), Paths.get(s"${hdfsHomeDir}/etc/hadoop/core-site.xml"), StandardCopyOption.REPLACE_EXISTING)
                im.modifyConfig(s"/hadoop-${pkg.version}/etc/hadoop/hdfs-site.xml")(line => {
                  if(line.contains("dfs.data.dir")) {
                    s"""<value>${hdfsHomeDir}/dfs/data</value>"""
                  } else if(line.contains("dfs.nn.dir")){
                    s"""<value>${hdfsHomeDir}/dfs/name</value>"""
                  } else line
                })
                hpm.addSoftLinks(s"${hdfsHomeDir}/bin", softLinkDir)
                self ! StartHDFS(true)



            }
      }

  }

  override def receive: Receive =
    handleClusterCommands(pkg.status, "", new Cancellable {
      override def cancel(): Boolean = true

      override def isCancelled: Boolean = true
    }, pkg.processes)

}


object LocalHDFSManager {

  def props(appConfig: Configuration, pkg: HadoopPackage, clusterService: ClusterService, ws: WSClient): Props =
    Props(new LocalHDFSManager(appConfig, pkg, clusterService, ws))

  case class StartHDFS(init: Boolean)
  case object DownloadHadoop
}

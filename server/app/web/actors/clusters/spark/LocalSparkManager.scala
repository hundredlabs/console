package web.actors.clusters.spark

import java.io.{File, FileInputStream, FileOutputStream, FileWriter, IOException}
import java.net.{URL, UnknownHostException}
import java.nio.channels.Channels
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Cancellable, PoisonPill, Props}
import akka.pattern._
import com.gigahex.commons.constants.AppSettings
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import web.actors.clusters.{PIDMonitor, ReadableConsumerByteChannel, ServiceMessages}
import web.actors.clusters.spark.LocalSparkManager.{HandleSparkDownloadRequest, StartClusterProcesses, UpdateDownloadProgress}
import web.models.cloud.ClusterProcess
import web.models.cluster.SparkPackage
import web.services.ClusterService
import web.utils.MetricConverter
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Success}

class LocalSparkManager(appConfig: Configuration, pkg: SparkPackage, clusterService: ClusterService, ws: WSClient)
    extends Actor
    with ActorLogging {

  private implicit val dispatcher = context.dispatcher
  private val rootDir             = s"${appConfig.get[String]("gigahex.packages")}/gigahex/packages"
  private val binPath             = s"${rootDir}/spark-${pkg.version}-bin-hadoop${pkg.hadoopBinaryVersion}/bin"
  private val confDir             = s"${rootDir}/spark-${pkg.version}-bin-hadoop${pkg.hadoopBinaryVersion}/conf"
  private val eventDir            = s"${rootDir}/spark-${pkg.version}-bin-hadoop${pkg.hadoopBinaryVersion}/events"
  private val spm                 = SparkProcessManager(s"${rootDir}/spark-${pkg.version}-bin-hadoop${pkg.hadoopBinaryVersion}", clusterService)
  private val softLinkDir = AppSettings.getBinDir(appConfig.get[String]("gigahex.packages"))

  private val defaultSparkConf = Map(
    "spark.eventLog.dir"            -> s"file://$eventDir",
    "spark.history.fs.logDirectory" -> s"file://$eventDir",
    "spark.executor.memory"         -> "1g",
    "spark.eventLog.enabled"        -> "true",
    "spark.master"                  -> "spark://0.0.0.0:7077"
  )

  private def handleClusterCommands(status: ClusterStatus,
                                    progress: String,
                                    downloadChecker: Cancellable,
                                    processes: Seq[ClusterProcess] = Seq()): Receive = {

    case ServiceMessages.StartCluster =>
      clusterService.getSparkCluster(pkg.clusterId, pkg.workspaceId).flatMap {
        case None => Future.successful(ClusterStatus.DELETED)
        case Some(v) =>
          v.status match {
            case com.gigahex.commons.models.ClusterStatus.NEW =>
              self ! HandleSparkDownloadRequest
              clusterService
                .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING)
                .map(_ => ClusterStatus.DOWNLOADING)

            case com.gigahex.commons.models.ClusterStatus.TERMINATED_WITH_ERRORS =>
              self ! HandleSparkDownloadRequest
              clusterService
                .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING)
                .map(_ => ClusterStatus.DOWNLOADING)

            case ClusterStatus.TERMINATED =>
              self ! StartClusterProcesses
              clusterService
                .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING)
                .map(_ => ClusterStatus.STARTING)

            case ClusterStatus.INACTIVE =>
              self ! StartClusterProcesses
              Future.successful(ClusterStatus.STARTING)
            case x => Future.successful(x)
          }
      } pipeTo sender()

    case ServiceMessages.StopCluster =>
      context.children.foreach(monitor => monitor ! PoisonPill)
      clusterService
        .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATING)
        .map(_ => ClusterStatus.TERMINATING)
        .pipeTo(sender())
      clusterService.getSparkCluster(pkg.clusterId, pkg.workspaceId).onComplete {
        case Success(Some(sparkClusterInfo)) =>
          spm.stopSparkProcesses(pkg.clusterId, sparkClusterInfo.processes)
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED)
          context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes))

        case Failure(e) =>
          context.become(handleClusterCommands(ClusterStatus.TERMINATED, progress, downloadChecker, processes))
          clusterService
            .updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED_WITH_ERRORS, Some(e.getMessage))
        case _ => log.warning(s"Cluster not found - ${pkg.clusterId}")
      }

    case StartClusterProcesses =>
      spm
        .onHistoryProcessStart(pid => {
          context.actorOf(
            PIDMonitor.props(pkg.clusterId,
                             pid,
                             SparkProcesses.SHS,
                             spm.getHistoryProcess(binPath, s"$confDir/spark-defaults.conf"),
                             0,
                             10,
                             clusterService))
          clusterService.updateClusterProcess(pkg.clusterId, SparkProcesses.SHS, RunStatus.Running, "", Some(pid))
        })
        .onMasterProcessStart(pid => {
          PIDMonitor.props(pkg.clusterId, pid, SparkProcesses.MASTER, spm.getMasterProcess(binPath, 8080), 0, 10, clusterService)
          clusterService.updateClusterProcess(pkg.clusterId, SparkProcesses.MASTER, RunStatus.Running, "", Some(pid))
        })
        .onWorkerProcessStart(pid => {
          PIDMonitor.props(pkg.clusterId, pid, SparkProcesses.WORKER, spm.getWorkerProcess(binPath, 8081), 0, 10, clusterService)
          clusterService.updateClusterProcess(pkg.clusterId, SparkProcesses.WORKER, RunStatus.Running, "", Some(pid))
        })
        .onStandaloneClusterStart(() => {
          clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.RUNNING)
          context.become(handleClusterCommands(ClusterStatus.STARTING, progress, downloadChecker, processes = processes))
        })
        .startSparkProcesses

    case HandleSparkDownloadRequest =>
      val updater = context.system.scheduler.scheduleWithFixedDelay(2.seconds, 2.seconds, self, UpdateDownloadProgress)
      clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.DOWNLOADING).onComplete {
        case Failure(exception) => log.error(exception.getMessage)
        case Success(_) =>
          install(progress => {
            context.become(handleClusterCommands(ClusterStatus.DOWNLOADING, progress, updater))
          }).onComplete {
            case Failure(exception) =>
              val errorMsg = exception match {
                case e: UnknownHostException => s"Failed connecting with the remote package repository - ${e.getMessage}"
                case e : Exception => s"Failed downloading the package - ${e.getMessage}"
              }
              log.error(s"Failed downloading - ${exception.getClass.getName} - $errorMsg")
              clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED_WITH_ERRORS, Some(errorMsg))
              context.become(handleClusterCommands(ClusterStatus.TERMINATED_WITH_ERRORS, progress, updater))
            case Success(_) =>

              clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.STARTING).onComplete {
                case Failure(e) => log.warning(e.getMessage)
                case Success(_) => self ! StartClusterProcesses
              }
              context.become(handleClusterCommands(ClusterStatus.STARTING, progress, updater))

          }
      }

    case ServiceMessages.UpdateFromProcess(name, processStatus) =>
      val updatedProcesses = processes.map { p =>
        if (p.name.equals(name)) {
          p.copy(status = processStatus)
        } else p
      }
      log.info(s"Process - ${name} has $processStatus . Processes: ${processes.size} Stopped processes : ${updatedProcesses.count(
        _.status == RunStatus.Stopped)}")
      if (processStatus == RunStatus.Stopped) {
        sender() ! PoisonPill
      }
      if (processes.size == updatedProcesses.count(_.status == RunStatus.Running)) {
        clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.RUNNING)
      } else if (processes.size == updatedProcesses.count(_.status == RunStatus.Stopped)) {
        clusterService.updateCluster(pkg.workspaceId, pkg.clusterId, ClusterStatus.TERMINATED)
        self ! PoisonPill
      }

      context.become(handleClusterCommands(status, progress, downloadChecker, processes = updatedProcesses))

    case ServiceMessages.DeleteCluster =>
      spm.deletePackages() match {
        case Failure(exception) => sender() ! Left(exception)
        case Success(_)         => sender() ! Right(true)
      }

    case ServiceMessages.InitClusterState =>
      clusterService.getSparkCluster(pkg.clusterId, pkg.workspaceId).onComplete {
        case Failure(exception) => log.error(exception.getMessage)
        case Success(info) =>
          info match {
            case None => log.warning(s"Cluster: ${pkg.clusterId} not found")
            case Some(v) =>
              if (v.status == ClusterStatus.RUNNING && context.children.toList.isEmpty) {
                log.info("creating the process monitors")
                v.processes.foreach { p =>
                  context.actorOf(PIDMonitor.props(pkg.clusterId, p.pid.getOrElse(-1L), p.name, spm.getCmd(p.name), 0, 10, clusterService),
                                  p.name)
                }
              }
          }
      }

    case UpdateDownloadProgress =>
      if (progress.length != 0 && status == ClusterStatus.DOWNLOADING) {
        val splits          = progress.split("/")
        val bytesDownloaded = splits(0).toLong
        val totalBytes      = splits(1).toLong
        clusterService.updateDownloadProgress(pkg.clusterId, pkg.workspaceId, s"${bytesDownloaded}/${totalBytes}")
        log.info(
          s"Spark download progress: ${MetricConverter.toReadableSize(bytesDownloaded)}/${MetricConverter.toReadableSize(totalBytes)}")
      }

  }

  override def receive: Receive =
    handleClusterCommands(ClusterStatus.NEW, "", new Cancellable {
      override def cancel(): Boolean = true

      override def isCancelled: Boolean = true
    }, pkg.processes)

  /**
    * Download and setup the spark binaries in the local
    * @param onProgress handler for progress tracking
    * @param ec execution context
    * @return
    */
  def install(onProgress: String => Unit)(implicit ec: ExecutionContext): Future[String] = Future {
    blocking {

      val downloadUrl =
        new URL(s"${AppSettings.sparkCDN}/spark-${pkg.version}/spark-${pkg.version}-bin-hadoop${pkg.hadoopBinaryVersion}.tgz")
      val conn                = downloadUrl.openConnection()
      val inputStream         = conn.getInputStream
      val readableByteChannel = Channels.newChannel(inputStream)
      try {
        val rootDirectory = new File(rootDir)
        if (!rootDirectory.exists()) {
          Files.createDirectories(Paths.get(rootDir))
        }
        val maxLength = conn.getContentLengthLong
        val filePath  = s"${rootDir}/${System.currentTimeMillis()}.tar.gz"

        val rcbc = ReadableConsumerByteChannel(readableByteChannel, downloadedSize => {
          onProgress(s"$downloadedSize/$maxLength")
        })

        val fileOutputStream = new FileOutputStream(filePath)
        val fileChannel      = fileOutputStream.getChannel
        fileChannel.transferFrom(rcbc, 0, Long.MaxValue)
        extractTar(filePath, rootDir)
        new File(filePath).delete()
        writeDefaultConf(s"$confDir/spark-defaults.conf", eventDir, defaultSparkConf, pkg.userConfig)
        addSoftLinks(s"${rootDir}/spark-${pkg.version}-bin-hadoop${pkg.hadoopBinaryVersion}/bin")

      } catch {
        case e: Exception => {
          throw e
        }
      } finally {
        readableByteChannel.close()
        inputStream.close()
      }
    }
  }

  private def writeDefaultConf(path: String, eventDir: String, properties: Map[String, String], userProvidedConfig: Seq[String]): Unit = {
    val fw = new FileWriter(path)
    try {
      val events = new File(eventDir)
      if (!events.isDirectory) {
        events.mkdir()
      }
      //merge the configs
      val filteredProps = userProvidedConfig.filter(!_.isEmpty)
      val finalConfig   = properties ++ filteredProps.map(v => v.split(" ")(0) -> v.split(" ")(1))
      finalConfig.foreach {
        case (k, v) =>
          fw.write(s"$k $v")
          fw.append('\n')
      }

    } catch {
      case e: Exception => throw e
    } finally {
      fw.close()
    }
  }

  private def addSoftLinks(sparkInstallDir: String): String = {
    val dir               = new File(sparkInstallDir)
    val executableDirPath = s"${dir.getParentFile.getAbsolutePath}/exec"
    val executableDir     = new File(executableDirPath)
    if (!executableDir.exists()) {
      executableDir.mkdir()
    }
    if (dir.isFile) {
      throw new IOException("Expected a directory, found a file")
    }
    dir.listFiles().toSeq.filter(!_.getName.endsWith(".cmd")).foreach { file =>
      file.setExecutable(true)

      val executableFilePath = s"$executableDirPath/${file.getName}"
      val executableFile     = new File(executableFilePath)

      val findSparkContent = s"""#!/bin/bash
                                |exec "${file.getAbsolutePath}" "$$@" """.stripMargin
      val fw               = new FileWriter(executableFilePath)
      fw.write(findSparkContent)
      fw.flush()
      fw.close()
      executableFile.setExecutable(true)

      val symbolicLinkPath = Paths.get(s"$softLinkDir/${file.getName}")
      if (Files.isSymbolicLink(symbolicLinkPath)) {
        Files.delete(symbolicLinkPath)
      }
      Files.createSymbolicLink(symbolicLinkPath, Paths.get(executableFilePath))
    }
    sparkInstallDir
  }

  private def extractTar(source: String, targetDir: String): Unit = {
    val gzipIn = new GzipCompressorInputStream(new FileInputStream(source))
    val tarIn  = new TarArchiveInputStream(gzipIn)
    try {

      var entry = tarIn.getNextEntry
      while (entry != null) {
        val name = s"${targetDir}/${entry.getName}"
        val f    = new File(name)

        if (entry.isDirectory) {
          if (!f.isDirectory && !f.mkdirs) throw new IOException("failed to create directory " + f)
        } else {
          val parent = f.getParentFile;
          if (!parent.isDirectory && !parent.mkdirs()) {
            throw new IOException("failed to create directory " + parent);
          }
          val o = Files.newOutputStream(f.toPath)
          IOUtils.copy(tarIn, o)
          o.flush()
          o.close()
        }
        entry = tarIn.getNextEntry
      }
    } finally {
      gzipIn.close()
      tarIn.close()
    }
  }

}

object LocalSparkManager {
  def props(appConfig: Configuration, pkg: SparkPackage, clusterService: ClusterService, ws: WSClient) =
    Props(new LocalSparkManager(appConfig, pkg, clusterService, ws))
  case object HandleSparkDownloadRequest
  case object StartClusterProcesses
  case object UpdateDownloadProgress

}

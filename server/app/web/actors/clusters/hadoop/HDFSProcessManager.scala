package web.actors.clusters.hadoop

import java.io.{File, FileWriter, IOException}
import java.nio.file.{Files, Paths}

import com.gigahex.commons.models.RunStatus
import web.actors.clusters.ExternalProcessRunner
import web.models.cloud.ClusterProcess
import web.models.cluster.HDFSProcesses
import web.services.ClusterService

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try

case class HDFSProcessManager(installDir: String,
                              clusterId: Long,
                              clusterService: ClusterService,
                              private val onNameNodeStart: Long => Unit,
                              private val onDataNodeStart: Long => Unit,
                              private val onHDFSStart: () => Unit) {

  def onNNStart(handler: Long => Unit): HDFSProcessManager = this.copy(onNameNodeStart = handler)
  def onDNStart(handler: Long => Unit): HDFSProcessManager = this.copy(onDataNodeStart = handler)
  def onHdfsStart(handler: () => Unit): HDFSProcessManager = this.copy(onHDFSStart = handler)


  val formatHDFSCmd = Seq(s"${installDir}/bin/hdfs", "--config", s"${installDir}/etc/hadoop", "namenode", "-format", s"cid-${clusterId}")

  def getCmd(processName: String): Seq[String] = processName match {
    case HDFSProcesses.DATA_NODE =>
      Seq(s"${installDir}/bin/hdfs", "--config", s"${installDir}/etc/hadoop", "datanode")
    case HDFSProcesses.NAME_NODE =>
      Seq(s"${installDir}/bin/hdfs", "--config", s"${installDir}/etc/hadoop", "namenode")
  }

  private def deleteRecursively(f: File): Boolean = {
    if (f.isDirectory) f.listFiles match {
      case files: Array[File] => files.foreach(deleteRecursively)
      case null               =>
    }
    f.delete()
  }

  def deletePackages(homeDir: String): Try[Unit] = {
    Try {
      //unlink the hdfs
      val link = Paths.get(s"${homeDir}/bin/hdfs")
      if (Files.isSymbolicLink(link)) {
        Files.delete(link)
      }

      //delete the packages
      val dir = new File(installDir)
      if (dir.isDirectory) {
        dir.listFiles.foreach(deleteRecursively)
      }
      if (dir.exists && !dir.delete) {
        throw new Exception(s"Unable to delete ${dir.getAbsolutePath}")
      }
    }
  }

  def addSoftLinks(hdfsBinDir: String, softLinkDir: String): Unit = {
    val dir               = new File(hdfsBinDir)
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
  }

  def startHadoop(init: Boolean)(implicit ec: ExecutionContext): Future[Unit] = Future {
    blocking {
      if (init) {
        new ExternalProcessRunner(formatHDFSCmd, true).run(_ => {}, _ => {})
      }
      val namenodeProcess =
        new ExternalProcessRunner(getCmd(HDFSProcesses.NAME_NODE), true)
      namenodeProcess.run(
        line => {
          if (line.contains("initialization completed")) {
            new ExternalProcessRunner(getCmd(HDFSProcesses.DATA_NODE), true)
              .run(
                dnLog => {
                  if (dnLog.contains("Successfully sent block report")) {
                    onHDFSStart()
                  }

                },
                dnPID => onDataNodeStart(dnPID)
              )
          }
        },
        pid => onNameNodeStart(pid)
      )

    }
  }

  def stopHDFSProcesses(clusterId: Long, processes: Seq[ClusterProcess]): Unit = {
    processes.foreach { p =>
      if (p.pid.isDefined) {
        ProcessHandle.of(p.pid.get).ifPresent(p => p.destroy())
        clusterService.updateClusterProcess(clusterId, p.name, RunStatus.Stopped, "")
      }
    }
  }

}

object HDFSProcessManager {
  def apply(dir: String, clusterId: Long, clusterService: ClusterService): HDFSProcessManager =
    new HDFSProcessManager(dir, clusterId, clusterService, pid => {}, pid => {}, () => {})
}

package web.actors.clusters.spark

import java.io.File

import com.gigahex.commons.models.RunStatus
import web.actors.clusters.ExternalProcessRunner
import web.actors.clusters.kafka.KafkaProcessManager
import web.models.cloud.ClusterProcess
import web.services.ClusterService

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try

case class SparkProcessManager(installDir: String,
                               clusterService: ClusterService,
                               private val onMasterStart: Long => Unit,
                               private val onWorkerStart: Long => Unit,
                               private val onHistoryServerStart: Long => Unit,
                               private val onClusterStart: () => Unit) {

  def onMasterProcessStart(handler: Long => Unit): SparkProcessManager   = this.copy(onMasterStart = handler)
  def onWorkerProcessStart(handler: Long => Unit): SparkProcessManager   = this.copy(onWorkerStart = handler)
  def onHistoryProcessStart(handler: Long => Unit): SparkProcessManager  = this.copy(onHistoryServerStart = handler)
  def onStandaloneClusterStart(handler: () => Unit): SparkProcessManager = this.copy(onClusterStart = handler)

  private def deleteRecursively(f: File): Boolean = {
    if (f.isDirectory) f.listFiles match {
      case files: Array[File] => files.foreach(deleteRecursively)
      case null =>
    }
    f.delete()
  }

  def getCmd(process: String): Seq[String] = process match {
    case SparkProcesses.MASTER =>
      Seq(s"${installDir}/bin/spark-class",
          "org.apache.spark.deploy.master.Master",
          "--host",
          "0.0.0.0",
          "--port",
          "7077",
          "--webui-port",
          "8080")
    case SparkProcesses.WORKER =>
      Seq(s"${installDir}/bin/spark-class", "org.apache.spark.deploy.worker.Worker", "--webui-port", "8081", "spark://0.0.0.0:7077")
    case SparkProcesses.SHS =>
      Seq(s"${installDir}/bin/spark-class",
          "org.apache.spark.deploy.history.HistoryServer",
          "--properties-file",
          s"${installDir}/conf/spark-defaults.conf")
  }

  def getMasterProcess(binPath: String, uiPort: Int): Seq[String] = {
    Seq(s"$binPath/spark-class",
        "org.apache.spark.deploy.master.Master",
        "--host",
        "0.0.0.0",
        "--port",
        "7077",
        "--webui-port",
        uiPort.toString)
  }

  def getWorkerProcess(binPath: String, uiPort: Int): Seq[String] = {
    Seq(s"$binPath/spark-class", "org.apache.spark.deploy.worker.Worker", "--webui-port", uiPort.toString, "spark://0.0.0.0:7077")
  }

  def getHistoryProcess(binPath: String, configPath: String): Seq[String] = {
    Seq(s"$binPath/spark-class", "org.apache.spark.deploy.history.HistoryServer", "--properties-file", configPath)
  }

  def deletePackages(): Try[Unit] = {
    Try {
      val dir = new File(installDir)
      if (dir.isDirectory) {
        dir.listFiles.foreach(deleteRecursively)
      }
      if (dir.exists && !dir.delete) {
        throw new Exception(s"Unable to delete ${dir.getAbsolutePath}")
      }
    }
  }

  def startSparkProcesses(implicit ec: ExecutionContext): Future[Unit] = Future {
    blocking {
      val externalProcess =
        new ExternalProcessRunner(getMasterProcess(s"$installDir/bin", 8080), true)
      externalProcess.run(
        line => {
          if (line.contains("Bound MasterWebUI")) {
            new ExternalProcessRunner(getWorkerProcess(s"$installDir/bin", 8081), true)
              .run(
                workerLog => {
                  if (workerLog.contains("Successfully registered with master")) {
                    onClusterStart()
                  }

                },
                workerPID => onWorkerStart(workerPID)
              )
          }
        },
        pid => onMasterStart(pid)
      )
      new ExternalProcessRunner(getCmd(SparkProcesses.SHS), true)
        .run(_ => {}, pid => onHistoryServerStart(pid))

    }
  }

  def stopSparkProcesses(clusterId: Long, processes: Seq[ClusterProcess]): Unit = {
    processes.foreach { p =>
      if (p.pid.isDefined) {
        ProcessHandle.of(p.pid.get).ifPresent(p => p.destroy())
        clusterService.updateClusterProcess(clusterId, p.name, RunStatus.Stopped, "")
      }
    }
  }

}

object SparkProcessManager {
  def apply(installDir: String, clusterService: ClusterService): SparkProcessManager =
    new SparkProcessManager(installDir, clusterService, _ => {}, _ => {}, _ => {}, () => {})
}

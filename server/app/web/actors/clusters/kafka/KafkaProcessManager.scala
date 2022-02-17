package web.actors.clusters.kafka

import java.io.{BufferedReader, File, InputStreamReader}

import com.gigahex.commons.models.RunStatus

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try
import sys.process._
import web.actors.clusters.{ExternalProcessRunner, PIDMonitor}
import web.models.cluster.KafkaProcesses
import web.services.ClusterService

case class KafkaProcessManager(installDir: String,
                               private val onKafkaServerStartComplete: () => Unit,
                               private val beforeKafkaStart: Long => Unit,
                               private val beforeZkStart: Long => Unit) {

  def onZkStart(handler: Long => Unit): KafkaProcessManager        = this.copy(beforeZkStart = handler)
  def onKafkaServerStart(handler: () => Unit): KafkaProcessManager = this.copy(onKafkaServerStartComplete = handler)
  def withKafkaPID(handler: Long => Unit): KafkaProcessManager     = this.copy(beforeKafkaStart = handler)

  def getCmd(processName: String): Seq[String] = processName match {
    case KafkaProcesses.KAFKA_SERVER =>  Seq(s"${installDir}/bin/zookeeper-server-start.sh", s"${installDir}/config/zookeeper.properties")
    case KafkaProcesses.ZOOKEEPER => Seq(s"${installDir}/bin/kafka-server-start.sh", s"${installDir}/config/server.properties")
  }

  private def deleteRecursively(f: File): Boolean = {
    if (f.isDirectory) f.listFiles match {
      case files: Array[File] => files.foreach(deleteRecursively)
      case null =>
    }
    f.delete()
  }

  def startKafkaProcesses(implicit ec: ExecutionContext): Future[Unit] = Future {
    blocking {
      val externalProcess =
        new ExternalProcessRunner(getCmd(KafkaProcesses.KAFKA_SERVER), true)
      externalProcess.run(
        line => {
          if (line.contains("started")) {
            println("Starting the Kafka Server using KPM")

            new ExternalProcessRunner(getCmd(KafkaProcesses.ZOOKEEPER), true)
              .run(
                kafkaLog => {
                  if (kafkaLog.contains("started (kafka.server.KafkaServer)")) {
                    onKafkaServerStartComplete()
                  }

                },
                kafkaPID => beforeKafkaStart(kafkaPID)
              )
          }
        },
        pid => beforeZkStart(pid)
      )

    }
  }

  def deleteKafkaPackages(path: String): Try[Unit] = {
    Try {
      val dir = new File(path)
      if (dir.isDirectory) {
        dir.listFiles.foreach(deleteRecursively)
      }
      if (dir.exists && !dir.delete) {
        throw new Exception(s"Unable to delete ${dir.getAbsolutePath}")
      }
    }
  }

  def stopKafkaProcesses(clusterId: Long, clusterService: ClusterService)(implicit ec: ExecutionContext): Future[Unit] = Future {
    blocking {
      Process(s"${installDir}/bin/kafka-server-stop.sh").run().exitValue()
      clusterService.updateClusterProcess(clusterId, KafkaProcesses.KAFKA_SERVER, RunStatus.Stopped, "")
      Thread.sleep(18000)
      Process(s"${installDir}/bin/zookeeper-server-stop.sh").run().exitValue()
      clusterService.updateClusterProcess(clusterId, KafkaProcesses.ZOOKEEPER, RunStatus.Stopped, "")
    }
  }

}

object KafkaProcessManager {

  def apply(installDir: String): KafkaProcessManager = new KafkaProcessManager(installDir, () => {}, pid => {}, pid => {})
}


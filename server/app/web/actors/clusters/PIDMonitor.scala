package web.actors.clusters

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.lang

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import web.actors.clusters.PIDMonitor.{CheckProcessStatus, StartProcess}
import web.services.ClusterService
import com.gigahex.commons.models.RunStatus
import com.typesafe.scalalogging.LazyLogging
import web.actors.clusters.ServiceMessages.UpdateFromProcess

import concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class PIDMonitor(clusterId: Long,
                 pid: Long,
                 name: String,
                 cmdToStart: Seq[String],
                 maxRetryCount: Int,
                 retryIntervalInSeconds: Int,
                 clusterService: ClusterService)
    extends Actor
    with ActorLogging {
  private implicit val ec: ExecutionContext = context.dispatcher

  val checker = context.system.scheduler.scheduleWithFixedDelay(2.seconds, 2.seconds, self, CheckProcessStatus)

  private def handleMonitorCommand(retryCount: Int, currentPID: Long): Receive = {

    case StartProcess =>
      val newPID = new ProcessBuilder(cmdToStart :_*).start().pid()
      context.become(handleMonitorCommand(retryCount + 1, newPID))

    case CheckProcessStatus =>
      if (pid > 0 && ProcessHandle.of(pid).isPresent) {
        clusterService.updateClusterProcess(clusterId, name, RunStatus.Running, "", Some(currentPID))
      } else {
        context.parent ! UpdateFromProcess(name, RunStatus.Stopped)
        clusterService.updateClusterProcess(clusterId, name, RunStatus.Stopped, "", Some(currentPID))
        if (retryCount >= maxRetryCount) {
          self ! PoisonPill
        } else {
          context.system.scheduler.scheduleOnce(retryIntervalInSeconds.seconds, self, StartProcess)
        }
      }
  }

  override def receive: Receive = handleMonitorCommand(0, pid)

}

class LogThread(logStream: InputStream, handler: String => Unit) extends Runnable {
  override def run(): Unit = {
    val outputStream = logStream
    val reader = new InputStreamReader(outputStream)
    val br = new BufferedReader(reader)
    var logLine = br.readLine()
    while (logLine != null) {
      println(logLine)
      handler(logLine)
      logLine = br.readLine()
    }
    outputStream.close()
  }
}

class ExternalProcessRunner(cmd: Seq[String], showLogs: Boolean) extends LazyLogging {

  def run(handleLog: String => Unit, handlePID: Long => Unit) : Unit = {
    println(s"Running: ${cmd.mkString(" ")}")
    Try {
      val pb = new lang.ProcessBuilder(cmd: _*)
      logger.info(s"Java home: ${System.getProperty("java.home")}")
      pb.environment().put("JAVA_HOME", "/Library/Java/JavaVirtualMachines/graalvm-ce-java8-20.1.0/Contents/Home")
      val process = pb.start()


      handlePID(process.pid())
      val stdOut = new LogThread(process.getInputStream, handleLog)
      val stdErr = new LogThread(process.getErrorStream, handleLog)
      val thStdOut = new Thread(stdOut)
      val thStdErr = new Thread(stdErr)
      thStdOut.start()
      thStdErr.start()
    } match {
      case Failure(ex) =>
        ex.printStackTrace()
      case Success(out) => out
    }
  }

}

object PIDMonitor {
  case object CheckProcessStatus
  case object StartProcess
  def props(clusterId: Long,
            pid: Long,
            name: String,
            cmdToStart: Seq[String],
            maxRetryCount: Int,
            retryIntervalInSeconds: Int,
            clusterService: ClusterService): Props =
    Props(new PIDMonitor(clusterId, pid, name, cmdToStart, maxRetryCount, retryIntervalInSeconds, clusterService))

}


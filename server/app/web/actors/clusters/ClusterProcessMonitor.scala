package web.actors.clusters

import akka.actor.{Actor, ActorLogging, Props}
import com.gigahex.commons.models.RunStatus
import com.gigahex.commons.models.RunStatus.RunStatus
import web.actors.clusters.ClusterProcessMonitor.{GetProcessStatus, KillProcess, RestartProcess, StartProcess, UpdateProcessStatus}
import web.services.ClusterService
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext
import scala.sys.process._
import concurrent.duration._
import scala.util.{Failure, Success}

class ClusterProcessMonitor(clusterId: Long,
                            name: String,
                            runCommand: String,
                            healthCheckUrl: Option[String],
                            ws: WSClient,
                            clusterService: ClusterService)
    extends Actor
    with ActorLogging {

  private implicit val ec: ExecutionContext = context.dispatcher
  val processUpdater                        = context.system.scheduler.scheduleWithFixedDelay(3.seconds, 2.seconds, self, UpdateProcessStatus)

  private val localProcessLogger = ProcessLogger(log => {
    println(log)
  })

  private def handleProcess(state: RunStatus = RunStatus.NotStarted, localProcess: Option[Process] = None): Receive = {
    case StartProcess =>
      val runningProcess = Process(runCommand).run(localProcessLogger)
      context.become(handleProcess(state = RunStatus.Starting, localProcess = Some(runningProcess)))

    case GetProcessStatus => sender() ! state

    case KillProcess =>
      localProcess match {
        case None => log.info(s"Process $name is not running")
        case Some(p) =>
          log.info(s"Killing the process - ${name}")
          p.destroy()
          if (!processUpdater.isCancelled) {
            processUpdater.cancel()
          }
          clusterService.updateClusterProcess(clusterId, name, RunStatus.Stopped, "")
          context.parent ! ServiceMessages.UpdateFromProcess(name, RunStatus.Stopped)
      }

    case RestartProcess =>
      localProcess match {
        case None    => log.info(s"Process $name is not running")
        case Some(p) => p.destroy()
      }

    case UpdateProcessStatus =>
      localProcess match {
        case None =>
          clusterService.updateClusterProcess(clusterId, name, RunStatus.NotStarted, "")
        case Some(p) =>
          if (p.isAlive() && healthCheckUrl.isDefined) {
            ws.url(healthCheckUrl.get)
              .get()
              .flatMap(resp =>
                resp.status match {
                  case 200 =>
                    context.parent ! ServiceMessages.UpdateFromProcess(name, RunStatus.Running)
                    clusterService.updateClusterProcess(clusterId, name, RunStatus.Running, "")

                  case _ => clusterService.updateClusterProcess(clusterId, name, RunStatus.Waiting, resp.body)
              })
              .onComplete {
                case Failure(_) =>
                  clusterService.updateClusterProcess(clusterId, name, RunStatus.Starting, "")
                case Success(hasUpdated) =>
                  log.debug(s"Updated the cluster process: $name:$clusterId - $hasUpdated")
              }

          } else if(p.isAlive()){
            context.parent ! ServiceMessages.UpdateFromProcess(name, RunStatus.Running)
            clusterService.updateClusterProcess(clusterId, name, RunStatus.Running, "")
          } else {
            context.parent ! ServiceMessages.UpdateFromProcess(name, RunStatus.Stopped)
            clusterService.updateClusterProcess(clusterId, name, RunStatus.Stopped, "")
          }
      }

  }

  override def receive: Receive = handleProcess()

}

object ClusterProcessMonitor {
  def props(clusterId: Long,
            name: String,
            runCommand: String,
            healthCheckUrl: Option[String],
            ws: WSClient,
            clusterService: ClusterService) =
    Props(new ClusterProcessMonitor(clusterId, name, runCommand, healthCheckUrl, ws, clusterService))

  case object StartProcess
  case object RestartProcess
  case object KillProcess
  case object GetProcessStatus
  case object UpdateProcessStatus
}

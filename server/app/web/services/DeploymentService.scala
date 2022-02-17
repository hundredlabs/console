package web.services

import java.io.File
import java.nio.file.{FileSystems, Files, Paths, StandardOpenOption}

import akka.NotUsed
import akka.stream.alpakka.file.scaladsl.FileTailSource
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.gigahex.commons.models.{ DeploymentRunResponse, DeploymentRunResult, DeploymentView, JobConfig, NewDeploymentRequest, NewDeploymentRun, RunStatus}
import com.gigahex.commons.models.RunStatus.RunStatus
import javax.inject.Inject
import web.models.DeploymentRunHistory
import web.repo.DeploymentsRepo
import play.api.Configuration

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait DeploymentService {

  def newDeploymentJob(workspaceId: Long, run: NewDeploymentRun): Future[Option[Long]]

  def listDeployments(workspaceId: Long, projectId: Long): Future[Seq[DeploymentView]]

  def delete(workspaceId: Long, jobId: Long, deploymentId: Long): Future[Boolean]

  def getDeploymentRunResult(orgId: Long, deploymentRunId: Long): Future[Option[DeploymentRunResult]]

  def listDeploymentHistory(workspaceId: Long, projectId: Long, deploymentId: Long): Future[Option[DeploymentRunHistory]]

  def getDeploymentJob(orgId: Long, clusterId: String, deploymentRunId: Long): Future[Option[NewDeploymentRequest]]

  def updateDeployment(jobId: Long, workspaceId: Long, deploymentId: Long, jobConfig: JobConfig): Future[Boolean]

  def getDeploymentRunInstance(workspaceId: Long, clusterId: Long): Future[Option[DeploymentRunResponse]]

  def getDepoymentByName(orgId: Long, depName: String, project: String): Future[Option[NewDeploymentRequest]]

  def updateDeploymentRun(runId: Long, status: RunStatus): Future[Boolean]

  def updateActionStatus(deploymentWorkId: Long, actionId: String, status: RunStatus, logs: Seq[String], conf: Configuration)(
      implicit mat: Materializer): Future[Boolean]

  def getActionLogs(depRunId: Long, actionId: String, conf: Configuration): Source[String, NotUsed]

}

class DeploymentServiceImpl @Inject()(deploymentsRepo: DeploymentsRepo) extends DeploymentService {

  implicit val ec: ExecutionContext = deploymentsRepo.ec

  override def newDeploymentJob(workspaceId: Long, run: NewDeploymentRun): Future[Option[Long]] = deploymentsRepo.upsertDeploymentRun(workspaceId, run)

  override def listDeployments(workspaceId: Long, jobId: Long): Future[Seq[DeploymentView]] =
    deploymentsRepo.listDeployments(workspaceId, jobId)

  override def delete(workspaceId: Long, jobId: Long, deploymentId: Long): Future[Boolean] =
    deploymentsRepo.delete(workspaceId, jobId, deploymentId)

  override def getDeploymentRunResult(orgId: Long, deploymentRunId: Long): Future[Option[DeploymentRunResult]] =
    deploymentsRepo.getDeploymentResult(orgId, deploymentRunId)

  override def listDeploymentHistory(workspaceId: Long, projectId: Long, deploymentId: Long): Future[Option[DeploymentRunHistory]] =
    deploymentsRepo.listDeploymentHistory(workspaceId, projectId, deploymentId)

  override def getDeploymentJob(orgId: Long, clusterId: String, deploymentRunId: Long): Future[Option[NewDeploymentRequest]] =
    deploymentsRepo.getDeployment(orgId, clusterId, deploymentRunId)

  override def updateDeployment(jobId: Long, workspaceId: Long, deploymentId: Long, jobConfig: JobConfig): Future[Boolean] =
    deploymentsRepo.updateDeployment(jobId, workspaceId, deploymentId, jobConfig)

  override def getDeploymentRunInstance(workspaceId: Long, clusterId: Long): Future[Option[DeploymentRunResponse]] =
    deploymentsRepo.getDeploymentRunInstance(workspaceId, clusterId)

  override def getDepoymentByName(orgId: Long, depName: String, project: String): Future[Option[NewDeploymentRequest]] =
    deploymentsRepo.getDepoymentByName(orgId, depName, project)

  override def updateDeploymentRun(runId: Long, status: RunStatus): Future[Boolean] = deploymentsRepo.updateDeploymentRun(runId, status)

  import akka.stream.scaladsl._
  override def updateActionStatus(deploymentWorkId: Long, actionId: String, status: RunStatus, logs: Seq[String], conf: Configuration)(
      implicit mat: Materializer): Future[Boolean] = {
    val logsDirPath = s"${conf.underlying.getString("gigahex.logsDir")}/${deploymentWorkId}/"
    val logsDir     = new File(logsDirPath)
    if (!logsDir.exists()) {
      logsDir.mkdirs()
    }
    val logFile = new File(s"${logsDirPath}${actionId}.log")
    if (!logFile.exists()) {
      logFile.createNewFile()
    }
    val logsFilePath = Paths.get(s"${logsDirPath}${actionId}.log")
    val logsSource   = Source.fromIterator(() => logs.toIterator.map(_ + "\n"))
    logsSource.map(log => ByteString(log)).runWith(FileIO.toPath(logsFilePath, Set(StandardOpenOption.APPEND))).flatMap { r =>
      Future.successful(true)
    }
  }

  override def getActionLogs(depRunId: Long, actionId: String, conf: Configuration): Source[String, NotUsed] = {
    val fs       = FileSystems.getDefault
    val logsFile = s"${conf.underlying.getString("gigahex.logsDir")}/${depRunId}/${actionId}.log"
    FileTailSource.lines(
      path = fs.getPath(logsFile),
      maxLineSize = 8192,
      pollingInterval = 1000.millis
    )
  }

}

package web.repo

import com.gigahex.commons.models.{DeploymentRunInstance, DeploymentRunResponse, DeploymentRunResult, DeploymentView, JobConfig, NewDeploymentRequest, NewDeploymentRun}
import com.gigahex.commons.models.RunStatus.RunStatus
import web.models.DeploymentRunHistory

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentsRepo {

  val ec: ExecutionContext

  def upsertDeploymentRun(workspaceId: Long, run: NewDeploymentRun): Future[Option[Long]]

  def listDeployments(workspaceId: Long, jobId: Long): Future[Seq[DeploymentView]]

  def delete(workspaceId: Long, jobId: Long, deploymentId: Long): Future[Boolean]

  def getDeploymentConfig(orgId: Long, projectId: Long, deploymentId: Long): Future[Option[NewDeploymentRequest]]

  def getDeploymentResult(orgId: Long, deploymentRunId: Long): Future[Option[DeploymentRunResult]]

  def updateDeployment(jobId: Long, workspaceId: Long, deploymentId: Long, jobConfig: JobConfig): Future[Boolean]

  def listDeploymentHistory(workspaceId: Long, projectId: Long, deploymentId: Long): Future[Option[DeploymentRunHistory]]

  def getDeployment(orgId: Long, agentId: String, deploymentRunId: Long): Future[Option[NewDeploymentRequest]]

  def getDeploymentRunInstance(workspaceId: Long, clusterId: Long): Future[Option[DeploymentRunResponse]]

  def getDepoymentByName(orgId: Long, depName: String, project: String): Future[Option[NewDeploymentRequest]]

  def updateDeploymentRun(runId: Long, status: RunStatus): Future[Boolean]

  def updateActionStatus(deploymentWorkId: Long, actionId: String, status: RunStatus, logs: Seq[String]): Future[Boolean]

}

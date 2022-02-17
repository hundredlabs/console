package web.repo

import com.gigahex.commons.models.JobType.JobType
import com.gigahex.commons.models.{DeploymentJob, JVMException, NewDeploymentRequest}
import web.models.spark.JobInfoId
import web.models.{DeploymentDetail, JobInstanceView, JobListView, JobRunInfo, JobSummary, JobValue, JobView, RegisterJob, RegisterJobRequest, TaskInstance, TaskMeta}

import scala.concurrent.{ExecutionContext, Future, blocking}

trait JobRepository {

  val ec: ExecutionContext

  def getSparkTask(jobId: Long, runId: Long): Future[Option[Long]]

  /**
    * Upsert the Job info, during registration/re-trigger of same job
    * @param jobReq
    * @return jobId generated
    */
  def upsert(workspaceId: Long, jobReq: RegisterJobRequest, jobId: Option[Long] = None): Future[Long]

  def updateDescription(jobId: Long, workspaceId: Long, text: String): Future[Boolean]

  def addDeploymentConfig(workspaceId: Long, jobId: Long, clusterId: Long, request: NewDeploymentRequest): Future[Long]

  def saveDeploymentJob(orgId: Long, workspaceId: Long, job: DeploymentJob): Future[Long]

  /**
    * Get the history of a job
    * @param jobId
    * @return
    */
  def getLatestJobHistory(jobId: Long): Future[Seq[JobInstanceView]]

  def getDeploymentDetail(workspaceId: Long, deploymentId: Long): Future[Option[DeploymentDetail]]

  /**
    * Fetch the job details
    * @param jobId
    * @return If found, return the job view
    */
  def find(jobId: Long): Future[Option[JobView]]

  def getJobValue(jobId: Long): Option[JobValue]

  def getProjectByName(orgId: Long, name: String): Future[Option[Long]]

  def getJobSummary(jobId: Long): Future[Option[JobSummary]]

  def fetchTimelimitExceededJobRuns(): Future[Seq[Long]]

  def listJobsByCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Seq[JobListView]]

  def listJobs(orgId: Long, workspaceId: Long): Future[Seq[JobListView]]

  /**
    * Register the job run
    * @param jobId
    * @param task
    * @param maxJobRun
    * @param tz
    * @return
    */
  def addJobRun(jobId: Long, task: TaskMeta, maxJobRun: Int, tz: String): Future[Long]

  def getTaskHistory(jobId: Long, taskName: String): Future[Seq[TaskInstance]]

  def getJobRunDetail(jobId: Long, runId: Long): Future[String]

  /**
    * Update the status of the job
    * @param status
    * @param runId
    * @return
    */
  def updateJobStatus(status: String, runId: Long, tz: String): Future[Boolean]



  /**
    * Update the task status of the job
    * @param seqId
    * @param jobRunId
    * @param status
    * @return
    */
  def updateTaskStatus(orgId: Long, jobId: Long, jobRunId: Long, seqId: Int, status: String): Future[Boolean]

  def remove(jobId: Long): Future[Boolean]

  def saveJVMException(runId: Long, attemptId: String, e: JVMException, errJson: String): Future[Boolean]

  def getError(runId: Long, attemptId: String): Future[Seq[String]]

  def listJobsByOrgs(orgIds: Seq[Long], jobType: JobType): Future[Seq[JobInfoId]]

  def listJobRuns(jobId: Long): Future[Seq[JobRunInfo]]

}

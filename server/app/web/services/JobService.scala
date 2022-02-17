package web.services

import java.time.ZonedDateTime

import akka.pattern._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.gigahex.commons.models.JobType.JobType
import com.gigahex.commons.models.{DeploymentJob, ErrorType, JVMErrorStack, JVMException, JobType, NewDeploymentRequest, TraceElement}
import javax.inject.Inject
import web.models.common.JobErrorJson
import web.models.spark.JobInfoId
import web.models.{
  DeploymentDetail,
  JobInstanceView,
  JobListView,
  JobModified,
  JobRunInfo,
  JobRunView,
  JobSubmissionResult,
  JobSummary,
  JobValue,
  JobView,
  RegisterJobRequest,
  TaskMeta,
  TaskView
}
import web.repo.{JobRepository, LogsIndex}
import web.services.JobServiceClientActor.{CheckJobRunDetail, JobRunDetailReq, ListJobs}
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json

import concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait JobService {

  /**
    * The term project will be gradually used to denote any kind of project
    * @param jobId
    * @param jobReq
    * @return
    */
  def updateProject(orgId: Long, jobId: Long, jobReq: RegisterJobRequest): Future[JobModified]

  def getProjectByName(orgId: Long, name: String): Future[Option[Long]]

  def getJobSummary(projectId: Long): Future[Option[JobSummary]]

  def find(jobId: Long): Future[Option[JobView]]

  def updateJobCache(jobId: Long, cacheApi: SyncCacheApi, forceUpdate: Boolean = false): JobValue

  def getSparkTask(jobId: Long, runId: Long): Future[Either[Throwable, Option[Long]]]

  def listJobs(orgId: Long, workspaceId: Long): Future[Either[Throwable, Seq[JobListView]]]

  def saveDeploymentJob(orgId: Long, workspaceId: Long, job: DeploymentJob): Future[Long]

  def registerJob(orgId: Long, workspaceId: Long, request: RegisterJobRequest): Future[Long]

  def updateDescription(jobId: Long, workspaceId: Long, text: String): Future[Boolean]

  def addDeploymentConfig(workspaceId: Long, jobId: Long, clusterId: Long, request: NewDeploymentRequest): Future[Long]

  def getDeploymentDetail(workspaceId: Long, deploymentId: Long): Future[Option[DeploymentDetail]]

  def addJobRun(jobId: Long, tasks: TaskMeta, tz: Option[String] = None): Future[JobSubmissionResult]

  def getJobHistory(jobId: Long): Future[Seq[JobInstanceView]]

  def getTaskHistory(name: String, jobId: Long): Future[Either[Throwable, Seq[TaskView]]]

  def getJobRunDetail(jobId: Long, runId: Long): Future[Either[Throwable, JobRunView]]

  def updateJobStatus(status: String, runId: Long, tz: String): Future[JobModified]

  def fetchTimelimitExceededJobRuns(): Future[Seq[Long]]


  def saveError(runId: Long, attemptId: String, error: ErrorType): Future[Boolean]

  def getError(runId: Long, attemptId: String): Future[Seq[JVMErrorStack]]

  def updateTaskStatus(orgId: Long, jobId: Long, jobRunId: Long, seqId: Int, status: String): Future[JobModified]

  def remove(jobId: Long): Future[Boolean]

  def listJobsByCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Seq[JobListView]]

  def listJobs(orgIds: Seq[Long], jobType: JobType): Future[Seq[JobInfoId]]

  def listRuns(jobId: Long): Future[Seq[JobRunInfo]]

  def formatInterval(interval: Long): String = interval match {
    case x if x < 60                 => s"${x} secs"
    case x if x >= 60 && x < 3600    => s"${x / 60} mins"
    case x if x >= 3600 && x < 86400 => s"${x / 3600} hrs"
    case x if x >= 86400             => s"${x / 86400} days"
  }

}

class JobServiceClientActor(jobService: JobService, orgId: Long, out: ActorRef) extends Actor with JobErrorJson with ActorLogging {
  implicit val ec = context.dispatcher
  //context.system.scheduler.schedule(2.seconds, 2.seconds, self, CheckJobRunDetail)

  def jobRunState(state: Option[JobRunView], jobId: Long, runId: Long): Receive = {
    case JobRunDetailReq(jobId, runId) =>
      val res = jobService.getJobRunDetail(jobId.toLong, runId.toLong).flatMap {
        case Left(err) => Future.failed(err)
        case Right(v) =>
          context.become(jobRunState(Some(v), jobId.toLong, runId.toLong))
          Future.successful(v)
      }
      res pipeTo (out)

    case ListJobs(s) =>
      jobService
        .listJobs(orgId, 1)
        .flatMap {
          case Left(err)    => Future.failed(err)
          case Right(value) => Future.successful(value)
        }
        .pipeTo(out)
      if (s.equalsIgnoreCase("send")) {
        context.system.scheduler.scheduleAtFixedRate(2.seconds, 2.seconds, self, ListJobs(""))
      }

    case CheckJobRunDetail =>
      jobService.getJobRunDetail(jobId, runId).map(_.toOption).onComplete {
        case Failure(_) =>
        case Success(value) =>
          (value, state) match {
            case (Some(n), Some(o)) if !(n.equals(o)) =>
              log.info("updating the state")
              out ! n
              context.become(jobRunState(value, jobId, runId))
            case (Some(n), None) =>
              out ! n
              context.become(jobRunState(value, jobId, runId))
            case _ =>
          }
      }
  }

  override def receive = jobRunState(None, 0L, 1L)
}

object JobServiceClientActor {
  case class JobRunDetailReq(jobId: String, runId: String)
  case class ListJobs(send: String)
  case object CheckJobRunDetail
  def props(jobService: JobService, orgId: Long)(out: ActorRef): Props = Props(new JobServiceClientActor(jobService, orgId, out))
}

class JobServiceImpl @Inject()(jobRepository: JobRepository, indexer: LogsIndex) extends JobService with JobErrorJson {
  val maxJobRun   = 5
  val maxJobCount = 5
  implicit val ec = jobRepository.ec

  override def registerJob(orgId: Long, workspaceId: Long, jobReq: RegisterJobRequest): Future[Long] =
    for {
      jobs <- jobRepository.listJobs(orgId, workspaceId)
      result <- if (jobs.map(_.name).contains(jobReq.jobConfig.jobName) || jobs.size < maxJobCount) {
        jobRepository
          .upsert(workspaceId, jobReq)

      } else {
        Future.successful(0L)
      }

    } yield result

  override def updateDescription(jobId: Long, workspaceId: Long, text: String): Future[Boolean] = jobRepository.updateDescription(jobId, workspaceId, text)

  override def updateProject(orgId: Long, jobId: Long, jobReq: RegisterJobRequest): Future[JobModified] =
    jobRepository
      .upsert(orgId, jobReq, Some(jobId))
      .map(r => JobModified(jobId, r > 0))

  override def getProjectByName(orgId: Long, name: String): Future[Option[Long]] = jobRepository.getProjectByName(orgId, name)

  override def getJobSummary(projectId: Long): Future[Option[JobSummary]] = jobRepository.getJobSummary(projectId)

  override def find(jobId: Long): Future[Option[JobView]] = jobRepository.find(jobId)

  override def updateJobCache(jobId: Long, cacheApi: SyncCacheApi, forceUpdate: Boolean = false): JobValue = {
    val emptyJv = JobValue(jobId, JobType.default, "", Seq())
    if (forceUpdate) {
      val jv = jobRepository.getJobValue(jobId)
      cacheApi.set(jobId.toString, jv)
      jv.getOrElse(emptyJv)
    } else {
      cacheApi.getOrElseUpdate[JobValue](jobId.toString) {
        jobRepository.getJobValue(jobId) match {
          case None        => emptyJv
          case Some(value) => value
        }
      }
    }
  }

  def getSparkTask(jobId: Long, runId: Long): Future[Either[Throwable, Option[Long]]] = {
    jobRepository
      .getSparkTask(jobId, runId)
      .map(Right(_))
      .recover {
        case e: Exception => Left(e)
      }
  }

  override def listJobs(orgId: Long, workspaceId: Long): Future[Either[Throwable, Seq[JobListView]]] =
    jobRepository.listJobs(orgId, workspaceId).map(Right(_)).recover {
      case e: Exception => Left(e)
    }

  override def saveDeploymentJob(orgId: Long, workspaceId: Long, job: DeploymentJob): Future[Long] =
    jobRepository.saveDeploymentJob(orgId, workspaceId, job)

  override def addDeploymentConfig(workspaceId: Long, jobId: Long, clusterId: Long, request: NewDeploymentRequest): Future[Long] =
    jobRepository.addDeploymentConfig(workspaceId, jobId, clusterId, request)

  override def getDeploymentDetail(workspaceId: Long, deploymentId: Long): Future[Option[DeploymentDetail]] =
    jobRepository.getDeploymentDetail(workspaceId, deploymentId)

  override def addJobRun(jobId: Long, tasks: TaskMeta, tz: Option[String] = None): Future[JobSubmissionResult] =
    jobRepository
      .addJobRun(jobId, tasks, maxJobRun, tz.getOrElse("GMT"))
      .map(i => JobSubmissionResult(jobId, Some(i)))
      .recover {
        case e: Exception => JobSubmissionResult(jobId, None, e.getMessage)
      }

  override def getJobHistory(jobId: Long): Future[Seq[JobInstanceView]] = {
    jobRepository
      .getLatestJobHistory(jobId)

  }

  override def getTaskHistory(name: String, jobId: Long): Future[Either[Throwable, Seq[TaskView]]] =
    jobRepository
      .getTaskHistory(jobId, name)
      .map {
        case xs if xs.isEmpty => Left(new IllegalArgumentException(s"Job with id, ${jobId} not found"))
        case xs =>
          val ts = xs.map { x =>
            TaskView(
              name = x.name,
              seqId = x.seqId,
              taskStatus = x.status,
              taskType = x.taskType,
              runtime = getRuntime(x.status, x.startTime, x.endTime),
              started = x.startTime match {
                case None        => "--"
                case Some(value) => formatInterval(ZonedDateTime.now().toEpochSecond - value.toEpochSecond) + " ago"
              },
              runId = Some(x.jobRunId)
            )
          }
          Right(ts)
      }
      .recover {
        case e: Exception => Left(e)
      }

  private[this] def getRuntime(status: String, started: Option[ZonedDateTime], endTime: Option[ZonedDateTime]): String =
    (status, started, endTime) match {
      case (x, _, _)                       => "--"
      case (_, Some(started), None)        => formatInterval(ZonedDateTime.now().toEpochSecond - started.toEpochSecond)
      case (_, Some(startTs), Some(endTs)) => formatInterval(endTs.toEpochSecond - startTs.toEpochSecond)
    }

  override def getJobRunDetail(jobId: Long, runId: Long): Future[Either[Throwable, JobRunView]] =
    jobRepository
      .getJobRunDetail(jobId, runId)
      .map { status =>
        Right(JobRunView(jobId, runId, status))
      }
      .recover {
        case e: Exception => Left(e)
      }

  override def updateJobStatus(status: String, runId: Long, tz: String): Future[JobModified] =
    jobRepository
      .updateJobStatus(status, runId, tz)
      .map { x =>
        JobModified(runId, x)
      }
      .recover {
        case e: Exception => JobModified(runId, false, s"${e.getClass.getName} : ${e.getMessage}")
      }

  override def fetchTimelimitExceededJobRuns(): Future[Seq[Long]] = jobRepository.fetchTimelimitExceededJobRuns()

  override def saveError(runId: Long, attemptId: String, error: ErrorType): Future[Boolean] = error match {
    case e: JVMException => jobRepository.saveJVMException(runId, attemptId, e, Json.toJson(error).toString())
  }

  override def getError(runId: Long, attemptId: String): Future[Seq[JVMErrorStack]] = {
    def toString(elem: TraceElement): String = {
      val sb = new StringBuilder()
      sb.append("at ")
      sb.append(elem.className)
      sb.append("." + elem.methodName)
      sb.append("(")
      sb.append(elem.fileName)
      if (elem.lineNum > 0) {
        sb.append(s":${elem.lineNum}")
      }
      sb.append(")")
      sb.toString()
    }
    jobRepository.getError(runId, attemptId).map { e =>
      e.map(x => Json.parse(x).as[ErrorType])
        .map { case x: JVMException => x }
        .map(x => JVMErrorStack(x.message, x.level, x.traces.map(toString(_))))
    }
  }

  override def updateTaskStatus(orgId: Long, jobId: Long, jobRunId: Long, seqId: Int, status: String): Future[JobModified] =
    jobRepository
      .updateTaskStatus(orgId, jobId, jobRunId, seqId, status)
      .map(x => JobModified(jobId, x))
      .recover {
        case e: Exception => JobModified(jobId, false, s"${e.getClass.getName} : ${e.getMessage}")
      }

  override def remove(jobId: Long): Future[Boolean] = {
    for {
      jobDeleted   <- jobRepository.remove(jobId)
    } yield (jobDeleted)
  }

  override def listJobsByCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Seq[JobListView]] =
    jobRepository.listJobsByCluster(orgId, workspaceId, clusterId)

  override def listJobs(orgIds: Seq[Long], jobType: JobType): Future[Seq[JobInfoId]] = jobRepository.listJobsByOrgs(orgIds, jobType)

  override def listRuns(jobId: Long): Future[Seq[JobRunInfo]] = jobRepository.listJobRuns(jobId)

}

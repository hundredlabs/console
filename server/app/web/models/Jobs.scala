package web.models

import java.time.ZonedDateTime

import com.gigahex.commons.models.{ClusterProvider, DeploymentJob, DeploymentRunResponse,  JobConfig, JobType, NewDeploymentRequest, SparkJobConfig}
import com.gigahex.commons.models.JobType.JobType
import web.models.spark.{ExecutorMetricInstance, ExecutorMetricResponse, SparkLog, SparkLogWSReq}
import web.services.JobServiceClientActor.{JobRunDetailReq, ListJobs}
import scalikejdbc._
import scalikejdbc.WrappedResultSet
import play.api.libs.json._

case class Job(id: Option[Long],
               name: String,
               description: Option[String],
               authorId: Long,
               dtCreated: ZonedDateTime,
               lastUpdated: ZonedDateTime)

object Job extends SQLSyntaxSupport[Job] {
  override val tableName = "jobs"

  def apply(rs: WrappedResultSet) =
    new Job(
      rs.longOpt("id"),
      rs.string("name"),
      rs.stringOpt("description"),
      rs.long("author_id"),
      rs.zonedDateTime("dt_created"),
      rs.zonedDateTime("last_updated")
    )
}

case class JobListView(id: Long, name: String, runs: Seq[JobInstanceSnip], jobType: String)
case class JobInstanceSnip(runId: Long, runSeqId: Long, status: String, lastTaskId: Option[Long] = None)
case class JobInstance(id: Option[Long],
                       jobId: Long,
                       status: String,
                       taskCount: Int,
                       taskCompleted: Int,
                       dtStarted: ZonedDateTime,
                       lastUpdated: ZonedDateTime)

object JobInstance extends SQLSyntaxSupport[JobInstance] {
  override val tableName = "job_history"

  def apply(rs: WrappedResultSet) =
    new JobInstance(
      rs.longOpt("id"),
      rs.long("job_id"),
      rs.string("status"),
      rs.int("task_count"),
      rs.int("task_completed"),
      rs.zonedDateTime("dt_started"),
      rs.zonedDateTime("last_updated")
    )

}

case class TaskMeta(name: String,
                    taskType: String,
                    jobRunId: Option[Long] = None,
                    deploymentRunId: Option[Long] = None,
                    actionId: Option[String] = None)

case class TaskInstance(runId: Option[Long],
                        jobRunId: Long,
                        name: String,
                        taskType: String,
                        seqId: Int,
                        status: String,
                        startTime: Option[ZonedDateTime],
                        endTime: Option[ZonedDateTime])

object TaskInstance extends SQLSyntaxSupport[TaskInstance] {
  override val tableName = "task_history"

  def apply(rs: WrappedResultSet) =
    new TaskInstance(
      rs.longOpt("id"),
      rs.long("job_run_id"),
      rs.string("name"),
      rs.string("task_type"),
      rs.int("seq_id"),
      rs.string("status"),
      rs.zonedDateTimeOpt("start_time"),
      rs.zonedDateTimeOpt("end_time")
    )

}

case class JobSubmissionResponse(jobRunId: Long, taskIds: Seq[Long])
case class JobView(id: Long, name: String, description: Option[String], jobType: JobType = JobType.default)
case class JobInstanceView(id: Long,
                           runIndex: Int,
                           deploymentId: Long,
                           deploymentName: String,
                           status: String,
                           startedAt: String,
                           runtime: String,
                           avgMemUsed: String,
                           avgCpuUsed: String)
case class JobHistoryView(id: Long, name: String, runs: Seq[JobInstanceView])
case class TaskView(name: String,
                    seqId: Int,
                    taskType: String,
                    taskStatus: String,
                    runtime: String,
                    started: String,
                    runId: Option[Long] = None)
case class JobRunView(id: Long, runId: Long, status: String)
case class SparkJobRunInstance(jobId: Long, taskName: String, sparkApp: String, jobRunId: Long, appAttemptId: String)

object JobViewsBuilder {

  case class JobInstanceRecord(jobId: Long,
                               name: String,
                               jobLastUpdated: Long,
                               runIndex: Long,
                               isSoftDeleted: Boolean,
                               runId: Option[Long],
                               status: Option[String],
                               startedTs: Option[Long],
                               jobType: String)

  def buildJobView(xs: List[WrappedResultSet]): Option[JobView] = xs match {
    case head :: tl =>
      val jv = JobView(head.long("id"), head.string("name"), head.stringOpt("description"))
      Some(jv)
    case Nil => None
  }

  def buildJobInstanceView(result: WrappedResultSet): JobInstanceRecord = {
    JobInstanceRecord(
      jobId = result.long("id"),
      name = result.string("name"),
      jobLastUpdated = result.zonedDateTime("last_updated").toEpochSecond(),
      runIndex = result.longOpt("run_index").getOrElse(0),
      isSoftDeleted = false,
      runId = result.longOpt("run_id"),
      status = result.stringOpt("status"),
      startedTs = result.zonedDateTimeOpt("dt_started").map(_.toEpochSecond),
      jobType = result.string("job_type")
    )
  }

}
case class TaskHistoryReq(name: String)
case class LogsSearchResponse(timeTaken: Long,
                              resultSize: Long,
                              logs: Seq[SparkLog],
                              lastOffset: Long,
                              levels: Seq[String] = Seq(),
                              classnames: Seq[String] = Seq(),
                              startDate: Option[String] = None,
                              endDate: Option[String] = None)
case class LogSearchRequest(query: String,
                            filters: Map[String, Array[String]] = Map.empty,
                            startTime: Option[Long] = None,
                            endTime: Option[Long] = None,
                            lastOffset: Long = 0)

case class ExecutorMetricSummary(executorId: String, maxMemory: Long, avgUsedMemory: Long, avgProcessCpuUsage: Double)

case class JVMNonHeapUsage(used: Long,
                           max: Long,
                           fracUsage: Double,
                           init: Long,
                           codeCacheUsed: Long,
                           compressedClassSpaceUsed: Long,
                           metaspaceUsed: Long)
case class JVMHeapUsage(init: Long,
                        used: Long,
                        max: Long,
                        fracUsage: Double,
                        edenSpaceUsed: Long,
                        oldGenUsed: Long,
                        survivorSpaceUsed: Long)
case class JVMGCTime(marksweepCount: Long, marksweepTime: Long, scavengeCount: Long, scavengeTime: Long)
case class CPUMetric(processUsage: Double, systemUsage: Double)
case class JVMMemOverallUsage(swapSize: Long, swapFree: Long, freeMemory: Long, totalUsed: Long)
case class InstantMetric(timestamp: Long,
                         timeOffset: Float,
                         heapUsage: JVMHeapUsage,
                         nonHeapUsage: JVMNonHeapUsage,
                         gcTime: JVMGCTime,
                         jvmOverall: JVMMemOverallUsage,
                         cpu: CPUMetric)
case class ExecutorRuntimeMetric(executorId: String,
                                 status: String,
                                 address: String,
                                 cpuAllocated: Int,
                                 jvmMemSize: Long,
                                 jvmMemAllocatedSize: Long,
                                 cpusAvailable: Long,
                                 metrics: Seq[InstantMetric],
                                 metricTimeUnit: Option[String] = None)
case class JobRunInfo(runId: Long, runSeq: Int)
case class RegisterJobRequest(jobType: JobType, jobConfig: JobConfig, depId: String, clusterId: Long)
case class UpdateJobDescription(text: String)
case class DeploymentDetail(clusterId: Long, clusterName: String, status: String, deploymentName: String, jobConfig: JobConfig)

trait JobFormats {
  implicit val jbTypeFormat             = Json.formatEnum(JobType)
  implicit val clusterProviderFmt       = Json.formatEnum(ClusterProvider)
//  implicit val clusterStatusFmt          = Json.formatEnum(ClusterStatus)
  implicit val jobRegistrationResultFmt = Json.format[JobRegistrationResult]
  implicit val sparkConfigFmt           = Json.format[SparkProjectConfig]
  implicit val sparkJobConfigFmt        = Json.format[SparkJobConfig]
  implicit val jobConfigFmt             = Json.format[JobConfig]
  implicit val deploymentDetailFmt      = Json.format[DeploymentDetail]
  implicit val jobDeploymentFmt         = Json.format[DeploymentJob]
  implicit val addDeploymentConfigFmt   = Json.format[NewDeploymentRequest]
  implicit val registerJobRequestFmt    = Json.format[RegisterJobRequest]

  //Deployment json formats
  implicit val deploymentRunResponseFmt = Json.format[DeploymentRunResponse]

  implicit val jobConfig              = Json.format[ProjectConfig]
  implicit val jobSubmitFormat        = Json.format[RegisterJob]
  implicit val jobTaskMetaFmt         = Json.format[TaskMeta]
  implicit val jobFormat              = Json.format[JobView]
  implicit val jobInstanceFmt         = Json.format[JobInstanceView]
  implicit val jobHistoryViewFmt      = Json.format[JobHistoryView]
  implicit val taskView               = Json.format[TaskView]
  implicit val jobRunViewFmt          = Json.format[JobRunView]
  implicit val jobFmt                 = Json.format[Job]
  implicit val jobWSFormat            = Json.format[JobRunDetailReq]
  implicit val jobListWSFmt           = Json.format[ListJobs]
  implicit val taskFetchFormat        = Json.format[TaskHistoryReq]
  implicit val jobRunSnipFmt          = Json.format[JobInstanceSnip]
  implicit val jobViewSnipFmt         = Json.format[JobListView]
  implicit val jobSubmissionFrmt      = Json.format[JobSubmissionResponse]
  implicit val sparkLogFmt            = Json.format[SparkLog]
  implicit val logsSearchResponseFmt  = Json.format[LogsSearchResponse]
  implicit val logSearchRequestFmt    = Json.format[LogSearchRequest]
  implicit val execMetricInstanceFmt  = Json.format[ExecutorMetricInstance]
  implicit val execMetricsResponseFmt = Json.format[ExecutorMetricResponse]
  implicit val sparkLogReq            = Json.format[SparkLogWSReq]
  implicit val execMetricSumFmt       = Json.format[ExecutorMetricSummary]

  //executor runtime metrics
  implicit val nonheapUsageFmt  = Json.format[JVMNonHeapUsage]
  implicit val gcTimeFmt        = Json.format[JVMGCTime]
  implicit val heapUsageFmt     = Json.format[JVMHeapUsage]
  implicit val jvmOverallFmt    = Json.format[JVMMemOverallUsage]
  implicit val cpuMetricFmt     = Json.format[CPUMetric]
  implicit val instantMetricFmt = Json.format[InstantMetric]
  implicit val execRuntimeFmt   = Json.format[ExecutorRuntimeMetric]
  implicit val jobUpdateDesc = Json.format[UpdateJobDescription]
  implicit val jobRunInfoFmt = Json.format[JobRunInfo]

}

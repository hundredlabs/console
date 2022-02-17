package web.services

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import com.gigahex.commons.events._
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{AddMetricRequest, GxApplicationMetricReq, NewExecutor}
import com.typesafe.scalalogging.LazyLogging
import javax.inject.{Inject, Singleton}
import web.analyzers.spark.StageAnalyzer
import web.models.{ExecutorMetricSummary, ExecutorRuntimeMetric, LogSearchRequest, LogsSearchResponse, SparkJobRunInstance}
import web.models.spark.{
  AppCurrentState,
  CPUTimeSerie,
  CompareResponse,
  CpuCoreTimeSerie,
  ExecutionMetricSummary,
  ExecutorMetricResponse,
  HeapMemoryTimeSerie,
  JobMetricResponse,
  JobRun,
  MemoryTimeSerie,
  MetricObservationResult,
  OffHeapMemoryTimeSerie,
  OverallRuntimeMetric,
  RawSparkMetric,
  SparkAggMetrics,
  SparkAppInfo,
  SparkLog,
  SparkStageSummary,
  StageState,
  WorkerRuntimeMetrics
}
import web.repo.{LogsIndex, SparkEventsRepo}
import web.utils.TimeUnit

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

trait SparkEventService {

  def appStarted(runId: Long, event: ApplicationStarted, tz: String): Future[Boolean]

  def appEnded(jobId: Long, runId: Long, event: ApplicationEnded): Future[Boolean]

  def jobStarted(jobId: Long, runId: Long, event: JobStarted): Future[Boolean]

  def jobEnded(jobId: Long, runId: Long, event: JobEnded): Future[Boolean]

  def stageSubmitted(jobId: Long, runId: Long, event: StageSubmitted): Future[Boolean]

  def stageCompleted(jobId: Long, runId: Long, event: StageCompleted): Future[Boolean]

  def taskStarted(jobId: Long, runId: Long, event: TaskStarted): Future[Boolean]

  def taskCompleted(jobId: Long, runId: Long, event: TaskCompleted): Future[Boolean]

  def publishAppState(runId: Long, metric: GxApplicationMetricReq, timezone: String): Future[Int]

  def saveRuntimeMetrics(workspaceId: Long, runId: Long, request: AddMetricRequest): Future[Int]

  def addExecutorMetric(event: NewExecutor): Future[Int]

  def getRunningSparkJob(jobId: Long, taskName: String): Future[Seq[SparkJobRunInstance]]

  def updateSparkMetric(jobId: Long,
                        runId: Long,
                        appAttemptId: String,
                        avgTaskRuntime: Long,
                        maxTaskRuntime: Long,
                        totalExecutorRuntime: Long,
                        totalJvmGCtime: Long): Future[Boolean]

  def getSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Either[Throwable, SparkAggMetrics]]

  def getSparkAppStatus(jobId: Long, runId: Long, appId: String): Future[Option[RunStatus]]

  def getSparkJobMetrics(jobId: Long,
                         runId: Long,
                         appAttemptId: String,
                         startTime: Long,
                         endTime: Long): Future[Either[Throwable, JobMetricResponse]]

  def getSparkAggMetricsAll(jobId: Long, runId: Long): Future[Either[Throwable, SparkAppInfo]]

  def getSparkStageMetrics(jobId: Long, runId: Long, appAttemptId: String, sparkJobId: Int): Future[Either[Throwable, Seq[StageState]]]

  def getRawSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[RawSparkMetric]]

  def getExecutorMetrics(runId: Long, attemptId: String): Future[Option[ExecutorMetricResponse]]

  def getExecutionMetrics(runId: Long, attemptId: String): Future[Option[ExecutionMetricSummary]]

  def listExecutorSummaries(jobId: Long, runId: Long): Future[Seq[ExecutorMetricSummary]]

  def getWorkersRuntimeMetrics(runId: Long, attemptId: String, workerId: String): Future[Option[WorkerRuntimeMetrics]]

  def listSparkAppsByStatus(status: RunStatus): Future[Seq[AppCurrentState]]

  def getSparkOverviewMetric(jobId: Long, runId: Long, attemptId: String): Future[Option[OverallRuntimeMetric]]

  def getCPUTimeSerie(runId: Long, attemptId: String, startTime: Option[Long], endTime: Option[Long]): Future[Option[CPUTimeSerie]]

  def getMemTimeSerie(runId: Long, attemptId: String, startTime: Option[Long], endTime: Option[Long]): Future[Option[MemoryTimeSerie]]

  def getHeapMemTimeSerie(runId: Long,
                          attemptId: String,
                          startTime: Option[Long],
                          endTime: Option[Long]): Future[Option[HeapMemoryTimeSerie]]

  def getOffHeapMemTimeSerie(runId: Long,
                             attemptId: String,
                             startTime: Option[Long],
                             endTime: Option[Long]): Future[Option[OffHeapMemoryTimeSerie]]

  def getCpuCoreTimeSerie(runId: Long, attemptId: String, startTime: Option[Long], endTime: Option[Long]): Future[Option[CpuCoreTimeSerie]]

  def getStageMetricDetail(runId: Long,
                           attemptId: String,
                           stageId: Int,
                           parentJobId: Int,
                           stageAttemptId: Int): Future[Option[SparkStageSummary]]

  def getExecutorRuntimeMetrics(jobId: Long, runId: Long, executorId: String): Future[Option[ExecutorRuntimeMetric]]
  //Log related apis
  def indexLogs(jobId: Long, runId: Long, lines: Seq[SparkLog]): Future[Int]

  def getLogs(jobId: Long, runId: Long, tz: Option[String], lastTs: Long): Future[LogsSearchResponse]

  def searchLogs(jobId: Long, runId: Long, tz: Option[String], searchRequest: LogSearchRequest): Future[LogsSearchResponse]

  def analyzeRuntimeMetrics(jobId: Long, runId: Long, attemptId: String): Future[Seq[MetricObservationResult]]

  def fetchRunComparison(run: JobRun): Future[Option[CompareResponse]]

}
@Singleton
class SparkEventServiceImpl @Inject()(sparkEventsRepo: SparkEventsRepo, indexer: LogsIndex) extends SparkEventService with LazyLogging {
  implicit val ec: ExecutionContext = sparkEventsRepo.blockingEC
  val driverLogs                    = mutable.HashMap.empty[Long, Seq[String]]

  private def toTime(epoch: Long): ZonedDateTime = {
    ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())

  }

  override def getExecutorMetrics(runId: Long, attemptId: String): Future[Option[ExecutorMetricResponse]] =
    sparkEventsRepo.getExecutorMetric(runId, attemptId)

  override def getExecutionMetrics(runId: Long, attemptId: String): Future[Option[ExecutionMetricSummary]] =
    sparkEventsRepo.getExecutionMetrics(runId, attemptId)

  override def indexLogs(jobId: Long, runId: Long, lines: Seq[SparkLog]): Future[Int] = {
    indexer.addLogs(jobId, runId, "", lines)
  }

  override def listExecutorSummaries(jobId: Long, runId: Long): Future[Seq[ExecutorMetricSummary]] =
    sparkEventsRepo.listExecutorSummaries(jobId, runId)

  override def getWorkersRuntimeMetrics(runId: Long, attemptId: String, workerId: String): Future[Option[WorkerRuntimeMetrics]] =
    sparkEventsRepo.getWorkersRuntimeMetrics(runId, attemptId, workerId)

  override def listSparkAppsByStatus(status: RunStatus): Future[Seq[AppCurrentState]] = sparkEventsRepo.listSparkAppByStatus(status)

  override def getSparkOverviewMetric(jobId: Long, runId: Long, attemptId: String): Future[Option[OverallRuntimeMetric]] =
    sparkEventsRepo.getSparkOvervallMetric(jobId, runId, attemptId)

  override def getCPUTimeSerie(runId: Long,
                               attemptId: String,
                               startTime: Option[Long],
                               endTime: Option[Long]): Future[Option[CPUTimeSerie]] =
    sparkEventsRepo.getCPUTimeSerie(runId, attemptId, startTime.map(toTime(_)), endTime.map(toTime(_)))

  override def getMemTimeSerie(runId: Long,
                               attemptId: String,
                               startTime: Option[Long],
                               endTime: Option[Long]): Future[Option[MemoryTimeSerie]] =
    sparkEventsRepo.getMemTimeSerie(runId, attemptId, startTime.map(toTime(_)), endTime.map(toTime(_)))

  override def getHeapMemTimeSerie(runId: Long,
                                   attemptId: String,
                                   startTime: Option[Long],
                                   endTime: Option[Long]): Future[Option[HeapMemoryTimeSerie]] =
    sparkEventsRepo.getHeapMemTimeSerie(runId, attemptId, startTime.map(toTime(_)), endTime.map(toTime(_)))

  override def getOffHeapMemTimeSerie(runId: Long,
                                      attemptId: String,
                                      startTime: Option[Long],
                                      endTime: Option[Long]): Future[Option[OffHeapMemoryTimeSerie]] =
    sparkEventsRepo.getOffHeapMemTimeSerie(runId, attemptId, startTime.map(toTime(_)), endTime.map(toTime(_)))

  override def getCpuCoreTimeSerie(runId: Long,
                                   attemptId: String,
                                   startTime: Option[Long],
                                   endTime: Option[Long]): Future[Option[CpuCoreTimeSerie]] = {
    sparkEventsRepo.getCoresUtilization(runId, attemptId, startTime.map(toTime), endTime.map(toTime(_)))
  }

  override def getStageMetricDetail(runId: Long,
                                    attemptId: String,
                                    stageId: Int,
                                    parentJobId: Int,
                                    stageAttemptId: Int): Future[Option[SparkStageSummary]] = {
    sparkEventsRepo.getStageMetricDetail(runId, attemptId, stageId, parentJobId, stageAttemptId)
  }

  override def getExecutorRuntimeMetrics(jobId: Long, runId: Long, executorId: String): Future[Option[ExecutorRuntimeMetric]] =
    sparkEventsRepo
      .getExecutorRuntimeMetrics(jobId, runId, executorId)
      .map(ermOpt =>
        ermOpt.map { erm =>
          val ts           = erm.metrics.map(_.timestamp)
          val minTs        = ts.min / 1000
          val withTsOffset = erm.metrics.map(e => e.copy(timeOffset = e.timestamp / 1000 - minTs))
          erm.copy(metrics = withTsOffset, metricTimeUnit = Some(TimeUnit.SEC.toString))
      })

  override def getLogs(jobId: Long, runId: Long, tz: Option[String], lastTs: Long): Future[LogsSearchResponse] = {
    indexer.getLogs(jobId, runId, tz.getOrElse("GMT"), lastTs).map { r =>
      val modLogs = r.logs.map { l =>
        val df         = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss.SSS").withZone(ZoneId.of(tz.getOrElse("GMT")))
        val readableTs = df.format(Instant.ofEpochMilli(l.timestamp))
        l.copy(readableTime = Some(readableTs))
      }
      r.copy(logs = modLogs)
    }
  }

  override def searchLogs(jobId: Long, runId: Long, tz: Option[String], searchRequest: LogSearchRequest): Future[LogsSearchResponse] =
    indexer.searchLogs(searchRequest, jobId, runId).map { r =>
      val modLogs = r.logs.map { l =>
        val df         = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss.SSS").withZone(ZoneId.of(tz.getOrElse("GMT")))
        val readableTs = df.format(Instant.ofEpochMilli(l.timestamp))
        l.copy(readableTime = Some(readableTs))
      }
      r.copy(logs = modLogs)
    }

  import StageAnalyzer._
  override def analyzeRuntimeMetrics(jobId: Long, runId: Long, attemptId: String): Future[Seq[MetricObservationResult]] = {
    sparkEventsRepo
      .getAllStages(jobId, runId, attemptId)
      .map(m => {
        val successStages = m.filter(_.numTasksCompleted > 0)
        Seq(longGCTime(successStages), findSlowTasks(successStages), longShuffleReadTime(successStages), tooManySmallTasks(successStages)) ++ getErrorInStage(
          m)
      })
      .map(_.sortWith((x, y) => x.severityLevel > y.severityLevel))
  }

  override def fetchRunComparison(runs: JobRun): Future[Option[CompareResponse]] = sparkEventsRepo.fetchRunComparison(runs)

  override def appStarted(runId: Long, event: ApplicationStarted, tz: String): Future[Boolean] =
    sparkEventsRepo.appStarted(runId, event, tz)

  override def appEnded(jobId: Long, runId: Long, event: ApplicationEnded): Future[Boolean] =
    sparkEventsRepo.appEnded(jobId, runId, event)

  override def jobStarted(jobId: Long, runId: Long, event: JobStarted): Future[Boolean] =
    sparkEventsRepo.jobStarted(jobId, runId, event)

  override def jobEnded(jobId: Long, runId: Long, event: JobEnded): Future[Boolean] =
    sparkEventsRepo.jobEnded(jobId, runId, event)

  override def stageSubmitted(jobId: Long, runId: Long, event: StageSubmitted): Future[Boolean] =
    sparkEventsRepo.stageSubmitted(jobId, runId, event)

  override def stageCompleted(jobId: Long, runId: Long, event: StageCompleted): Future[Boolean] =
    sparkEventsRepo.stageCompleted(jobId, runId, event)

  override def taskStarted(jobId: Long, runId: Long, event: TaskStarted): Future[Boolean] =
    sparkEventsRepo.taskStarted(jobId, runId, event)

  override def taskCompleted(jobId: Long, runId: Long, event: TaskCompleted): Future[Boolean] =
    sparkEventsRepo.taskCompleted(jobId, runId, event)

  override def updateSparkMetric(jobId: Long,
                                 runId: Long,
                                 appAttemptId: String,
                                 avgTaskRuntime: Long,
                                 maxTaskRuntime: Long,
                                 totalExecutorRuntime: Long,
                                 totalJvmGCtime: Long): Future[Boolean] =
    sparkEventsRepo.updateSparkMetric(jobId, runId, appAttemptId, avgTaskRuntime, maxTaskRuntime, totalExecutorRuntime, totalJvmGCtime)

  override def getSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Either[Throwable, SparkAggMetrics]] = {
    sparkEventsRepo
      .getSparkAggMetrics(jobId, runId, appAttemptId)
      .map {
        case None =>
          Left(new RuntimeException(s"Spark application not found for job id : ${jobId} and runId : ${runId}"))
        case Some(value) => Right(value)
      }
      .recover {
        case e: Exception => Left(e)
      }
  }

  override def getSparkAppStatus(jobId: Long, runId: Long, appId: String): Future[Option[RunStatus]] = {
    sparkEventsRepo.getSparkAppStatus(jobId, runId, appId)
  }

  override def getSparkJobMetrics(jobId: Long,
                                  runId: Long,
                                  appAttemptId: String,
                                  startTime: Long,
                                  endTime: Long): Future[Either[Throwable, JobMetricResponse]] = {
    sparkEventsRepo.getSparkJobMetrics(jobId, runId, appAttemptId, startTime, endTime).map(Right(_)).recover {
      case e: Exception => Left(e)
    }
  }

  override def getSparkAggMetricsAll(jobId: Long, runId: Long): Future[Either[Throwable, SparkAppInfo]] = {
    sparkEventsRepo
      .getSparkAggMetricsAll(jobId, runId)
      .map {
        case None        => Left(new IllegalArgumentException("Metrics not collected for this Spark task"))
        case Some(value) => Right(value)

      }
      .recover {
        case e: Exception => Left(e)
      }
  }

  override def getSparkStageMetrics(jobId: Long,
                                    runId: Long,
                                    appAttemptId: String,
                                    sparkJobId: Int): Future[Either[Throwable, Seq[StageState]]] = {

    sparkEventsRepo.getSparkStageMetrics(jobId, runId, appAttemptId, sparkJobId).map(Right(_)).recover {
      case e: Exception => Left(e)
    }
  }

  override def publishAppState(runId: Long, metric: GxApplicationMetricReq, timezone: String): Future[Int] = {
    sparkEventsRepo.publishAppState(runId, metric, timezone)
  }

  def saveRuntimeMetrics(workspaceId: Long, runId: Long, request: AddMetricRequest): Future[Int] = {
    val keyValPairs = request.metrics.flatMap { emr =>
      emr.metricValues.map {
        case (key, d) => emr.metricName + "_" + key -> d
      }
    }
    sparkEventsRepo.saveRuntimeMetric(workspaceId,
                                      runId,
                                      request.timestamp,
                                      request.timezone,
                                      request.appId,
                                      request.executorId,
                                      keyValPairs)

  }

  override def addExecutorMetric(event: NewExecutor): Future[Int] = sparkEventsRepo.addExecutorMetric(event)

  override def getRunningSparkJob(jobId: Long, taskName: String): Future[Seq[SparkJobRunInstance]] =
    sparkEventsRepo.getRunningSparkJob(jobId, taskName)

  override def getRawSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[RawSparkMetric]] =
    sparkEventsRepo.getRawSparkAggMetrics(jobId, runId, appAttemptId)

}

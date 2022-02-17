package web.models.spark


import com.gigahex.commons.events.SparkConfProps
import com.gigahex.commons.models.{
  AggMetric,
  ErrorSource,
  ErrorType,
  JVMErrorStack,
  JVMException,
  RunStatus,
  SeverityLevel,
  StageAggMetrics,
  TaskTimeline,
  TraceElement
}
import com.gigahex.commons.models.RunStatus.RunStatus
import web.models.{JobRunInfo, LogSearchRequest, TimeFilter}
import play.api.libs.json.Json


case class SparkAggMetrics(appAttemptId: String,
                           timeTaken: String,
                           avgTaskRuntime: String,
                           maxTaskRuntime: String,
                           totalExecutorRuntime: String,
                           totalGCTime: String,
                           status: RunStatus,
                           startTime: Long,
                           endTime: Long)

case class SparkAppInfo(name: String, runId: Long, runSeqId: Long, appId: String, status: RunStatus, appAttempts: Seq[SparkAggMetrics])
case class AppCurrentState(appId: String, runId: Long, lastUpdated: Long, timezone: String)

case class SparkAppDetail(jobName: String, jobId: Long, runs: Seq[JobRunInfo])

case class RawSparkMetric(appAttemptId: String,
                          avgTaskRuntime: Long,
                          maxTaskRuntime: Long,
                          totalExecutorRuntime: Long,
                          totalGCTime: Long,
                          appRuntime: Option[Long],
                          status: RunStatus)
case class ExecutorMetricInstance(cores: Int,
                                  status: String,
                                  execId: String,
                                  rddBlocks: Int,
                                  maxStorageMemory: String,
                                  usedMemory: String,
                                  diskUsed: String,
                                  activeTasks: Long,
                                  failedTasks: Long,
                                  completedTasks: Long,
                                  taskRuntime: String,
                                  inputSize: String,
                                  shuffleRead: String,
                                  shuffleWrite: String)
case class ExecutorMetricResponse(totalCores: Int,
                                  totalActiveTasks: Long,
                                  totalFailedTasks: Long,
                                  totalCompletedTasks: Long,
                                  totalTaskTime: String,
                                  pctMemoryUsed: Double,
                                  totalMemoryStorage: String,
                                  totalMemoryUsed: String,
                                  totalDiskUsed: String,
                                  execMetrics: Seq[ExecutorMetricInstance])

case class ExecutionMetricSummary(workersCount: Int,
                                  executorsCount: Int,
                                  peakHeapMemoryOfExecutorUsed: Long,
                                  allocatedHeapMemoryOfExecutor: Long,
                                  storageMemoryUsed: Long,
                                  storageMemoryAllocated: Long,
                                  workers: Set[String])

case class WorkerRuntimeMetrics(id: String, cpuCores: Int, physicalMemory: Long, executorsMemory: Seq[ExecutorMemoryTimeserie])
case class ExecutorMemoryTimeserie(executorId: String, data: Seq[TotalMemoryTimeSerie])
case class TotalMemoryTimeSerie(ts: Long, memoryUsed: Long)

case class JobMetricResponse(totalJobs: Int, jobs: Seq[JobState])
case class JobState(jobId: Long,
                    name: String,
                    startedAt: String,
                    runtime: String,
                    currentStatus: String,
                    numStages: Int,
                    completedStages: Int,
                    startTime: Long,
                    endTime: Long)

case class StageState(stageId: Long,
                      stageAttemptId: Long,
                      name: String,
                      startedAt: String,
                      runtime: String,
                      currentStatus: String,
                      numTasks: Int,
                      completedTasks: Int,
                      shuffleRecordsRead: Long,
                      shuffleRecordsWritten: Long,
                      recordsRead: Long,
                      recordsWritten: Long,
                      shuffleInputSize: String,
                      shuffleWriteSize: String,
                      inputSize: String,
                      outputSize: String)

case class AppAttemptStats(appAttemptId: String, index: Int, jobs: Seq[JobState])
case class ApplicationState(jobId: Long, runId: Long, appId: String, appName: String, attempts: Seq[AppAttemptStats])
case class AppStateWSReq(jobId: String, runId: String, appAttemptId: String, sparkJobId: Option[String] = None)
case class ExecutorWSReq(jobId: String, runId: String, appAttemptId: String, execId: Option[String] = None)
case class SparkStageWSReq(jobId: String, runId: String, appAttemptId: String, sparkJobId: Int)
case class SparkLogWSReq(jobId: String, runId: String, tz: String, searchRequest: LogSearchRequest)
case class ListExecutorSummariesReq(jobId: String, runId: String, taskId: String)
case class AggregateMetric(avg: Double, peak: Double)
case class TasksGauge(active: Int, completed: Int, failed: Int, skipped: Int)
case class OverallRuntimeMetric(name: String,
                                appStatus: RunStatus,
                                sparkUser: String,
                                appId: String,
                                elapsedTime: String,
                                started: String,
                                config: SparkConfProps,
                                cpuAgg: AggregateMetric,
                                memAgg: AggregateMetric,
                                tasks: TasksGauge)
case class GetSparkMetric(projectId: Long, runId: Long, attemptId: String)
case class GetSparkTimeSerie(projectId: Long, runId: Long, attemptId: String, startTime: Option[Long], endTime: Option[Long])
case class GetSparkStage(projectId: Long, runId: Long, attemptId: String, stageId: Int, parentJobId: Int, stageAttemptId: Int)

case class JobRun(projectId: Long, runId: Long)
case class CompareRunRequests(runs: Seq[JobRun])

case class FloatMetricValue(offset: Long, timestamp: Long, metric: Float)
case class LongMetricValue(offset: Long, timestamp: Long, metric: Long)
case class IntMetricValue(offset: Long, timestamp: Long, metric: Int)
case class CPUTimeSerie(startTs: Long, endTs: Long, executorsCpuUsage: Seq[FloatMetricValue], systemsCpuUsage: Seq[FloatMetricValue])
case class MemoryTimeSerie(startTs: Long,
                           endTs: Long,
                           clusterMaxMem: Seq[LongMetricValue],
                           clusterMemUsage: Seq[LongMetricValue],
                           heap: Seq[LongMetricValue],
                           offHeap: Seq[LongMetricValue])
case class HeapMemoryTimeSerie(startTs: Long,
                               endTs: Long,
                               edenSpace: Seq[LongMetricValue],
                               oldGenSpace: Seq[LongMetricValue],
                               survivorSpace: Seq[LongMetricValue])
case class OffHeapMemoryTimeSerie(startTs: Long,
                                  endTs: Long,
                                  metaSpace: Seq[LongMetricValue],
                                  codeCache: Seq[LongMetricValue],
                                  compressedClass: Seq[LongMetricValue])
case class CpuCoreTimeSerie(startTs: Long, endTs: Long, cores: Seq[IntMetricValue], tasks: Seq[IntMetricValue])
case class SparkStageSummary(aggMetrics: StageAggMetrics, timeline: TaskTimeline)

case class MetricObservationResult(title: String, description: String, severityLevel: Int)
case class CompareResponse(jobId: Long,
                           runId: Long,
                           runSeq: Int,
                           runtime: String,
                           name: String,
                           status: String,
                           avgMemoryUsed: Double,
                           avgCPUUsed: Double,
                           counters: RuntimeCounters,
                           slowest: SlowestRuntime,
                           executors: ExecutorCounters,
                           config: SparkConfProps)
case class RuntimeCounters(jobs: Int, stages: Int)
case class JobInfoId(name: String, id: Long)

case class ExecutorCounters(count: Int, usedStorageMem: Long, maxStorageMem: Long, totalTasks: Int, inputRead: Long)
case class SlowestRuntime(jobId: Int, jobRuntime: String, stageId: Int, stageRuntime: String)

case class SparkStageCompleteStats(stageId: Int,
                                   name: String,
                                   stageAttemptId: Int,
                                   parentJobId: Int,
                                   summary: SparkStageSummary,
                                   bytesRead: Long,
                                   recordsRead: Long,
                                   bytesWritten: Long,
                                   recordsWritten: Long,
                                   numTasksCompleted: Int,
                                   runtime: Long,
                                   startTime: Long,
                                   endTime: Long,
                                   failedTasks: Int,
                                   failedReason: Option[String])

trait SparkMetricFmt {

  implicit val runStatusFmt              = Json.formatEnum(RunStatus)
  implicit val errSourceFmt              = Json.formatEnum(ErrorSource)
  implicit val sevLevelFmt               = Json.formatEnum(SeverityLevel)
  implicit val traceElementFmt           = Json.format[TraceElement]
  implicit val appError                  = Json.format[JVMException]
  implicit val errorType                 = Json.format[ErrorType]
  implicit val JVMErrorStackFmt          = Json.format[JVMErrorStack]
  implicit val sparkAggMetricsFmt        = Json.format[SparkAggMetrics]
  implicit val sparkAppInfoFmt           = Json.format[SparkAppInfo]
  implicit val stageStateFmt             = Json.format[StageState]
  implicit val jobStateFmt               = Json.format[JobState]
  implicit val appAttemptStatsFmt        = Json.format[AppAttemptStats]
  implicit val appStateFmt               = Json.format[ApplicationState]
  implicit val appStateWSReqFmt          = Json.format[AppStateWSReq]
  implicit val sparkStageWSReqFmt        = Json.format[SparkStageWSReq]
  implicit val sparkExecMetricReqFmt     = Json.format[ExecutorWSReq]
  implicit val getSparkMetricFmt         = Json.format[GetSparkMetric]
  implicit val sparkConfPropsFmt         = Json.format[SparkConfProps]
  implicit val aggMetricsFmt             = Json.format[AggregateMetric]
  implicit val tasksGaugeFmt             = Json.format[TasksGauge]
  implicit val sparkOverviewMetricFmt    = Json.format[OverallRuntimeMetric]
  implicit val metricValueFmt            = Json.format[FloatMetricValue]
  implicit val lngmetricValueFmt         = Json.format[LongMetricValue]
  implicit val CPUTimeSerieFmt           = Json.format[CPUTimeSerie]
  implicit val memoryTimeSerieFmt        = Json.format[MemoryTimeSerie]
  implicit val getSparkTimeSerieFmt      = Json.format[GetSparkTimeSerie]
  implicit val heapMemoryTimeSerieFmt    = Json.format[HeapMemoryTimeSerie]
  implicit val offHeapMemoryTimeSerieFmt = Json.format[OffHeapMemoryTimeSerie]
  implicit val intMetricValueFmt         = Json.format[IntMetricValue]
  implicit val cpuCoreTimeSerieFmt       = Json.format[CpuCoreTimeSerie]
  implicit val sparkstageFmt             = Json.format[GetSparkStage]
  implicit val aggMetricFmt              = Json.format[AggMetric]
  implicit val aggStageMetric            = Json.format[StageAggMetrics]
  implicit val stageTimelineFmt          = Json.format[TaskTimeline]
  implicit val sparkstageSummaryFmt      = Json.format[SparkStageSummary]
  implicit val jobMetricResponseFmt      = Json.format[JobMetricResponse]
  implicit val stageCompleteStatsFmt     = Json.format[SparkStageCompleteStats]
  implicit val timeFilterFmt             = Json.format[TimeFilter]
  implicit val metricObservationResult   = Json.format[MetricObservationResult]
  implicit val jobRunFmt                 = Json.format[JobRun]
  implicit val slowestRuntimeFmt         = Json.format[SlowestRuntime]
  implicit val compareRunRequestsFmt     = Json.format[CompareRunRequests]
  implicit val runtimeCountersFmt        = Json.format[RuntimeCounters]
  implicit val executorCountersFmt       = Json.format[ExecutorCounters]
  implicit val compareResponseFmt        = Json.format[CompareResponse]
  implicit val jobInfoIdFmt              = Json.format[JobInfoId]
  implicit val executionMetricSummaryFmt = Json.format[ExecutionMetricSummary]
  implicit val totalMemTSFmt             = Json.format[TotalMemoryTimeSerie]
  implicit val executorMemTSFmt          = Json.format[ExecutorMemoryTimeserie]
  implicit val workerRuntimeFmt          = Json.format[WorkerRuntimeMetrics]
}

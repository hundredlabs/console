package com.gigahex.commons.models

sealed trait SparkMachineState

case object AppNotStarted       extends SparkMachineState
case object AppIsRunning        extends SparkMachineState
case object AppWaitingForEvents extends SparkMachineState
case object AppCompleted        extends SparkMachineState

sealed trait SparkMachineData
case object Uninitialized extends SparkMachineData

case class ApplicationMetric(appId: String,
                             appName: String,
                             startTime: Long,
                             sparkUser: String,
                             status: String,
                             endTime: Option[Long],
                             attempts: Map[String, AppAttemptMetric])
    extends SparkMachineData {
  def getGxAppState(runId: Long): GxAppState = {

    val attemptMetrics = attempts.map {
      case (str, metric) =>
        GxAppAttemptMetric(
          metric.time,
          str,
          metric.endTime,
          metric.executorMetrics.map {
            case (s, m) =>
              GxExecutorMetric(
                s,
                m.usedStorage,
                m.rddBlocks,
                m.diskUsed,
                m.maxMem,
                m.activeTasks,
                m.failedTasks,
                m.completedTasks,
                m.totalTasks,
                m.totalTaskTime,
                m.inputBytes,
                m.shuffleRead,
                m.shuffleWrite
              )
          }.toSeq,
          metric.jobs.map {
            case (i, metric) =>
              GxJobMetricReq(
                i,
                metric.description,
                metric.startedTime,
                metric.endTime,
                metric.failedReason,
                metric.currentStatus,
                metric.numStages,
                metric.inProgress,
                metric.completedStages,
                metric.stages.values.toSeq
              )
          }.toSeq,
          avgTaskTime = metric.avgTaskTime,
          maxTaskTime = metric.maxTaskTime,
          totalExecutorRuntime = metric.totalExecutorRuntime,
          totalJvmGCtime = metric.totalJvmGCtime,
          totalTaskRuntime = metric.totalTaskRuntime
        )
    }
    GxAppState(runId, GxApplicationMetricReq(appId, appName, startTime, sparkUser, status, endTime, attemptMetrics.toSeq))
  }
}

case class AppAttemptMetric(time: Long,
                            endTime: Option[Long],
                            executorMetrics: Map[String, ExecutorMetric],
                            jobs: Map[Int, JobMetric],
                            avgTaskTime: Long = 0,
                            maxTaskTime: Long = 0,
                            totalTaskRuntime: Long = 0,
                            totalExecutorRuntime: Long = 0,
                            totalJvmGCtime: Long = 0)

case class NewExecutor(runId: Long, execId: String, host: String, cores: Int, time: Long)

//Below 2 classes are used for decoding the metrics from spark metric sink
case class ExecutorMetricMeta(metricName: String, metricValues: Map[String, Double])
case class AddMetricRequest(timestamp: Long, timezone: String, appId: String, executorId: String, metrics: List[ExecutorMetricMeta])

case class ExecutorMetric(diskUsed: Long = 0,
                          rddBlocks: Int = 0,
                          usedStorage: Long = 0,
                          maxMem: Long = 0,
                          activeTasks: Long = 0,
                          failedTasks: Long = 0,
                          completedTasks: Long = 0,
                          totalTasks: Long = 0,
                          totalTaskTime: Long = 0,
                          inputBytes: Long = 0,
                          shuffleRead: Long = 0,
                          shuffleWrite: Long = 0)
case class JobMetric(startedTime: Long,
                     description: String,
                     endTime: Option[Long],
                     currentStatus: String,
                     numStages: Int,
                     inProgress: Int,
                     failedReason: Option[String],
                     completedStages: Int,
                     stages: Map[Int, StageMetric])
case class AggMetric(min: Long, mean: Double, max: Long, total: Long)
case class TaskTimeline(schedulerDelay: Long,
                        executorComputeTime: Long,
                        shuffleReadTime: Long,
                        shuffleWriteTime: Long,
                        gettingResultTime: Long,
                        taskDeserializationTime: Long,
                        resultSerializationTime: Long)
case class StageAggMetrics(taskDuration: AggMetric,
                           gcTime: AggMetric,
                           shuffleReadRecords: AggMetric,
                           shuffleReadSize: AggMetric,
                           shuffleWriteSize: AggMetric,
                           shuffleWriteRecords: AggMetric,
                           inputReadRecords: AggMetric,
                           inputReadSize: AggMetric,
                           outputWriteRecords: AggMetric,
                           outputWriteSize: AggMetric)
case class StageMetric(stageId: Int,
                       name: String,
                       bytesRead: Long,
                       bytesWritten: Long,
                       recordsRead: Long,
                       recordsWritten: Long,
                       startedTime: Long,
                       endTime: Option[Long],
                       attemptId: Int,
                       currentStatus: String,
                       numTasks: Int,
                       inProgress: Int,
                       completedTasks: Int,
                       failedTasks: Int,
                       aggMetrics: StageAggMetrics,
                       timeline: TaskTimeline,
                       failedReason: Option[String])

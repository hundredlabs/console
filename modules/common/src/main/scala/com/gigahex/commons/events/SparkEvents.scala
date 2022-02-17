package com.gigahex.commons.events

import com.gigahex.commons.models.TaskTimeline


sealed trait SparkEvents {
  val time: Long
}
case class ExecutorAdded(execId: String, appAttemptId: String, host: String, cores: Int, time: Long) extends SparkEvents
case class SparkConfProps(sparkVer: String, master: String, executorMem: String, driverMem: String, executorCores: Int, memoryOverHead: String)
case class ApplicationStarted(appId: String, appAttemptId: String, appName: String, time: Long, sparkUser: String, config: SparkConfProps) extends SparkEvents

case class ApplicationEnded(time: Long, appId: String, appAttemptId: String, finalStatus: String) extends SparkEvents
case class BlockManagerAdded(execId: String, appAttemptId: String, host: String, maxMem: Long, time: Long) extends SparkEvents
case class BlockUpdated(appAttemptId: String,rddBlocks: Int, memUsed: Long, diskUsed: Long, execId: String, time: Long) extends SparkEvents
case class JobStarted(jobId: Int, description: String, time: Long, stageInfos: Seq[StageMeta], appId: String, appAttemptId: String) extends SparkEvents

case class JobEnded(jobId: Int, time: Long, succeeded: Boolean, appAttemptId: String, failedReason: Option[String]) extends SparkEvents

case class StageMeta(id: Int, name: String, attemptId: Int, numTasks: Int)
case class StageSubmitted(stageId: Int,
                          attemptId: Int,
                          appAttemptId: String,
                          time: Long,
                          numTasks: Int,
                          status: String,
                          parentIds: Seq[Int],
                          parentJobId: Int)
  extends SparkEvents

case class StageCompleted(stageId: Int,
                          attemptId: Int,
                          numTasks: Int,
                          status: String,
                          time: Long,
                          appAttemptId: String,
                          parentIds: Seq[Int],
                          parentJobId: Int,
                          failureReason: Option[String])
  extends SparkEvents

case class TaskStarted(taskId: Long, time: Long, stageId: Int, taskAttemptId: Int, appAttemptId: String, execId: String) extends SparkEvents

case class InputMetrics(bytesRead: Long, recordsRead: Long)
case class OutputMetrics(bytesWritten: Long, recordsWritten: Long)

case class TaskCompleted(taskId: Long,
                         taskAttemptId: Int,
                         executorId: String,
                         appId: String,
                         appAttemptId: String,
                         stageId: Int,
                         time: Long,
                         stageAttemptId: Int,
                         status: String,
                         failedReason: Option[String],
                         inputMetrics: InputMetrics,
                         outputMetrics: OutputMetrics,
                         timeline: TaskTimeline,
                         jvmGCtime: Long,
                         executorRuntime: Long,
                         taskRuntime: Long,
                         shuffleRecordsRead: Long,
                         shuffleRecordsWritten: Long,
                         shuffleBytesRead: Long,
                         shuffleBytesWritten: Long)
  extends SparkEvents

package com.gigahex.commons.models


case class GxAppState(runId: Long, metric: GxApplicationMetricReq)
case class DriverLogs( lines: Seq[String])
case class GxAppAttemptMetric(time: Long,
                              attemptId: String,
                              endTime: Option[Long],
                              executorMetrics: Seq[GxExecutorMetric],
                              jobs: Seq[GxJobMetricReq],
                              avgTaskTime: Long = 0,
                              maxTaskTime: Long = 0,
                              totalTaskRuntime: Long = 0,
                              totalExecutorRuntime: Long = 0,
                              totalJvmGCtime: Long = 0)

case class GxJobMetricReq(jobId: Int,
                          name: String,
                          startedTime: Long,
                          endTime: Option[Long],
                          failedReason: Option[String],
                          currentStatus: String,
                          numStages: Int,
                          inProgress: Int,
                          completedStages: Int,
                          stages: Seq[StageMetric])

case class GxExecutorMetric(execId: String,
                            usedStorage: Long,
                            rddBlocks: Int,
                            diskUsed: Long,
                            maxMem: Long,
                            activeTasks: Long,
                            failedTasks: Long,
                            completedTasks: Long,
                            totalTasks: Long,
                            totalTaskTime: Long,
                            inputBytes: Long,
                            shuffleRead: Long,
                            shuffleWrite: Long)

case class GxApplicationMetricReq(appId: String,
                                  appName: String,
                                  startTime: Long,
                                  sparkUser: String,
                                  status: String,
                                  endTime: Option[Long],
                                  attempts: Seq[GxAppAttemptMetric])

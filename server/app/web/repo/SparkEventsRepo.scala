package web.repo

import java.time.ZonedDateTime

import com.gigahex.commons.events.{
  ApplicationEnded,
  ApplicationStarted,
  JobEnded,
  JobStarted,
  StageCompleted,
  StageSubmitted,
  TaskCompleted,
  TaskStarted
}

import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{GxApplicationMetricReq, NewExecutor}
import web.models.{ExecutorMetricSummary, ExecutorRuntimeMetric, SparkJobRunInstance}
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
  OffHeapMemoryTimeSerie,
  OverallRuntimeMetric,
  RawSparkMetric,
  SparkAggMetrics,
  SparkAppInfo,
  SparkStageCompleteStats,
  SparkStageSummary,
  StageState,
  WorkerRuntimeMetrics
}

import scala.concurrent.{ExecutionContext, Future}

trait SparkEventsRepo {

  val groupInterval = 5
  val jvmRuntimeMetrics: Seq[String] = Seq(
    "jvm_gc_marksweep_count",
    "jvm_gc_marksweep_time",
    "jvm_gc_collection_count",
    "jvm_gc_collection_time",
    "jvm_direct_count",
    "jvm_direct_used",
    "jvm_direct_capacity",
    "jvm_heap_usage",
    "jvm_heap_init",
    "jvm_heap_committed",
    "jvm_heap_max",
    "jvm_heap_used",
    "jvm_mapped_count",
    "jvm_mapped_used",
    "jvm_mapped_capacity",
    "jvm_non_heap_max",
    "jvm_non_heap_committed",
    "jvm_non_heap_usage",
    "jvm_non_heap_used",
    "jvm_non_heap_init",
    "jvm_pools_code_cache_used",
    "jvm_pools_code_cache_committed",
    "jvm_pools_code_cache_usage",
    "jvm_pools_code_cache_init",
    "jvm_pools_code_cache_max",
    "jvm_pools_compressed_class_space_used",
    "jvm_pools_compressed_class_space_init",
    "jvm_pools_compressed_class_space_usage",
    "jvm_pools_compressed_class_space_max",
    "jvm_pools_compressed_class_space_committed",
    "jvm_pools_metaspace_used",
    "jvm_pools_metaspace_committed",
    "jvm_pools_metaspace_usage",
    "jvm_pools_metaspace_init",
    "jvm_pools_metaspace_max",
    "jvm_pools_gc_eden_space_init",
    "jvm_pools_gc_eden_space_committed",
    "jvm_pools_gc_eden_space_max",
    "jvm_pools_gc_eden_space_used",
    "jvm_pools_gc_eden_space_usage",
    "jvm_pools_gc_old_gen_init",
    "jvm_pools_gc_old_gen_committed",
    "jvm_pools_gc_old_gen_max",
    "jvm_pools_gc_old_gen_used",
    "jvm_pools_gc_old_gen_usage",
    "jvm_pools_gc_survivor_space_max",
    "jvm_pools_gc_survivor_space_usage",
    "jvm_pools_gc_survivor_space_committed",
    "jvm_pools_gc_survivor_space_used",
    "jvm_pools_gc_survivor_space_init",
    "jvm_total_init",
    "jvm_total_committed",
    "jvm_total_max",
    "jvm_total_used",
    "jvm_load_average",
    "jvm_file_descriptors_open",
    "jvm_file_descriptors_max",
    "jvm_mem_committed",
    "jvm_mem_size",
    "jvm_mem_free",
    "jvm_cpu_num_available",
    "jvm_cpu_process_usage",
    "jvm_cpu_usage",
    "jvm_swap_size",
    "jvm_swap_free"
  )
  def getSparkAppStatus(jobId: Long, runId: Long, appId: String): Future[Option[RunStatus]] = ???

  def getRunningSparkJob(jobId: Long, taskName: String): Future[Seq[SparkJobRunInstance]] = ???

  def publishAppState(runId: Long, metric: GxApplicationMetricReq, timezone: String): Future[Int]

  def addExecutorMetric(newExecutor: NewExecutor): Future[Int]

  def getStageMetricDetail(runId: Long,
                           attemptId: String,
                           stageId: Int,
                           parentJobId: Int,
                           stageAttemptId: Int): Future[Option[SparkStageSummary]]

  def getCPUTimeSerie(runId: Long,
                      attemptId: String,
                      startTime: Option[ZonedDateTime],
                      endTime: Option[ZonedDateTime]): Future[Option[CPUTimeSerie]]

  def getMemTimeSerie(runId: Long,
                      attemptId: String,
                      startTime: Option[ZonedDateTime],
                      endTime: Option[ZonedDateTime]): Future[Option[MemoryTimeSerie]]

  def getHeapMemTimeSerie(runId: Long,
                          attemptId: String,
                          startTime: Option[ZonedDateTime],
                          endTime: Option[ZonedDateTime]): Future[Option[HeapMemoryTimeSerie]]

  def getOffHeapMemTimeSerie(runId: Long,
                             attemptId: String,
                             startTime: Option[ZonedDateTime],
                             endTime: Option[ZonedDateTime]): Future[Option[OffHeapMemoryTimeSerie]]

  def getCoresUtilization(runId: Long,
                          attemptId: String,
                          startTime: Option[ZonedDateTime],
                          endTime: Option[ZonedDateTime]): Future[Option[CpuCoreTimeSerie]]

  def listSparkAppByStatus(status: RunStatus): Future[Seq[AppCurrentState]]

  def getSparkOvervallMetric(jobId: Long, runId: Long, attempt: String): Future[Option[OverallRuntimeMetric]]

  def getAllStages(jobId: Long, runId: Long, attempt: String): Future[Seq[SparkStageCompleteStats]]

  val blockingEC: ExecutionContext

  def updateSparkMetric(jobId: Long,
                        runId: Long,
                        appAttemptId: String,
                        avgTaskRuntime: Long,
                        maxTaskRuntime: Long,
                        totalExecutorRuntime: Long,
                        totalJvmGCtime: Long): Future[Boolean]

  def appStarted(runId: Long, event: ApplicationStarted, timezone: String): Future[Boolean]

  def appEnded(jobId: Long, runId: Long, event: ApplicationEnded): Future[Boolean]

  def jobStarted(jobId: Long, runId: Long, event: JobStarted): Future[Boolean]

  def jobEnded(jobId: Long, runId: Long, event: JobEnded): Future[Boolean]

  def stageSubmitted(jobId: Long, runId: Long, event: StageSubmitted): Future[Boolean]

  def stageCompleted(jobId: Long, runId: Long, event: StageCompleted): Future[Boolean]

  def taskStarted(jobId: Long, runId: Long, event: TaskStarted): Future[Boolean]

  def taskCompleted(jobId: Long, runId: Long, event: TaskCompleted): Future[Boolean]

  def getSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[SparkAggMetrics]]

  def getSparkAggMetricsAll(jobId: Long, runId: Long): Future[Option[SparkAppInfo]]

  def getSparkJobMetrics(jobId: Long, runId: Long, appAttemptId: String, startTime: Long, endTime: Long): Future[JobMetricResponse]

  def getSparkStageMetrics(jobId: Long, runId: Long, appAttemptId: String, sparkJobId: Int): Future[Seq[StageState]]

  def getRawSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[RawSparkMetric]]

  def listExecutorSummaries(jobId: Long, runId: Long): Future[Seq[ExecutorMetricSummary]]

  def getExecutorRuntimeMetrics(jobId: Long, runId: Long, executorId: String): Future[Option[ExecutorRuntimeMetric]]

  def getExecutionMetrics(runId: Long, attemptId: String): Future[Option[ExecutionMetricSummary]]

  def getWorkersRuntimeMetrics(runId: Long, attemptId: String, workerId: String): Future[Option[WorkerRuntimeMetrics]]

  def saveRuntimeMetric(jobId: Long,
                        runId: Long,
                        timestampSec: Long,
                        tz: String,
                        appId: String,
                        executorId: String,
                        metricWithVals: List[(String, Double)]): Future[Int]

  def getExecutorMetric(runId: Long, attemptId: String): Future[Option[ExecutorMetricResponse]]

  def fetchRunComparison(runs: JobRun): Future[Option[CompareResponse]]

}

//class SparkEventsRepoImpl(val blockingEC: ExecutionContext) extends SparkEventsRepo {
//
//  implicit val ec = blockingEC
//
//  override def getSparkAppStatus(jobId: Long, runId: Long, appId: String): Future[Option[RunStatus]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        val xs = sql"select app_status from events_spark_app where job_run_id = ${runId} AND app_id = ${appId}"
//          .map(r => RunStatus.withName(r.string("app_status")))
//          .list()
//          .apply()
//        if (xs.size > 0) {
//          Some(xs.head)
//        } else
//          None
//      }
//    }
//  }
//
//  override def getRunningSparkJob(jobId: Long, taskName: String): Future[Seq[SparkJobRunInstance]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""select sa.app_name, sa.app_attempt_id, sa.job_run_id from events_spark_app as sa
//              INNER JOIN job_history as jh ON sa.job_run_id = jh.id
//              INNER JOIN task_history as th ON th.job_run_id = jh.id WHERE jh.job_id = ${jobId} AND th.name = ${taskName}
//              AND th.task_type = 'spark-submit'
//           """
//          .map(
//            r =>
//              SparkJobRunInstance(
//                jobId = jobId,
//                taskName = taskName,
//                sparkApp = r.string("app_name"),
//                jobRunId = r.long("job_run_id"),
//                appAttemptId = r.string("app_attempt_id")
//            ))
//          .list()
//          .apply()
//      }
//    }
//  }
//
//  override def publishAppState(jobId: Long, runId: Long, metric: GxApplicationMetricReq, timezone: String): Future[Int] = Future {
//
//    blocking {
//      DB localTx { implicit session =>
//        //update the executor metrics
//
//        metric.attempts.foreach { attemptMetric =>
//          val appStartTs = new Timestamp(attemptMetric.time)
//          val appEndTime = attemptMetric.endTime.map(t => new Timestamp(t))
//          val now        = ZonedDateTime.now(ZoneId.of(timezone))
//          sql"""INSERT INTO events_spark_app(job_run_id,  app_id, app_name, spark_user, app_attempt_id, app_status, start_time, last_updated)
//             VALUES(${runId},  ${metric.appId}, ${metric.appName}, ${metric.sparkUser}, ${attemptMetric.attemptId}, ${RunStatus.Running.toString}, ${appStartTs}, ${appStartTs})
//             ON DUPLICATE KEY UPDATE
//            app_status = ${metric.status},
//            last_updated=${now},
//            end_time = ${appEndTime.getOrElse(null)} ,
//            total_executor_runtime = ${attemptMetric.totalExecutorRuntime} ,
//            total_gc_time = ${attemptMetric.totalJvmGCtime} ,
//            avg_task_runtime = ${attemptMetric.avgTaskTime} ,
//            max_task_runtime = ${attemptMetric.maxTaskTime}
//             """
//            .update()
//            .apply()
//
//          attemptMetric.executorMetrics.foreach { em =>
//            sql"""INSERT INTO events_spark_executors( job_run_id, app_attempt_id, executor_id, status)
//             VALUES(${runId}, ${attemptMetric.attemptId}, ${em.execId}, 'active')
//             ON DUPLICATE KEY UPDATE
//             max_storage_memory = ${em.maxMem},
//             app_attempt_id = ${attemptMetric.attemptId},
//             rdd_blocks = ${em.rddBlocks},
//             disk_used = ${em.diskUsed},
//             used_storage = ${em.usedStorage},
//             active_tasks = ${em.activeTasks},
//             failed_tasks = ${em.failedTasks},
//             completed_tasks = ${em.completedTasks},
//             total_tasks = ${em.totalTasks},
//             task_time = ${em.totalTaskTime},
//             input_bytes = ${em.inputBytes},
//             shuffle_read = ${em.shuffleRead},
//             shuffle_write = ${em.shuffleWrite}
//           """.update()
//              .apply()
//          }
//
//          //ZonedDateTime.ofInstant(new Instant(100L), ZonedDateTime.now().getZone)
//          attemptMetric.jobs.foreach { job =>
//            val endTime = job.endTime.map(x => ZonedDateTime.ofInstant(Instant.ofEpochMilli(x), ZonedDateTime.now().getZone))
//            val startTs = new Timestamp(job.startedTime)
//            sql"""INSERT INTO events_spark_jobs(job_run_id, name, job_id, app_id, app_attempt_id, job_status, num_stages,
//                   num_stages_completed, start_time, end_time)
//             VALUES(${runId},${job.name}, ${job.jobId}, ${metric.appId}, ${attemptMetric.attemptId}, 'running',
//               ${job.numStages},${job.completedStages}, ${startTs}, ${endTime.getOrElse(null)})
//             ON DUPLICATE KEY UPDATE
//            job_status = ${job.currentStatus} ,
//            end_time = ${endTime.getOrElse(null)} ,
//            num_stages_completed = ${job.completedStages} ,
//            failed_reason = ${job.failedReason.getOrElse(null)}
//             """
//              .update()
//              .apply()
//
//            job.stages.foreach { stage =>
//              val stageStartTs = ZonedDateTime.ofInstant(Instant.ofEpochMilli(stage.startedTime), ZonedDateTime.now().getZone)
//              val stageEndTs   = stage.endTime.map(x => ZonedDateTime.ofInstant(Instant.ofEpochMilli(x), ZonedDateTime.now().getZone))
//              sql"""INSERT INTO events_spark_stage(job_run_id, parent_job_id, stage_id, stage_attempt_id, num_tasks,
//                   num_tasks_completed, start_time, end_time, stage_status, app_attempt_id, bytes_read, bytes_written, records_read, records_written, name)
//             VALUES(${runId}, ${job.jobId}, ${stage.stageId},${stage.attemptId},
//                ${stage.numTasks},${stage.completedTasks}, ${stageStartTs},${stageEndTs.getOrElse(null)}, ${stage.currentStatus},
//                 ${attemptMetric.attemptId}, ${stage.bytesRead}, ${stage.bytesWritten},
//                ${stage.recordsRead}, ${stage.recordsWritten}, ${stage.name})
//             ON DUPLICATE KEY UPDATE
//            stage_status = ${stage.currentStatus} ,
//            num_tasks_completed = ${stage.completedTasks} ,
//            end_time = ${stageEndTs.getOrElse(null)} ,
//            bytes_read = ${stage.bytesRead},
//            bytes_written = ${stage.bytesWritten},
//            records_read = ${stage.recordsRead},
//            records_written = ${stage.recordsWritten},
//            failed_reason = ${stage.failedReason.getOrElse(null)}
//             """.update()
//                .apply()
//
//            }
//
//          }
//        }
//
//        1
//      }
//    }
//  }
//
//  override def listSparkAppByStatus(status: RunStatus): Future[Seq[AppCurrentState]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""select app_id, job_run_id, last_updated, user_timezone from events_spark_app
//               where app_status = ${status.toString}
//           """
//          .map { r =>
//            val lastTs = r.dateTime("last_updated").toInstant.getEpochSecond
//            AppCurrentState(appId = r.string("app_id"), runId = r.long("job_run_id"), lastUpdated = lastTs, timezone = r.string("user_timezone"))
//          }
//          .list()
//          .apply()
//      }
//    }
//  }
//
//  override def getSparkOvervallMetric(jobId: Long, runId: Long, attempt: String): Future[Option[OverallRuntimeMetric]] = ???
//
//  override def addExecutorMetric(newExecutor: NewExecutor): Future[Int] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        val startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(newExecutor.time), ZonedDateTime.now().getZone)
//        sql"""insert into events_spark_executors( job_run_id, executor_id, status, cores, address, dt_added)
//             values(${newExecutor.runId}, ${newExecutor.execId}, 'active', ${newExecutor.cores}, ${newExecutor.host}, ${startTime})
//           """.update().apply()
//      }
//    }
//  }
//
//  override def getCPUTimeSerie(runId: Long, attemptId: String, jobId: Option[Long], stageId: Option[Long]): Future[Option[CPUTimeSerie]] = ???
//
//  override def getMemTimeSerie(runId: Long, attemptId: String, jobId: Option[Long], stageId: Option[Long]): Future[Option[MemoryTimeSerie]] = ???
//
//  override def getHeapMemTimeSerie(runId: Long, attemptId: String, jobId: Option[Long], stageId: Option[Long]): Future[Option[HeapMemoryTimeSerie]] = ???
//
//  override def getOffHeapMemTimeSerie(runId: Long, attemptId: String, jobId: Option[Long], stageId: Option[Long]): Future[Option[OffHeapMemoryTimeSerie]] = ???
//
//  override def getCoresUtilization(runId: Long, attemptId: String, jobId: Option[Long], stageId: Option[Long]): Future[Option[CpuCoreTimeSerie]] = ???
//
//  override def updateSparkMetric(jobId: Long,
//                                 runId: Long,
//                                 appAttemptId: String,
//                                 avgTaskRuntime: Long,
//                                 maxTaskRuntime: Long,
//                                 totalExecutorRuntime: Long,
//                                 totalJvmGCtime: Long): Future[Boolean] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""update events_spark_app set avg_task_runtime = ${avgTaskRuntime}, max_task_runtime = ${maxTaskRuntime},
//              total_executor_runtime = ${totalExecutorRuntime}, total_gc_time=${totalJvmGCtime} where
//              job_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}
//             """
//          .update()
//          .apply() == 1
//      }
//    }
//  }
//
//  override def appStarted(jobId: Long, runId: Long, event: ApplicationStarted, timezone: String): Future[Boolean] =
//    Future {
//      blocking {
//        DB localTx { implicit session =>
//          val startTs = new Timestamp(event.time)
//          val insertedRows =
//            sql"""insert into events_spark_app(job_run_id, app_id, app_name, spark_user, app_attempt_id, app_status, start_time, last_updated, user_timezone)
//             values(${runId}, ${event.appId}, ${event.appName}, ${event.sparkUser}, ${event.appAttemptId}, 'running', ${startTs}, ${startTs}, ${timezone})
//             """
//              .update()
//              .apply()
//
//          val updatedRows =
//            sql"""update job_history set status = 'running' , last_updated = ${new Timestamp(event.time)} where job_id = $jobId AND id = $runId
//             """
//              .update()
//              .apply()
//
//          insertedRows + updatedRows == 2
//        }
//      }
//    }
//
//  override def appEnded(jobId: Long, runId: Long, event: ApplicationEnded): Future[Boolean] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""update events_spark_app set app_status = ${event.finalStatus}, end_time = ${ZonedDateTime.now()} where
//              job_run_id = ${runId}
//             """
//          .update()
//          .apply() == 1
//      }
//    }
//  }
//
//  override def jobStarted(jobId: Long, runId: Long, event: JobStarted): Future[Boolean] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        var insertCounts = 0
//        insertCounts =
//          sql"""insert into events_spark_jobs(job_run_id, job_id, app_id, app_attempt_id, job_status,num_stages, num_stages_completed, start_time)
//             values(${runId},  ${event.jobId}, ${event.appId}, ${event.appAttemptId}, 'running', ${event.stageInfos.size}, ${0}, ${ZonedDateTime
//            .now()} )
//             """
//            .update()
//            .apply()
//
//        event.stageInfos.foreach { s =>
//          insertCounts = insertCounts + sql"""insert into events_spark_stage(job_run_id, parent_job_id, stage_id,
//                 stage_attempt_id, stage_status,num_tasks, num_tasks_completed, start_time, app_attempt_id)
//             values(${runId}, ${event.jobId}, ${s.id}, ${s.attemptId}, 'waiting', ${s.numTasks}, ${0}, ${ZonedDateTime
//            .now()}, ${event.appAttemptId} )
//             """
//            .update()
//            .apply()
//        }
//
//        insertCounts == 1 + event.stageInfos.size
//      }
//    }
//  }
//
//  override def jobEnded(jobId: Long, runId: Long, event: JobEnded): Future[Boolean] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        val status = if (event.succeeded) "passed" else "failed"
//        sql"""update events_spark_jobs set job_status = ${status}, end_time = ${ZonedDateTime
//          .now()}, failed_reason = ${event.failedReason.getOrElse("")}  where
//              job_run_id = ${runId} AND job_id = ${event.jobId}
//             """
//          .update()
//          .apply() == 1
//      }
//    }
//  }
//
//  override def stageSubmitted(jobId: Long, runId: Long, event: StageSubmitted): Future[Boolean] =
//    Future {
//      blocking {
//        DB localTx { implicit session =>
//          sql"""update events_spark_stage set stage_status = ${event.status} where
//              job_run_id = ${runId} AND parent_job_id = ${event.parentJobId} AND stage_id = ${event.stageId}
//              AND stage_attempt_id = ${event.attemptId}
//             """
//            .update()
//            .apply() == 1
//
//        }
//      }
//    }
//
//  override def stageCompleted(jobId: Long, runId: Long, event: StageCompleted): Future[Boolean] =
//    Future {
//      blocking {
//        DB localTx { implicit session =>
//          if (event.status.equals("succeeded")) {
//            sql"""update events_spark_jobs set num_stages_completed = num_stages_completed + 1  where
//              job_run_id = ${runId}  AND job_id = ${event.parentJobId} AND app_attempt_id = ${event.appAttemptId}
//             """
//              .update()
//              .apply()
//          }
//          sql"""update events_spark_stage set stage_status = ${event.status}, end_time = ${ZonedDateTime
//            .now()}, failed_reason = ${event.failureReason.getOrElse("")}  where
//              job_run_id = ${runId} AND parent_job_id = ${event.parentJobId} AND stage_id = ${event.stageId}
//              AND stage_attempt_id = ${event.attemptId}
//             """
//            .update()
//            .apply() == 1
//        }
//      }
//    }
//
//  override def taskStarted(jobId: Long, runId: Long, event: TaskStarted): Future[Boolean] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""insert into events_spark_task(task_id, job_run_id, stage_id, task_attempt_id, app_attempt_id, task_status, start_time)
//             values(${event.taskId}, ${runId}, ${event.stageId}, ${event.taskAttemptId},${event.appAttemptId}, 'running', ${ZonedDateTime
//          .now()} )
//             """
//          .update()
//          .apply() == 1
//
//      }
//    }
//  }
//
//  override def taskCompleted(jobId: Long, runId: Long, event: TaskCompleted): Future[Boolean] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        if (event.status.equals("SUCCESS")) {
//
//          sql"""update events_spark_stage set num_tasks_completed = num_tasks_completed + 1  where
//              job_run_id = ${runId} AND stage_id = ${event.stageId} AND stage_attempt_id = ${event.stageAttemptId}
//               AND app_attempt_id = ${event.appAttemptId}
//             """
//            .update()
//            .apply()
//        }
//        sql"""update events_spark_task set task_status = ${event.status}, end_time = ${ZonedDateTime
//          .now()}, failed_reason = ${event.failedReason.getOrElse("")}  where
//              job_run_id = ${runId} AND app_attempt_id = ${event.appAttemptId} AND task_id = ${event.taskId} AND stage_id = ${event.stageId}
//             """
//          .update()
//          .apply() == 1
//
//      }
//    }
//  }
//
//  override def getSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[SparkAggMetrics]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""select total_executor_runtime,app_status, app_id,user_timezone, start_time, end_time, total_gc_time, avg_task_runtime, max_task_runtime from
//             events_spark_app where job_run_id = ${runId} AND app_attempt_id = ${appAttemptId}
//           """
//          .map { r =>
//            val startTs   = r.dateTime("start_time").toInstant.toEpochMilli
//            val appStatus = RunStatus.withName(r.string("app_status"))
//            val startTime = r.dateTime("start_time").toEpochSecond
//            val endTime = r.dateTimeOpt("end_time") match {
//              case None => ZonedDateTime.now().toEpochSecond
//              case Some(value) => value.toEpochSecond
//            }
//            SparkAggMetrics(
//              appAttemptId = appAttemptId,
//              timeTaken = r.dateTimeOpt("end_time") match {
//                case None if appStatus == RunStatus.TimeLimitExceeded => "Time limit exceeded"
//                case None =>
//                  val now = ZonedDateTime.now(ZoneId.of(r.string("user_timezone"))).toInstant.toEpochMilli
//                  DateUtil.formatIntervalMillis(now - startTs)
//                case Some(value) =>
//                  val endTs = value.toInstant.toEpochMilli
//                  DateUtil.formatIntervalMillis(endTs - startTs)
//              },
//              avgTaskRuntime = DateUtil.formatIntervalMillis(r.long("avg_task_runtime")),
//              maxTaskRuntime = DateUtil.formatIntervalMillis(r.long("max_task_runtime")),
//              totalExecutorRuntime = DateUtil.formatIntervalMillis(r.long("total_executor_runtime")),
//              totalGCTime = DateUtil.formatIntervalMillis(r.long("total_gc_time")),
//              status = appStatus,
//              startTime,
//              endTime
//            )
//          }
//          .single()
//          .apply()
//      }
//    }
//  }
//
//  override def getRawSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[RawSparkMetric]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""select total_executor_runtime,app_status,start_time, end_time, total_gc_time, avg_task_runtime, max_task_runtime from
//             events_spark_app where job_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}
//           """
//          .map { r =>
//            val startTime  = r.dateTime("start_time")
//            val endTime    = r.dateTimeOpt("end_time")
//            val appRuntime = endTime.map(zd => zd.toInstant.toEpochMilli - startTime.toInstant.toEpochMilli)
//            RawSparkMetric(
//              appAttemptId = appAttemptId,
//              avgTaskRuntime = r.long("avg_task_runtime"),
//              maxTaskRuntime = r.long("max_task_runtime"),
//              totalExecutorRuntime = r.long("total_executor_runtime"),
//              totalGCTime = r.long("total_gc_time"),
//              appRuntime = appRuntime,
//              status = RunStatus.withName(r.string("app_status"))
//            )
//          }
//          .single()
//          .apply()
//      }
//    }
//  }
//
//  override def listExecutorSummaries(jobId: Long, runId: Long): Future[Seq[ExecutorMetricSummary]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""SELECT jvm_total_max, executor_id, AVG(jvm_cpu_process_usage) as avg_cpu_usage, AVG(jvm_total_used) as avg_mem_used
//             from metrics_spark_runtime metric
//             where job_run_id = ${runId} GROUP BY executor_id, jvm_total_max limit 10;
//           """
//          .map { r =>
//            ExecutorMetricSummary(
//              executorId = r.string("executor_id"),
//              maxMemory = r.double("jvm_total_max").toLong,
//              avgUsedMemory = r.double("avg_mem_used").toLong,
//              avgProcessCpuUsage = r.double("avg_cpu_usage")
//            )
//          }
//          .list()
//          .apply()
//      }
//    }
//  }
//
//  private[repo] def extractRuntimeMetrics(x: WrappedResultSet): InstantMetric = {
//
//    val cpuMetric = CPUMetric(x.double("jvm_cpu_process_usage"), x.double("jvm_cpu_usage"))
//    val overallUsage = JVMMemOverallUsage(
//      swapSize = x.long("jvm_swap_size"),
//      swapFree = x.long("jvm_swap_free"),
//      freeMemory = x.long("jvm_mem_free"),
//      totalUsed = x.long("jvm_total_used")
//    )
//    val gcTime = JVMGCTime(
//      marksweepCount = x.long("jvm_gc_marksweep_count"),
//      marksweepTime = x.long("jvm_gc_marksweep_time"),
//      scavengeCount = x.long("jvm_gc_collection_count"),
//      scavengeTime = x.long("jvm_gc_collection_time")
//    )
//    val heapUsage = JVMHeapUsage(
//      init = x.long("jvm_heap_init"),
//      used = x.long("jvm_heap_used"),
//      max = x.long("jvm_heap_max"),
//      fracUsage = x.double("jvm_heap_usage"),
//      edenSpaceUsed = x.long("jvm_pools_gc_eden_space_max"),
//      oldGenUsed = x.long("jvm_pools_gc_old_gen_used"),
//      survivorSpaceUsed = x.long("jvm_pools_gc_survivor_space_used")
//    )
//    val nonHeapUsage = JVMNonHeapUsage(
//      used = x.long("jvm_non_heap_used"),
//      max = x.long("jvm_non_heap_max"),
//      fracUsage = x.double("jvm_non_heap_usage"),
//      init = x.long("jvm_non_heap_init"),
//      codeCacheUsed = x.long("jvm_pools_code_cache_used"),
//      compressedClassSpaceUsed = x.long("jvm_pools_compressed_class_space_used"),
//      metaspaceUsed = x.long("jvm_pools_metaspace_used")
//    )
//    InstantMetric(x.dateTime("ts").toEpochSecond, 0f, heapUsage, nonHeapUsage, gcTime, overallUsage, cpuMetric)
//
//  }
//
//  override def getExecutorRuntimeMetrics(jobId: Long, runId: Long, executorId: String): Future[Option[ExecutorRuntimeMetric]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        var cpus: Long         = 0
//        var cpuAllocated       = 0
//        var address            = ""
//        var status             = ""
//        var memSize: Long      = 0L
//        var memAllocated: Long = 0L
//        val runtimeMetrics =
//          sql"""select jvm_gc_marksweep_count, jvm_gc_marksweep_time, jvm_gc_collection_count, jvm_gc_collection_time, ts,
//            jvm_heap_usage, jvm_heap_init, jvm_heap_max, jvm_heap_used, jvm_pools_gc_eden_space_max, jvm_pools_gc_old_gen_used, jvm_pools_gc_survivor_space_used,
//            jvm_non_heap_used, jvm_non_heap_usage, jvm_non_heap_max, jvm_non_heap_init, jvm_pools_code_cache_used, jvm_pools_compressed_class_space_used, jvm_pools_metaspace_used,
//            jvm_cpu_usage, jvm_cpu_process_usage, jvm_swap_size, jvm_swap_free, jvm_mem_free, jvm_total_used, jvm_cpu_num_available, ex.status, ex.address,
//            ex.cores, jvm_mem_size, jvm_total_max FROM metrics_spark_runtime me
//            INNER JOIN events_spark_executors ex ON me.executor_id = ex.executor_id AND me.job_run_id = ex.job_run_id
//             WHERE me.executor_id = ${executorId} AND me.job_run_id = ${runId}
//             """
//            .map { r =>
//              cpus = r.long("jvm_cpu_num_available")
//              memSize = r.long("jvm_mem_size")
//              memAllocated = r.long("jvm_total_max")
//              cpuAllocated = r.int("cores")
//              address = r.string("address")
//              status = r.string("status")
//              extractRuntimeMetrics(r)
//            }
//            .list()
//            .apply()
//
//        if (runtimeMetrics.isEmpty) {
//          None
//        } else {
//          val duplicateByTime = runtimeMetrics
//            .groupBy(_.timestamp)
//            .mapValues(_.last)
//            .map(x => x._2.copy(timestamp = x._2.timestamp * 1000))
//            .toSeq
//            .sortWith((x, y) => x.timestamp < y.timestamp)
//          val extractedMetric =
//            ExecutorRuntimeMetric(executorId, status, address, cpuAllocated, memSize, memAllocated, cpus, duplicateByTime)
//
//          Some(extractedMetric)
//        }
//      }
//    }
//  }
//
//  override def getExecutorMetric(runId: Long, attemptId: String): Future[Option[ExecutorMetricResponse]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        var totalCores          = 0
//        var totalActiveTasks    = 0L
//        var totalFailedTasks    = 0L
//        var totalCompletedTasks = 0L
//        var totalTaskTime       = 0L
//        var totalMemoryUsed     = 0L
//        var totalMaxMemory      = 0L
//        var totalDiskUsed       = 0L
//
//        val execMetrics = sql"""select cores, status, executor_id, rdd_blocks, max_storage_memory, used_storage, disk_used, active_tasks,
//             failed_tasks, completed_tasks, task_time, input_bytes, shuffle_read, shuffle_write from
//             events_spark_executors where job_run_id = ${runId} AND app_attempt_id = ${attemptId}
//           """
//          .map { r =>
//            totalCores += r.int("cores")
//            totalActiveTasks += r.long("active_tasks")
//            totalFailedTasks += r.long("failed_tasks")
//            totalCompletedTasks += r.long("completed_tasks")
//            totalTaskTime += r.long("task_time")
//            totalMemoryUsed += r.longOpt("used_storage").getOrElse(0L)
//            totalMaxMemory += r.longOpt("max_storage_memory").getOrElse(1L)
//            totalDiskUsed += r.long("disk_used")
//
//            ExecutorMetricInstance(
//              cores = r.int("cores"),
//              status = r.string("status"),
//              execId = r.string("executor_id"),
//              rddBlocks = r.int("rdd_blocks"),
//              maxStorageMemory = MetricConverter.toReadableSize(r.long("max_storage_memory")),
//              usedMemory = MetricConverter.toReadableSize(r.long("used_storage")),
//              diskUsed = MetricConverter.toReadableSize(r.long("disk_used")),
//              activeTasks = r.long("active_tasks"),
//              failedTasks = r.long("failed_tasks"),
//              completedTasks = r.long("completed_tasks"),
//              taskRuntime = DateUtil.formatIntervalMillis(r.long("task_time")),
//              inputSize = MetricConverter.toReadableSize(r.long("input_bytes")),
//              shuffleRead = MetricConverter.toReadableSize(r.long("shuffle_read")),
//              shuffleWrite = MetricConverter.toReadableSize(r.long("shuffle_write"))
//            )
//          }
//          .list()
//          .apply()
//        if (execMetrics.size == 0) {
//          None
//        } else {
//          val memoryPct = if (totalMaxMemory > 0) {
//            BigDecimal(totalMemoryUsed / totalMaxMemory.toDouble).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble * 100
//          } else {
//            0D
//          }
//          val m = ExecutorMetricResponse(
//            totalCores,
//            totalActiveTasks,
//            totalFailedTasks,
//            totalCompletedTasks,
//            DateUtil.formatIntervalMillis(totalTaskTime),
//            memoryPct,
//            MetricConverter.toReadableSize(totalMaxMemory),
//            MetricConverter.toReadableSize(totalMemoryUsed),
//            MetricConverter.toReadableSize(totalDiskUsed),
//            execMetrics
//          )
//          Some(m)
//        }
//      }
//    }
//  }
//
//  override def getSparkAggMetricsAll(jobId: Long, runId: Long): Future[Option[SparkAppInfo]] =
//    Future {
//      blocking {
//        DB localTx { implicit session =>
//          val sparkInfoMap =
//            sql"""select app_name,app_id, end_time, start_time, user_timezone, app_attempt_id,app_status,
//                  total_executor_runtime, total_gc_time, avg_task_runtime, max_task_runtime from
//             events_spark_app where job_run_id = ${runId}
//           """.map { r =>
//                val startTs = r.dateTime("start_time").toInstant.toEpochMilli
//              val startTime = r.dateTime("start_time").toEpochSecond
//              val endTime = r.dateTimeOpt("end_time") match {
//                case None => ZonedDateTime.now().toEpochSecond
//                case Some(value) => value.toEpochSecond
//              }
//                (r.string("app_name"), r.string("app_id")) -> SparkAggMetrics(
//                  appAttemptId = r.string("app_attempt_id"),
//                  timeTaken = r.dateTimeOpt("end_time") match {
//                    case None if RunStatus.withName(r.string("app_status")) == RunStatus.TimeLimitExceeded => "Unknown"
//                    case None =>
//                      val now = ZonedDateTime.now(ZoneId.of(r.string("user_timezone"))).toInstant.toEpochMilli
//                      DateUtil.formatIntervalMillis(now - startTs)
//                    case Some(value) =>
//                      val endTs = value.toInstant.toEpochMilli
//                      DateUtil.formatIntervalMillis(endTs - startTs)
//                  },
//                  avgTaskRuntime = r.longOpt("avg_task_runtime") match {
//                    case None        => "--"
//                    case Some(value) => DateUtil.formatIntervalMillis(value)
//                  },
//                  maxTaskRuntime = r.longOpt("max_task_runtime") match {
//                    case None        => "--"
//                    case Some(value) => DateUtil.formatIntervalMillis(value)
//                  },
//                  totalExecutorRuntime = r.longOpt("total_executor_runtime") match {
//                    case None        => "--"
//                    case Some(value) => DateUtil.formatIntervalMillis(value)
//                  },
//                  totalGCTime = r.longOpt("total_gc_time") match {
//                    case None        => "--"
//                    case Some(value) => DateUtil.formatIntervalMillis(value)
//                  },
//                  status = RunStatus.withName(r.string("app_status")),
//                  startTime,
//                  endTime
//                )
//              }
//              .list()
//              .apply()
//              .groupBy(_._1)
//          if (sparkInfoMap.size == 0) {
//            None
//          } else {
//            val info = sparkInfoMap.map {
//              case ((name, appId), metrics) =>
//                val allStatus = metrics.map(_._2.status)
//                val finalStatus = {
//                  if (allStatus.size == 1) {
//                    allStatus.head
//                  } else if (allStatus.contains(RunStatus.Succeeded)) {
//                    RunStatus.Succeeded
//                  } else if (allStatus.contains(RunStatus.Running)) {
//                    RunStatus.Running
//                  } else {
//                    RunStatus.Failed
//                  }
//                }
//                SparkAppInfo(name, appId, finalStatus, metrics.map(_._2))
//            }.head
//            Some(info)
//          }
//
//        }
//      }
//    }
//
//  override def getSparkJobMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Seq[JobState]] =
//    Future {
//      blocking {
//        DB localTx { implicit session =>
//          sql"""select job_id,name, job_status, num_stages, num_stages_completed, failed_reason, start_time, end_time from
//             events_spark_jobs where job_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}
//           """
//            .map { r =>
//              val runtime = r.dateTimeOpt("end_time") match {
//                case None        => "--"
//                case Some(value) => DateUtil.timeElapsed(r.dateTime("start_time"), Some(value))
//              }
//              JobState(
//                jobId = r.long("job_id"),
//                name = r.string("name"),
//                startedAt = DateUtil.timeElapsed(r.dateTime("start_time"), None) + " ago",
//                runtime = runtime,
//                currentStatus = r.string("job_status"),
//                numStages = r.int("num_stages"),
//                completedStages = r.int("num_stages_completed")
//              )
//            }
//            .list()
//            .apply()
//        }
//      }
//    }
//
//  override def getSparkStageMetrics(jobId: Long, runId: Long, appAttemptId: String, sparkJobId: Int): Future[Seq[StageState]] = Future {
//    blocking {
//      DB localTx { implicit session =>
//        sql"""select stage_id, start_time, end_time, stage_status, num_tasks, num_tasks_completed, records_read, records_written, bytes_read, bytes_written from
//             events_spark_stage where job_run_id = ${runId} AND app_attempt_id = ${appAttemptId} and parent_job_id = ${sparkJobId}
//           """
//          .map { r =>
//            val runtime = r.dateTimeOpt("end_time") match {
//              case None        => "--"
//              case Some(value) => DateUtil.timeElapsed(r.dateTime("start_time"), Some(value))
//            }
//            StageState(
//              stageId = r.long("stage_id"),
//              startedAt = DateUtil.timeElapsed(r.dateTime("start_time"), None) + " ago",
//              runtime = runtime,
//              currentStatus = r.string("stage_status"),
//              numTasks = r.int("num_tasks"),
//              completedTasks = r.int("num_tasks_completed"),
//              recordsRead = r.long("records_read"),
//              recordsWritten = r.long("records_written"),
//              inputSize = MetricConverter.toReadableSize(r.long("bytes_read")),
//              outputSize = MetricConverter.toReadableSize(r.long("bytes_written"))
//            )
//          }
//          .list()
//          .apply()
//      }
//    }
//  }
//
//  override def saveRuntimeMetric(jobId: Long,
//                                 runId: Long,
//                                 timestampSec: Long,
//                                 tz: String,
//                                 appId: String,
//                                 executorId: String,
//                                 metricWithVals: List[(String, Double)]): Future[Int] = {
//
//    Future {
//      blocking {
//        DB localTx { implicit session =>
//          //ZonedDateTime.ofInstant(Instant.ofEpochMilli(x), ZonedDateTime.now().getZone)
//          val eventTime      = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampSec), ZoneId.of(tz))
//          val tsFormat       = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS")
//          val timestampStr   = tsFormat.format(new Date(eventTime.toInstant.toEpochMilli))
//          val metricColNames = metricWithVals.map(_._1).mkString(", ")
//          val metricColVals  = metricWithVals.map(_._2).mkString(", ")
//          SQL(
//            s"insert into metrics_spark_runtime(job_run_id,  app_id, executor_id, ts, ${metricColNames}) VALUES(${runId}, '${appId}', '${executorId}', '${timestampStr}', ${metricColVals})")
//            .update()
//            .apply()
//        }
//
//      }
//    }
//  }
//}

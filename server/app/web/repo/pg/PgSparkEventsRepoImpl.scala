package web.repo.pg

import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.Date

import com.gigahex.commons.events.{
  ApplicationEnded,
  ApplicationStarted,
  JobEnded,
  JobStarted,
  SparkConfProps,
  StageCompleted,
  StageSubmitted,
  TaskCompleted,
  TaskStarted
}
import com.gigahex.commons.models.{AggMetric, GxApplicationMetricReq, NewExecutor, RunStatus, StageAggMetrics, TaskTimeline}
import com.gigahex.commons.models.RunStatus.RunStatus
import web.models.{
  CPUMetric,
  ExecutorMetricSummary,
  ExecutorRuntimeMetric,
  InstantMetric,
  JVMGCTime,
  JVMHeapUsage,
  JVMMemOverallUsage,
  JVMNonHeapUsage,
  SparkJobRunInstance
}
import web.models.spark.{
  AggregateMetric,
  AppCurrentState,
  CPUTimeSerie,
  CompareResponse,
  CpuCoreTimeSerie,
  ExecutionMetricSummary,
  ExecutorCounters,
  ExecutorMemoryTimeserie,
  ExecutorMetricInstance,
  ExecutorMetricResponse,
  FloatMetricValue,
  HeapMemoryTimeSerie,
  IntMetricValue,
  JobMetricResponse,
  JobRun,
  JobState,
  LongMetricValue,
  MemoryTimeSerie,
  OffHeapMemoryTimeSerie,
  OverallRuntimeMetric,
  RawSparkMetric,
  RuntimeCounters,
  SlowestRuntime,
  SparkAggMetrics,
  SparkAppInfo,
  SparkStageCompleteStats,
  SparkStageSummary,
  StageState,
  TasksGauge,
  TotalMemoryTimeSerie,
  WorkerRuntimeMetrics
}
import web.repo.SparkEventsRepo
import web.utils.{DateUtil, MetricConverter}
import scalikejdbc.{DB, SQL, WrappedResultSet}

import scala.concurrent.{ExecutionContext, Future, blocking}
import scalikejdbc._

class PgSparkEventsRepoImpl(val blockingEC: ExecutionContext) extends SparkEventsRepo {

  implicit val ec = blockingEC

  override def getSparkAppStatus(jobId: Long, runId: Long, appId: String): Future[Option[RunStatus]] = Future {
    blocking {
      DB localTx { implicit session =>
        val xs = sql"select app_status from events_spark_app where job_run_id = ${runId} AND app_id = ${appId}"
          .map(r => RunStatus.withName(r.string("app_status")))
          .list()
          .apply()
        if (xs.size > 0) {
          Some(xs.head)
        } else
          None
      }
    }
  }

  override def getRunningSparkJob(jobId: Long, taskName: String): Future[Seq[SparkJobRunInstance]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select sa.app_name, sa.app_attempt_id, sa.job_run_id from events_spark_app as sa
              INNER JOIN job_history as jh ON sa.job_run_id = jh.id
              INNER JOIN task_history as th ON th.job_run_id = jh.id WHERE jh.job_id = ${jobId} AND th.name = ${taskName}
              AND th.task_type = 'spark-submit'
           """
          .map(
            r =>
              SparkJobRunInstance(
                jobId = jobId,
                taskName = taskName,
                sparkApp = r.string("app_name"),
                jobRunId = r.long("job_run_id"),
                appAttemptId = r.string("app_attempt_id")
            ))
          .list()
          .apply()
      }
    }
  }

  override def publishAppState(runId: Long, metric: GxApplicationMetricReq, timezone: String): Future[Int] = Future {

    blocking {
      DB localTx { implicit session =>
        //update the executor metrics

        metric.attempts.foreach { attemptMetric =>
          val appStartTs = new Timestamp(attemptMetric.time)
          val appEndTime = attemptMetric.endTime.map(t => new Timestamp(t))
          val now        = ZonedDateTime.now(ZoneId.of(timezone))
          sql"""INSERT INTO events_spark_app(dep_run_id,  app_id, app_name, spark_user, app_attempt_id, app_status, start_time, last_updated)
             VALUES(${runId},  ${metric.appId}, ${metric.appName}, ${metric.sparkUser}, ${attemptMetric.attemptId}, ${RunStatus.Running.toString}, ${appStartTs}, ${appStartTs})
             ON CONFLICT (dep_run_id, app_id, app_attempt_id)
             DO
             UPDATE SET app_status = ${metric.status},
            last_updated=${now},
            end_time = ${appEndTime.getOrElse(null)} ,
            total_executor_runtime = ${attemptMetric.totalExecutorRuntime} ,
            total_gc_time = ${attemptMetric.totalJvmGCtime} ,
            avg_task_runtime = ${attemptMetric.avgTaskTime} ,
            max_task_runtime = ${attemptMetric.maxTaskTime}
             """
            .update()
            .apply()

          attemptMetric.executorMetrics.foreach { em =>
            sql"""INSERT INTO events_spark_executors( dep_run_id, app_attempt_id, executor_id, status)
             VALUES(${runId}, ${attemptMetric.attemptId}, ${em.execId}, 'active')
             ON CONFLICT (dep_run_id, executor_id)
             DO
             UPDATE SET
             max_storage_memory = ${em.maxMem},
             app_attempt_id = ${attemptMetric.attemptId},
             rdd_blocks = ${em.rddBlocks},
             disk_used = ${em.diskUsed},
             used_storage = ${em.usedStorage},
             active_tasks = ${em.activeTasks},
             failed_tasks = ${em.failedTasks},
             completed_tasks = ${em.completedTasks},
             total_tasks = ${em.totalTasks},
             task_time = ${em.totalTaskTime},
             input_bytes = ${em.inputBytes},
             shuffle_read = ${em.shuffleRead},
             shuffle_write = ${em.shuffleWrite}
           """.update()
              .apply()
          }

          //update the task distribution
          attemptMetric.executorMetrics.foreach { em =>
            sql"""INSERT INTO events_spark_task_distribution
                  ( dep_run_id, app_attempt_id, executor_id, status, active_tasks, failed_tasks, completed_tasks, total_tasks, ts)
             VALUES(${runId}, ${attemptMetric.attemptId}, ${em.execId}, 'active',
             ${em.activeTasks}, ${em.failedTasks}, ${em.completedTasks}, ${em.totalTasks}, ${now})
           """.update()
              .apply()
          }

          //ZonedDateTime.ofInstant(new Instant(100L), ZonedDateTime.now().getZone)
          attemptMetric.jobs.foreach { job =>
            val endTime = job.endTime.map(t => new Timestamp(t))
            val startTs = new Timestamp(job.startedTime)
            sql"""INSERT INTO events_spark_jobs(dep_run_id, name, job_id, app_id, app_attempt_id, job_status, num_stages,
                   num_stages_completed, start_time, end_time)
             VALUES(${runId},${job.name}, ${job.jobId}, ${metric.appId}, ${attemptMetric.attemptId}, ${job.currentStatus},
               ${job.numStages},${job.completedStages}, ${startTs}, ${endTime.getOrElse(null)})
             ON CONFLICT (job_id, dep_run_id)
             DO
             UPDATE SET
            job_status = ${job.currentStatus} ,
            end_time = ${endTime.getOrElse(null)} ,
            num_stages_completed = ${job.completedStages} ,
            failed_reason = ${job.failedReason.getOrElse(null)}
             """
              .update()
              .apply()

            if (endTime.isDefined) {
              sql"""update events_spark_stage"""
            }

            job.stages.foreach { stage =>
              val stageStartTs = DateUtil.getTime(stage.startedTime)
              val stageEndTs   = stage.endTime.map(t => DateUtil.getTime(t))
              val stageStatus = if (stageEndTs.isDefined) {
                stage.currentStatus
              } else if (job.currentStatus.equalsIgnoreCase(RunStatus.Succeeded.toString) || job.currentStatus.equalsIgnoreCase(
                           RunStatus.Failed.toString)) {
                RunStatus.Skipped.toString
              } else {
                job.currentStatus
              }
              sql"""INSERT INTO events_spark_stage(dep_run_id, parent_job_id, stage_id, stage_attempt_id, num_tasks,
                   num_tasks_completed, num_tasks_failed, start_time, end_time, stage_status, app_attempt_id, bytes_read, bytes_written,
                    records_read, records_written, name,
                    scheduler_delay,executor_compute_time, shuffle_read_time, shuffle_write_time, getting_result_time, task_deserialization_time,
                     result_deserialization_time,
                     input_read_records_min, input_read_records_max, input_read_records_mean, input_read_records_total,
                     input_read_size_min, input_read_size_max, input_read_size_mean, input_read_size_total,
                     output_write_size_min, output_write_size_max, output_write_size_mean, output_write_size_total,
                     output_write_records_min, output_write_records_max, output_write_records_mean, output_write_records_total,
                     task_duration_min, task_duration_max, task_duration_mean,task_duration_total,
                     gc_time_min, gc_time_max, gc_time_mean,gc_time_total,
                     shuffle_read_records_min, shuffle_read_records_max,shuffle_read_records_mean,shuffle_read_records_total,
                      shuffle_read_size_min, shuffle_read_size_max, shuffle_read_size_mean,shuffle_read_size_total,
                      shuffle_write_size_min, shuffle_write_size_max, shuffle_write_size_mean,shuffle_write_size_total,
                      shuffle_write_records_min, shuffle_write_records_max, shuffle_write_records_mean, shuffle_write_records_total)
             VALUES(${runId}, ${job.jobId}, ${stage.stageId},${stage.attemptId},
                ${stage.numTasks},${stage.completedTasks}, ${stage.failedTasks}, ${stageStartTs},${stageEndTs
                .getOrElse(null)}, ${stageStatus},
                 ${attemptMetric.attemptId}, ${stage.bytesRead}, ${stage.bytesWritten},
                ${stage.recordsRead}, ${stage.recordsWritten}, ${stage.name}, ${stage.timeline.schedulerDelay}, ${stage.timeline.executorComputeTime},
                ${stage.timeline.shuffleReadTime}, ${stage.timeline.shuffleWriteTime},
                 ${stage.timeline.gettingResultTime}, ${stage.timeline.taskDeserializationTime},${stage.timeline.resultSerializationTime},
                 ${stage.aggMetrics.inputReadRecords.min},${stage.aggMetrics.inputReadRecords.max},${stage.aggMetrics.inputReadRecords.mean},${stage.aggMetrics.inputReadRecords.total},
                 ${stage.aggMetrics.inputReadSize.min},${stage.aggMetrics.inputReadSize.max},${stage.aggMetrics.inputReadSize.mean},${stage.aggMetrics.inputReadSize.total},
                 ${stage.aggMetrics.outputWriteSize.min},${stage.aggMetrics.outputWriteSize.max},${stage.aggMetrics.outputWriteSize.mean},${stage.aggMetrics.outputWriteSize.total},
                 ${stage.aggMetrics.outputWriteRecords.min},${stage.aggMetrics.outputWriteRecords.max},${stage.aggMetrics.outputWriteRecords.mean},${stage.aggMetrics.outputWriteRecords.total},
                 ${stage.aggMetrics.taskDuration.min}, ${stage.aggMetrics.taskDuration.max}, ${stage.aggMetrics.taskDuration.mean}, ${stage.aggMetrics.taskDuration.total},
                 ${stage.aggMetrics.gcTime.min}, ${stage.aggMetrics.gcTime.max}, ${stage.aggMetrics.gcTime.mean}, ${stage.aggMetrics.gcTime.total},
                 ${stage.aggMetrics.shuffleReadRecords.min}, ${stage.aggMetrics.shuffleReadRecords.max}, ${stage.aggMetrics.shuffleReadRecords.mean}, ${stage.aggMetrics.shuffleReadRecords.total},
                 ${stage.aggMetrics.shuffleReadSize.min}, ${stage.aggMetrics.shuffleReadSize.max}, ${stage.aggMetrics.shuffleReadSize.mean}, ${stage.aggMetrics.shuffleReadSize.total},
                 ${stage.aggMetrics.shuffleWriteSize.min},${stage.aggMetrics.shuffleWriteSize.max},${stage.aggMetrics.shuffleWriteSize.mean},${stage.aggMetrics.shuffleWriteSize.total},
                 ${stage.aggMetrics.shuffleWriteRecords.min},${stage.aggMetrics.shuffleWriteRecords.max},${stage.aggMetrics.shuffleWriteRecords.mean},${stage.aggMetrics.shuffleWriteRecords.total}
                )
             ON CONFLICT (parent_job_id, dep_run_id, stage_id, stage_attempt_id)
             DO
             UPDATE SET
             start_time = ${DateUtil.getTime(stage.startedTime)},
            stage_status = ${stageStatus} ,
            scheduler_delay= ${stage.timeline.schedulerDelay},
            executor_compute_time = ${stage.timeline.executorComputeTime},
            shuffle_read_time = ${stage.timeline.shuffleReadTime},
            shuffle_write_time = ${stage.timeline.shuffleWriteTime},
            getting_result_time = ${stage.timeline.gettingResultTime},
            task_deserialization_time = ${stage.timeline.taskDeserializationTime},
            result_deserialization_time = ${stage.timeline.resultSerializationTime},
            task_duration_min = ${stage.aggMetrics.taskDuration.min}, task_duration_max = ${stage.aggMetrics.taskDuration.max},
            task_duration_mean = ${stage.aggMetrics.taskDuration.mean},task_duration_total = ${stage.aggMetrics.taskDuration.total},
            gc_time_min = ${stage.aggMetrics.gcTime.min}, gc_time_max = ${stage.aggMetrics.gcTime.max},
            gc_time_mean = ${stage.aggMetrics.gcTime.mean},gc_time_total = ${stage.aggMetrics.gcTime.total},

            input_read_records_min = ${stage.aggMetrics.inputReadRecords.min}, input_read_records_max = ${stage.aggMetrics.inputReadRecords.max},
            input_read_records_mean = ${stage.aggMetrics.inputReadRecords.mean},input_read_records_total = ${stage.aggMetrics.inputReadRecords.total},

            input_read_size_min = ${stage.aggMetrics.inputReadSize.min}, input_read_size_max = ${stage.aggMetrics.inputReadSize.max},
            input_read_size_mean = ${stage.aggMetrics.inputReadSize.mean},input_read_size_total = ${stage.aggMetrics.inputReadSize.total},

            output_write_size_min = ${stage.aggMetrics.outputWriteSize.min}, output_write_size_max = ${stage.aggMetrics.outputWriteSize.max},
            output_write_size_mean = ${stage.aggMetrics.outputWriteSize.mean},output_write_size_total = ${stage.aggMetrics.outputWriteSize.total},

            output_write_records_min = ${stage.aggMetrics.outputWriteRecords.min}, output_write_records_max = ${stage.aggMetrics.outputWriteRecords.max},
            output_write_records_mean = ${stage.aggMetrics.outputWriteRecords.mean},output_write_records_total = ${stage.aggMetrics.outputWriteRecords.total},

            shuffle_read_records_min = ${stage.aggMetrics.shuffleReadRecords.min}, shuffle_read_records_max = ${stage.aggMetrics.shuffleReadRecords.max},
            shuffle_read_records_mean = ${stage.aggMetrics.shuffleReadRecords.mean},shuffle_read_records_total = ${stage.aggMetrics.shuffleReadRecords.total},
            shuffle_read_size_min = ${stage.aggMetrics.shuffleReadSize.min}, shuffle_read_size_max= ${stage.aggMetrics.shuffleReadSize.max},
            shuffle_read_size_mean = ${stage.aggMetrics.shuffleReadSize.mean},shuffle_read_size_total = ${stage.aggMetrics.shuffleReadSize.total},
            shuffle_write_size_min = ${stage.aggMetrics.shuffleWriteSize.min}, shuffle_write_size_max = ${stage.aggMetrics.shuffleWriteSize.max},
            shuffle_write_size_mean = ${stage.aggMetrics.shuffleWriteSize.mean},shuffle_write_size_total = ${stage.aggMetrics.shuffleWriteSize.total},
            shuffle_write_records_min = ${stage.aggMetrics.shuffleWriteRecords.min}, shuffle_write_records_max = ${stage.aggMetrics.shuffleWriteRecords.max},
            shuffle_write_records_mean = ${stage.aggMetrics.shuffleWriteRecords.mean}, shuffle_write_records_total = ${stage.aggMetrics.shuffleWriteRecords.total},
            num_tasks_completed = ${stage.completedTasks} ,
            num_tasks_failed = ${stage.failedTasks},
            end_time = ${stageEndTs.getOrElse(null)},
            bytes_read = ${stage.bytesRead},
            bytes_written = ${stage.bytesWritten},
            records_read = ${stage.recordsRead},
            records_written = ${stage.recordsWritten},
            failed_reason = ${stage.failedReason.getOrElse(null)}
             """.update()
                .apply()

            }

          }
        }
        1
      }
    }
  }

  override def listSparkAppByStatus(status: RunStatus): Future[Seq[AppCurrentState]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select app_id, dep_run_id, last_updated, user_timezone from events_spark_app
               where app_status = ${status.toString}
           """
          .map { r =>
            val lastTs = r.dateTime("last_updated").toInstant.getEpochSecond
            AppCurrentState(appId = r.string("app_id"),
                            runId = r.long("dep_run_id"),
                            lastUpdated = lastTs,
                            timezone = r.string("user_timezone"))
          }
          .list()
          .apply()
      }
    }
  }
  case class RuntimeConfigPart()
  override def getSparkOvervallMetric(jobId: Long, runId: Long, attempt: String): Future[Option[OverallRuntimeMetric]] = Future {
    blocking {
      DB readOnly { implicit session =>
        var starTime = ZonedDateTime.now()
        var endTime  = ZonedDateTime.now()
        val result =
          sql"""select start_time, end_time, spark_user, app_name as name, app_id, spark_version, app_status, master, executor_mem,
                 driver_mem, executor_cores, memory_overhead
             FROM events_spark_app as ESA INNER JOIN deployment_history as DH ON ESA.dep_run_id = DH.id
              WHERE dep_run_id = ${runId} AND app_attempt_id = ${attempt}"""
            .map { r =>
              val elapsedTime = RunStatus.withNameOpt(r.string("app_status")) match {

                case com.gigahex.commons.models.RunStatus.TimeLimitExceeded => "unknown"
                case _                                                      => DateUtil.timeElapsedInFraction(r.zonedDateTime("start_time"), r.zonedDateTimeOpt("end_time"))
              }
              starTime = r.dateTime("start_time")
              endTime = r.dateTimeOpt("end_time").getOrElse(ZonedDateTime.now())
              OverallRuntimeMetric(
                name = r.string("name"),
                appStatus = RunStatus.withNameOpt(r.string("app_status")),
                sparkUser = r.string("spark_user"),
                appId = r.string("app_id"),
                elapsedTime = elapsedTime,
                started = DateUtil.timeElapsed(r.zonedDateTime("start_time"), None) + " ago",
                config = SparkConfProps(r.string("spark_version"),
                                        r.string("master"),
                                        r.string("executor_mem"),
                                        r.string("driver_mem"),
                                        r.int("executor_cores"),
                                        r.string("memory_overhead")),
                cpuAgg = AggregateMetric(0, 0),
                memAgg = AggregateMetric(0, 0),
                tasks = TasksGauge(0, 0, 0, 0)
              )
            }
            .single()
            .apply()

        result.flatMap { mayBe =>
          sql"""select sum(active_tasks) as total_active, sum(failed_tasks) as total_failed, sum(completed_tasks) as total_completed
                FROM events_spark_executors where dep_run_id = ${runId} AND app_attempt_id = ${attempt}"""
            .map { r =>
              mayBe.copy(
                tasks = mayBe.tasks.copy(active = r.intOpt("total_active").getOrElse(0),
                                         completed = r.intOpt("total_completed").getOrElse(0),
                                         failed = r.intOpt("total_failed").getOrElse(0)))
            }
            .single()
            .apply()
            .flatMap { ovm =>
              sql"""SELECT MAX(agg.cpu_used) as peak_cpu_used, AVG(agg.cpu_used) as avg_cpu_used,
                     max(agg.mem_used)/AVG(agg.cluster_mem) as peak_mem_used, AVG(agg.mem_used)/AVG(agg.cluster_mem) as avg_mem_used,
                      AVG(agg.cluster_mem) as cluster_mem, AVG(agg.mem_used) as avg_mem_used_val FROM
(SELECT AVG(cluster.executor_process_used) as cpu_used, SUM(cluster.mem_used) as mem_used, SUM(cluster.net_mem) as cluster_mem, cluster.with_time FROM(
SELECT tmp.with_time, tmp.address, SUM(tmp.jvm_cpu_process_usage) as executor_process_used, SUM(tmp.net_mem) as mem_used, AVG(max_mem) as net_mem FROM
(SELECT  ese.address,msr.executor_id,
		AVG(jvm_total_used) as net_mem, AVG(jvm_mem_size) as max_mem,
                  AVG(jvm_cpu_process_usage) as jvm_cpu_process_usage,
                  time_bucket_gapfill('5 seconds', ts) as with_time
                  FROM metrics_spark_runtime as msr
                  LEFT JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
                  WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${starTime} AND ${endTime}
                  GROUP BY msr.executor_id, address,with_time order by with_time) as tmp
                  GROUP BY address, with_time
		having AVG(tmp.jvm_cpu_process_usage) IS NOT NULL) as cluster
		GROUP BY cluster.with_time
		) as agg""".map { r =>
                  ovm.copy(
                    cpuAgg =
                      AggregateMetric(avg = r.doubleOpt("avg_cpu_used").getOrElse(0D), peak = r.doubleOpt("peak_cpu_used").getOrElse(0D)),
                    memAgg =
                      AggregateMetric(avg = r.doubleOpt("avg_mem_used").getOrElse(0D), peak = r.doubleOpt("peak_mem_used").getOrElse(0D))
                  )
                }
                .single()
                .apply()
            }
        }
      }
    }
  }

  override def getAllStages(jobId: Long, runId: Long, attempt: String): Future[Seq[SparkStageCompleteStats]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select stage_id, name, stage_attempt_id,num_tasks_failed, bytes_read, parent_job_id, records_read, bytes_written, records_written, start_time, end_time,num_tasks_completed,
               failed_reason, scheduler_delay, executor_compute_time, shuffle_read_time, shuffle_write_time, getting_result_time,
             task_deserialization_time, result_deserialization_time,
             input_read_records_min, input_read_records_max, input_read_records_mean, input_read_records_total,
                     input_read_size_min, input_read_size_max, input_read_size_mean, input_read_size_total,
                     output_write_size_min, output_write_size_max, output_write_size_mean, output_write_size_total,
                     output_write_records_min, output_write_records_max, output_write_records_mean, output_write_records_total,
                     task_duration_min, task_duration_mean, task_duration_max, task_duration_total,
             gc_time_min, gc_time_mean, gc_time_max, gc_time_total, shuffle_read_records_min, shuffle_read_records_mean, shuffle_read_records_max,
             shuffle_read_records_total, shuffle_read_size_min, shuffle_read_size_mean, shuffle_read_size_max, shuffle_read_size_total,
             shuffle_write_size_min, shuffle_write_size_mean, shuffle_write_size_max, shuffle_write_size_total,
             shuffle_write_records_min, shuffle_write_records_mean, shuffle_write_records_max, shuffle_write_records_total
             FROM events_spark_stage
             WHERE dep_run_id = ${runId} AND app_attempt_id = ${attempt} and (stage_status = ${RunStatus.Succeeded.toString}
             OR stage_status = ${RunStatus.Failed.toString})"""
          .map { r =>
            val stageId        = r.int("stage_id")
            val stageAttemptId = r.int("stage_attempt_id")
            val startTime      = r.dateTime("start_time").toInstant.toEpochMilli
            val endTime = r.dateTimeOpt("end_time") match {
              case None    => ZonedDateTime.now().toInstant.toEpochMilli
              case Some(v) => v.toInstant.toEpochMilli
            }

            val t = TaskTimeline(
              schedulerDelay = r.long("scheduler_delay"),
              executorComputeTime = r.long("executor_compute_time"),
              shuffleReadTime = r.long("shuffle_read_time"),
              shuffleWriteTime = r.long("shuffle_write_time"),
              gettingResultTime = r.long("getting_result_time"),
              taskDeserializationTime = r.long("task_deserialization_time"),
              resultSerializationTime = r.long("result_deserialization_time")
            )

            val am = StageAggMetrics(
              taskDuration = AggMetric(r.long("task_duration_min"),
                                       r.double("task_duration_mean"),
                                       r.long("task_duration_max"),
                                       r.long("task_duration_total")),
              gcTime = AggMetric(r.long("gc_time_min"), r.double("gc_time_mean"), r.long("gc_time_max"), r.long("gc_time_total")),
              shuffleReadRecords = AggMetric(r.long("shuffle_read_records_min"),
                                             r.double("shuffle_read_records_mean"),
                                             r.long("shuffle_read_records_max"),
                                             r.long("shuffle_read_records_total")),
              shuffleReadSize = AggMetric(r.long("shuffle_read_size_min"),
                                          r.double("shuffle_read_size_mean"),
                                          r.long("shuffle_read_size_max"),
                                          r.long("shuffle_read_size_total")),
              shuffleWriteSize = AggMetric(r.long("shuffle_write_size_min"),
                                           r.double("shuffle_write_size_mean"),
                                           r.long("shuffle_write_size_max"),
                                           r.long("shuffle_write_size_total")),
              shuffleWriteRecords = AggMetric(r.long("shuffle_write_records_min"),
                                              r.double("shuffle_write_records_mean"),
                                              r.long("shuffle_write_records_max"),
                                              r.long("shuffle_write_records_total")),
              inputReadRecords = AggMetric(r.long("input_read_records_min"),
                                           r.double("input_read_records_mean"),
                                           r.long("input_read_records_max"),
                                           r.long("input_read_records_total")),
              inputReadSize = AggMetric(r.long("input_read_size_min"),
                                        r.double("input_read_size_mean"),
                                        r.long("input_read_size_max"),
                                        r.long("input_read_size_total")),
              outputWriteRecords = AggMetric(r.long("output_write_records_min"),
                                             r.double("output_write_records_mean"),
                                             r.long("output_write_records_max"),
                                             r.long("output_write_records_total")),
              outputWriteSize = AggMetric(r.long("output_write_size_min"),
                                          r.double("output_write_size_mean"),
                                          r.long("output_write_size_max"),
                                          r.long("output_write_size_total"))
            )
            val stageSummary = SparkStageSummary(am, t)
            SparkStageCompleteStats(
              stageId = stageId,
              parentJobId = r.int("parent_job_id"),
              name = r.string("name"),
              stageAttemptId = stageAttemptId,
              bytesRead = r.long("bytes_read"),
              bytesWritten = r.long("bytes_written"),
              recordsRead = r.long("records_read"),
              recordsWritten = r.long("records_written"),
              numTasksCompleted = r.int("num_tasks_completed"),
              runtime = endTime - startTime,
              startTime = startTime,
              endTime = endTime,
              summary = stageSummary,
              failedTasks = r.intOpt("num_tasks_failed").getOrElse(0),
              failedReason = r.stringOpt("failed_reason")
            )
          }
          .list()
          .apply()
          .sortWith((x, y) => x.stageId < y.stageId)
      }
    }
  }
  val messageDigest = MessageDigest.getInstance("SHA-256")
  private def md5(s: String): String = {
    String.format("%032x", new BigInteger(1, messageDigest.digest(s.getBytes("UTF-8"))))

  }
  override def addExecutorMetric(newExecutor: NewExecutor): Future[Int] = Future {
    blocking {
      DB localTx { implicit session =>
        val startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(newExecutor.time), ZonedDateTime.now().getZone)

        sql"""insert into events_spark_executors(dep_run_id, executor_id, status, cores, address, dt_added)
             values(${newExecutor.runId}, ${newExecutor.execId}, 'active', ${newExecutor.cores}, ${md5(newExecutor.host)}, ${startTime})
           """.update().apply()
      }
    }
  }

  override def getStageMetricDetail(runId: Long,
                                    attemptId: String,
                                    stageId: Int,
                                    parentJobId: Int,
                                    stageAttemptId: Int): Future[Option[SparkStageSummary]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select scheduler_delay, executor_compute_time, shuffle_read_time, shuffle_write_time, getting_result_time,
             task_deserialization_time, result_deserialization_time,
             input_read_records_min, input_read_records_max, input_read_records_mean, input_read_records_total,
                     input_read_size_min, input_read_size_max, input_read_size_mean, input_read_size_total,
                     output_write_size_min, output_write_size_max, output_write_size_mean, output_write_size_total,
                     output_write_records_min, output_write_records_max, output_write_records_mean, output_write_records_total,
              task_duration_min, task_duration_mean, task_duration_max, task_duration_total,
             gc_time_min, gc_time_mean, gc_time_max, gc_time_total, shuffle_read_records_min, shuffle_read_records_mean, shuffle_read_records_max,
             shuffle_read_records_total, shuffle_read_size_min, shuffle_read_size_mean, shuffle_read_size_max, shuffle_read_size_total,
             shuffle_write_size_min, shuffle_write_size_mean, shuffle_write_size_max, shuffle_write_size_total,
             shuffle_write_records_min, shuffle_write_records_mean, shuffle_write_records_max, shuffle_write_records_total
             FROM events_spark_stage
             WHERE dep_run_id = ${runId} AND stage_id = ${stageId} AND app_attempt_id = ${attemptId}
             AND stage_attempt_id = ${stageAttemptId} AND parent_job_id = ${parentJobId}"""
          .map { r =>
            val t = TaskTimeline(
              schedulerDelay = r.long("scheduler_delay"),
              executorComputeTime = r.long("executor_compute_time"),
              shuffleReadTime = r.long("shuffle_read_time"),
              shuffleWriteTime = r.long("shuffle_write_time"),
              gettingResultTime = r.long("getting_result_time"),
              taskDeserializationTime = r.long("task_deserialization_time"),
              resultSerializationTime = r.long("result_deserialization_time")
            )

            val am = StageAggMetrics(
              taskDuration = AggMetric(r.long("task_duration_min"),
                                       r.double("task_duration_mean"),
                                       r.long("task_duration_max"),
                                       r.long("task_duration_total")),
              gcTime = AggMetric(r.long("gc_time_min"), r.double("gc_time_mean"), r.long("gc_time_max"), r.long("gc_time_total")),
              shuffleReadRecords = AggMetric(r.long("shuffle_read_records_min"),
                                             r.double("shuffle_read_records_mean"),
                                             r.long("shuffle_read_records_max"),
                                             r.long("shuffle_read_records_total")),
              shuffleReadSize = AggMetric(r.long("shuffle_read_size_min"),
                                          r.double("shuffle_read_size_mean"),
                                          r.long("shuffle_read_size_max"),
                                          r.long("shuffle_read_size_total")),
              shuffleWriteSize = AggMetric(r.long("shuffle_write_size_min"),
                                           r.double("shuffle_write_size_mean"),
                                           r.long("shuffle_write_size_max"),
                                           r.long("shuffle_write_size_total")),
              shuffleWriteRecords = AggMetric(r.long("shuffle_write_records_min"),
                                              r.double("shuffle_write_records_mean"),
                                              r.long("shuffle_write_records_max"),
                                              r.long("shuffle_write_records_total")),
              inputReadRecords = AggMetric(r.long("input_read_records_min"),
                                           r.double("input_read_records_mean"),
                                           r.long("input_read_records_max"),
                                           r.long("input_read_records_total")),
              inputReadSize = AggMetric(r.long("input_read_size_min"),
                                        r.double("input_read_size_mean"),
                                        r.long("input_read_size_max"),
                                        r.long("input_read_size_total")),
              outputWriteRecords = AggMetric(r.long("output_write_records_min"),
                                             r.double("output_write_records_max"),
                                             r.long("output_write_records_mean"),
                                             r.long("output_write_records_total")),
              outputWriteSize = AggMetric(r.long("output_write_size_min"),
                                          r.double("output_write_size_mean"),
                                          r.long("output_write_size_max"),
                                          r.long("output_write_size_total"))
            )
            SparkStageSummary(am, t)
          }
          .single()
          .apply()
      }
    }
  }

  private def getStartAndEndTs(runId: Long, attemptId: String, startTime: Option[ZonedDateTime], endTime: Option[ZonedDateTime])(
      implicit session: DBSession): Option[(ZonedDateTime, ZonedDateTime)] = {
    (startTime, endTime) match {
      case (Some(st), Some(et)) =>
        Some(st, et)
      case (None, None) =>
        sql"""SELECT start_time, end_time FROM events_spark_app WHERE
          dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => (r.dateTime("start_time"), r.dateTimeOpt("end_time").getOrElse(ZonedDateTime.now())))
          .single()
          .apply()
      case _ => None
    }
  }

  case class CPUDBRow(cpuUsed: Float, systemCpuUsed: Float, ts: ZonedDateTime)
  override def getCPUTimeSerie(runId: Long,
                               attemptId: String,
                               startTime: Option[ZonedDateTime],
                               endTime: Option[ZonedDateTime]): Future[Option[CPUTimeSerie]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val appStartTs = sql"""SELECT start_time FROM events_spark_app WHERE
          dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => r.dateTime("start_time"))
          .single()
          .apply()

        appStartTs.flatMap { appTs =>
          getStartAndEndTs(runId, attemptId, startTime, endTime) match {
            case None => None
            case Some((startTs, endTs)) =>
              val cpuMetrics =
                sql"""SELECT AVG(cluster.executor_process_used) as executor_process_used, AVG(cluster.sytem_cpu_used) as system_cpu_used, cluster.with_time FROM
		    (SELECT tmp.with_time, tmp.address, SUM(tmp.jvm_cpu_process_usage) as executor_process_used, AVG(tmp.jvm_cpu_usage) as sytem_cpu_used FROM (
		SELECT  ese.address,msr.executor_id,
		AVG(jvm_cpu_process_usage) as jvm_cpu_process_usage, AVG(jvm_cpu_usage) as jvm_cpu_usage, time_bucket_gapfill('5 seconds', ts) as with_time
		FROM metrics_spark_runtime as msr
		LEFT JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
		WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${startTs} AND ${endTs}
		GROUP BY msr.executor_id, address,with_time order by with_time) as tmp
		GROUP BY with_time, address
		having AVG(tmp.jvm_cpu_usage) IS NOT NULL ) as cluster
		GROUP BY cluster.with_time order by cluster.with_time"""
                  .map(r => CPUDBRow(r.float("executor_process_used"), r.float("system_cpu_used"), r.dateTime("with_time")))
                  .list()
                  .apply()

              val executorCPU =
                cpuMetrics.map(row =>
                  FloatMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.cpuUsed))
              val systemCPU = cpuMetrics.map(row =>
                FloatMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.systemCpuUsed))

              Some(CPUTimeSerie(startTs.toInstant.toEpochMilli, endTs.toInstant.toEpochMilli, executorCPU, systemCPU))
          }
        }
      }
    }
  }

  case class MemDBRow(memUsed: Double, memAvailable: Double, heapUsed: Double, offHeap: Double, ts: ZonedDateTime)
  override def getMemTimeSerie(runId: Long,
                               attemptId: String,
                               startTime: Option[ZonedDateTime],
                               endTime: Option[ZonedDateTime]): Future[Option[MemoryTimeSerie]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val appStartTs = sql"""SELECT start_time FROM events_spark_app WHERE
          dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => r.dateTime("start_time"))
          .single()
          .apply()

        appStartTs.flatMap { appTs =>
          getStartAndEndTs(runId, attemptId, startTime, endTime) match {
            case None => None
            case Some((startTs, endTs)) =>
              val tsInterval = SQLSyntax.createUnsafely(s"'${groupInterval} seconds'")
              val memUsage = sql"""SELECT SUM(cluster.node_mem_used) as cluster_mem_used, SUM(cluster.node_max_mem) as cluster_max_mem,
     SUM(cluster.net_jvm_heap) as net_jvm_heap, SUM(cluster.net_off_heap) as net_off_heap,
		 cluster.with_time FROM(SELECT tmp.with_time, tmp.address, SUM(tmp.net_mem) as node_mem_used, SUM(tmp.jvm_heap_used) as net_jvm_heap,
		SUM(tmp.off_heap_used) as net_off_heap, AVG(tmp.max_mem) as node_max_mem FROM (
		SELECT  ese.address,msr.executor_id,
		AVG(jvm_total_used) as net_mem, AVG(jvm_heap_used) as jvm_heap_used, AVG(jvm_non_heap_used) as off_heap_used,
		AVG(jvm_mem_size) as max_mem, time_bucket_gapfill(${tsInterval}, ts) as with_time
		FROM metrics_spark_runtime as msr
		LEFT JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
		WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${startTs} AND ${endTs}
		GROUP BY msr.executor_id, address,with_time order by with_time) as tmp
		GROUP BY with_time, address
		having AVG(tmp.max_mem) IS NOT NULL ) as cluster
		GROUP BY cluster.with_time order by cluster.with_time"""
                .map(
                  r =>
                    MemDBRow(r.double("cluster_mem_used"),
                             r.double("cluster_max_mem"),
                             r.double("net_jvm_heap"),
                             r.double("net_off_heap"),
                             r.dateTime("with_time")))
                .list()
                .apply()

              val clusterMemUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toInstant.toEpochMilli - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.memUsed.toLong))
              val clusterMemAvailable = memUsage.map(
                row =>
                  LongMetricValue((row.ts.toInstant.toEpochMilli - appTs.toEpochSecond),
                                  row.ts.toInstant.toEpochMilli,
                                  row.memAvailable.toLong))
              val clusterHeapUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toInstant.toEpochMilli - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.heapUsed.toLong))
              val clusterOffHeapUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toInstant.toEpochMilli - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.offHeap.toLong))

              Some(
                MemoryTimeSerie(startTs.toInstant.toEpochMilli,
                                endTs.toInstant.toEpochMilli,
                                clusterMemAvailable,
                                clusterMemUsed,
                                clusterHeapUsed,
                                clusterOffHeapUsed))
          }
        }
      }
    }
  }

  case class HeapMemDBRow(edenSpace: Double, oldGen: Double, survivorSpace: Double, ts: ZonedDateTime)
  override def getHeapMemTimeSerie(runId: Long,
                                   attemptId: String,
                                   startTime: Option[ZonedDateTime],
                                   endTime: Option[ZonedDateTime]): Future[Option[HeapMemoryTimeSerie]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val appStartTs = sql"""SELECT start_time FROM events_spark_app WHERE
          dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => r.dateTime("start_time"))
          .single()
          .apply()

        appStartTs.flatMap { appTs =>
          getStartAndEndTs(runId, attemptId, startTime, endTime) match {
            case None => None
            case Some((startTs, endTs)) =>
              val memUsage =
                sql"""SELECT SUM(cluster.eden_space_used) as eden_space_used, SUM(cluster.old_gen_used) as old_gen_used, SUM(cluster.survivor_space_used) as survivor_space_used, cluster.with_time FROM
		(SELECT tmp.with_time, tmp.address, SUM(tmp.eden_space_used) as eden_space_used, SUM(tmp.old_gen_used) as old_gen_used, SUM(tmp.survivor_space_used) AS survivor_space_used FROM (
		SELECT  ese.address,msr.executor_id,
		AVG(jvm_pools_gc_eden_space_used) as eden_space_used, AVG(jvm_pools_gc_old_gen_used) as old_gen_used, AVG(jvm_pools_gc_survivor_space_used) as survivor_space_used, time_bucket_gapfill('5 seconds', ts) as with_time
		FROM metrics_spark_runtime as msr
		LEFT JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
		WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${startTs} AND ${endTs}
		GROUP BY msr.executor_id, address,with_time order by with_time) as tmp
		GROUP BY with_time, address
		having SUM(tmp.old_gen_used) IS NOT NULL ) as cluster
		GROUP BY cluster.with_time order by cluster.with_time"""
                  .map(
                    r =>
                      HeapMemDBRow(r.double("eden_space_used"),
                                   r.double("old_gen_used"),
                                   r.double("survivor_space_used"),
                                   r.dateTime("with_time")))
                  .list()
                  .apply()

              val edenSpaceUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.edenSpace.toLong))
              val oldGenSpace =
                memUsage.map(row =>
                  LongMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.oldGen.toLong))
              val survivorSpace = memUsage.map(row =>
                LongMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.survivorSpace.toLong))

              Some(
                HeapMemoryTimeSerie(startTs.toInstant.toEpochMilli,
                                    endTs.toInstant.toEpochMilli,
                                    edenSpaceUsed,
                                    oldGenSpace,
                                    survivorSpace))
          }
        }
      }
    }
  }

  case class OffHeapMemDBRow(metaSpace: Double, codeCache: Double, compressedClass: Double, ts: ZonedDateTime)
  override def getOffHeapMemTimeSerie(runId: Long,
                                      attemptId: String,
                                      startTime: Option[ZonedDateTime],
                                      endTime: Option[ZonedDateTime]): Future[Option[OffHeapMemoryTimeSerie]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val appStartTs = sql"""SELECT start_time FROM events_spark_app WHERE
          dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => r.dateTime("start_time"))
          .single()
          .apply()

        appStartTs.flatMap { appTs =>
          getStartAndEndTs(runId, attemptId, startTime, endTime) match {
            case None => None
            case Some((startTs, endTs)) =>
              val memUsage =
                sql"""SELECT SUM(cluster.metaspace_used) as metaspace_used, SUM(cluster.compressed_class_space_used) as compressed_class_space_used,
                     SUM(cluster.code_cache_used) as code_cache_used, cluster.with_time FROM
		          (SELECT tmp.with_time, tmp.address, SUM(tmp.metaspace_used) as metaspace_used, SUM(tmp.compressed_class_space_used) as compressed_class_space_used,
		          SUM(tmp.code_cache_used) AS code_cache_used FROM (
		          SELECT  ese.address,msr.executor_id,
		          AVG(jvm_pools_metaspace_used) as metaspace_used, AVG(jvm_pools_compressed_class_space_used) as compressed_class_space_used,
		          AVG(jvm_pools_code_cache_used) as code_cache_used, time_bucket_gapfill('5 seconds', ts) as with_time
		          FROM metrics_spark_runtime as msr
		          LEFT JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
		WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${startTs} AND ${endTs}
		GROUP BY msr.executor_id, address,with_time order by with_time) as tmp
		GROUP BY with_time, address
		having SUM(tmp.code_cache_used) IS NOT NULL ) as cluster
		GROUP BY cluster.with_time order by cluster.with_time"""
                  .map(
                    r =>
                      OffHeapMemDBRow(r.double("metaspace_used"),
                                      r.double("compressed_class_space_used"),
                                      r.double("code_cache_used"),
                                      r.dateTime("with_time")))
                  .list()
                  .apply()

              val metaspaceUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.metaSpace.toLong))
              val compressedClassUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.compressedClass.toLong))
              val codeCacheUsed = memUsage.map(row =>
                LongMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.codeCache.toLong))

              Some(
                OffHeapMemoryTimeSerie(startTs.toInstant.toEpochMilli,
                                       endTs.toInstant.toEpochMilli,
                                       metaspaceUsed,
                                       compressedClassUsed,
                                       codeCacheUsed))
          }
        }
      }
    }
  }

  case class CPUCoresDBRow(cores: Int, tasks: Int, ts: ZonedDateTime)
  override def getCoresUtilization(runId: Long,
                                   attemptId: String,
                                   startTime: Option[ZonedDateTime],
                                   endTime: Option[ZonedDateTime]): Future[Option[CpuCoreTimeSerie]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val appStartTs = sql"""SELECT start_time FROM events_spark_app WHERE
          dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => r.dateTime("start_time"))
          .single()
          .apply()

        appStartTs.flatMap { appTs =>
          getStartAndEndTs(runId, attemptId, startTime, endTime) match {
            case None => None
            case Some((startTs, endTs)) =>
              val coresUsage =
                sql"""SELECT tmp.with_time,  SUM(tmp.active_tasks) as active_tasks, SUM(tmp.cores_available) as cores_available FROM (
		                          SELECT  ese.address,td.executor_id,
		                          AVG(td.active_tasks) as active_tasks, AVG(ese.cores) as cores_available, time_bucket_gapfill('5 seconds', ts) as with_time
		                          FROM events_spark_task_distribution as td
		                          LEFT JOIN events_spark_executors as ese ON td.dep_run_id = ese.dep_run_id AND td.executor_id = ese.executor_id
		                          WHERE td.dep_run_id = ${runId} AND ts BETWEEN ${startTs} AND ${endTs} AND td.status = 'active' and td.executor_id <> 'driver'
		                          GROUP BY td.executor_id, address,with_time order by with_time) as tmp
		                          GROUP BY with_time
		                          having SUM(tmp.active_tasks) IS NOT NULL"""
                  .map(r =>
                    CPUCoresDBRow(Math.round(r.float("cores_available")), Math.round(r.float("active_tasks")), r.dateTime("with_time")))
                  .list()
                  .apply()

              val filteredCoresUsage = coresUsage.filter(x => x.tasks <= x.cores)
              val coresAvailable =
                filteredCoresUsage.map(row =>
                  IntMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.cores))
              val activeTasks =
                filteredCoresUsage.map(row =>
                  IntMetricValue((row.ts.toEpochSecond - appTs.toEpochSecond), row.ts.toInstant.toEpochMilli, row.tasks))

              Some(CpuCoreTimeSerie(startTs.toInstant.toEpochMilli, endTs.toInstant.toEpochMilli, coresAvailable, activeTasks))
          }
        }
      }
    }
  }

  override def updateSparkMetric(jobId: Long,
                                 runId: Long,
                                 appAttemptId: String,
                                 avgTaskRuntime: Long,
                                 maxTaskRuntime: Long,
                                 totalExecutorRuntime: Long,
                                 totalJvmGCtime: Long): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""update events_spark_app set avg_task_runtime = ${avgTaskRuntime}, max_task_runtime = ${maxTaskRuntime},
              total_executor_runtime = ${totalExecutorRuntime}, total_gc_time=${totalJvmGCtime} where
              dep_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}
             """
          .update()
          .apply() == 1
      }
    }
  }

  override def appStarted(runId: Long, event: ApplicationStarted, timezone: String): Future[Boolean] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val startTs = new Timestamp(event.time)
           sql"""insert into events_spark_app(dep_run_id, app_id, app_name, spark_user, app_attempt_id, app_status, start_time, last_updated, user_timezone,
                  spark_version, master, executor_mem, driver_mem, executor_cores, memory_overhead)
             values(${runId}, ${event.appId}, ${event.appName}, ${event.sparkUser}, ${event.appAttemptId}, 'running', ${startTs}, ${startTs}, ${timezone},
             ${event.config.sparkVer}, ${event.config.master}, ${event.config.executorMem}, ${event.config.driverMem}, ${event.config.executorCores}, ${event.config.memoryOverHead})
             """
              .update()
              .apply() > 0



        }
      }
    }

  override def appEnded(jobId: Long, runId: Long, event: ApplicationEnded): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""update events_spark_app set app_status = ${event.finalStatus}, end_time = ${ZonedDateTime.now()} where
              dep_run_id = ${runId}
             """
          .update()
          .apply() == 1
      }
    }
  }

  override def jobStarted(jobId: Long, runId: Long, event: JobStarted): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        var insertCounts = 0
        insertCounts =
          sql"""insert into events_spark_jobs(dep_run_id, job_id, app_id, app_attempt_id, job_status,num_stages, num_stages_completed, start_time)
             values(${runId},  ${event.jobId}, ${event.appId}, ${event.appAttemptId}, 'running', ${event.stageInfos.size}, ${0}, ${ZonedDateTime
            .now()} )
             """
            .update()
            .apply()

        event.stageInfos.foreach { s =>
          insertCounts = insertCounts + sql"""insert into events_spark_stage(dep_run_id, parent_job_id, stage_id,
                 stage_attempt_id, stage_status,num_tasks, num_tasks_completed, start_time, app_attempt_id)
             values(${runId}, ${event.jobId}, ${s.id}, ${s.attemptId}, 'waiting', ${s.numTasks}, ${0}, ${ZonedDateTime
            .now()}, ${event.appAttemptId} )
             """
            .update()
            .apply()
        }

        insertCounts == 1 + event.stageInfos.size
      }
    }
  }

  override def jobEnded(jobId: Long, runId: Long, event: JobEnded): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val status = if (event.succeeded) "passed" else "failed"
        sql"""update events_spark_jobs set job_status = ${status}, end_time = ${ZonedDateTime
          .now()}, failed_reason = ${event.failedReason.getOrElse("")}  where
              dep_run_id = ${runId} AND job_id = ${event.jobId}
             """
          .update()
          .apply() == 1
      }
    }
  }

  override def stageSubmitted(jobId: Long, runId: Long, event: StageSubmitted): Future[Boolean] =
    Future {
      blocking {
        DB localTx { implicit session =>
          sql"""update events_spark_stage set stage_status = ${event.status} where
              dep_run_id = ${runId} AND parent_job_id = ${event.parentJobId} AND stage_id = ${event.stageId}
              AND stage_attempt_id = ${event.attemptId}
             """
            .update()
            .apply() == 1

        }
      }
    }

  override def stageCompleted(jobId: Long, runId: Long, event: StageCompleted): Future[Boolean] =
    Future {
      blocking {
        DB localTx { implicit session =>
          if (event.status.equals("succeeded")) {
            sql"""update events_spark_jobs set num_stages_completed = num_stages_completed + 1  where
              dep_run_id = ${runId}  AND job_id = ${event.parentJobId} AND app_attempt_id = ${event.appAttemptId}
             """
              .update()
              .apply()
          }
          sql"""update events_spark_stage set stage_status = ${event.status}, end_time = ${ZonedDateTime
            .now()}, failed_reason = ${event.failureReason.getOrElse("")}  where
              dep_run_id = ${runId} AND parent_job_id = ${event.parentJobId} AND stage_id = ${event.stageId}
              AND stage_attempt_id = ${event.attemptId}
             """
            .update()
            .apply() == 1
        }
      }
    }

  override def taskStarted(jobId: Long, runId: Long, event: TaskStarted): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""insert into events_spark_task(task_id, dep_run_id, stage_id, task_attempt_id, app_attempt_id, task_status, start_time)
             values(${event.taskId}, ${runId}, ${event.stageId}, ${event.taskAttemptId},${event.appAttemptId}, 'running', ${ZonedDateTime
          .now()} )
             """
          .update()
          .apply() == 1

      }
    }
  }

  override def taskCompleted(jobId: Long, runId: Long, event: TaskCompleted): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        if (event.status.equals("SUCCESS")) {

          sql"""update events_spark_stage set num_tasks_completed = num_tasks_completed + 1  where
              dep_run_id = ${runId} AND stage_id = ${event.stageId} AND stage_attempt_id = ${event.stageAttemptId}
               AND app_attempt_id = ${event.appAttemptId}
             """
            .update()
            .apply()
        }
        sql"""update events_spark_task set task_status = ${event.status}, end_time = ${ZonedDateTime
          .now()}, failed_reason = ${event.failedReason.getOrElse("")}  where
              dep_run_id = ${runId} AND app_attempt_id = ${event.appAttemptId} AND task_id = ${event.taskId} AND stage_id = ${event.stageId}
             """
          .update()
          .apply() == 1

      }
    }
  }

  override def getSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[SparkAggMetrics]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select total_executor_runtime, app_status, app_id,user_timezone,
              start_time, end_time, total_gc_time, avg_task_runtime, max_task_runtime from
             events_spark_app where dep_run_id = ${runId} AND app_attempt_id = ${appAttemptId}
           """
          .map { r =>
            val startTs   = r.dateTime("start_time").toInstant.toEpochMilli
            val appStatus = RunStatus.withName(r.string("app_status"))
            val startTime = r.dateTime("start_time").toEpochSecond
            val endTime = r.dateTimeOpt("end_time") match {
              case None        => ZonedDateTime.now().toEpochSecond
              case Some(value) => value.toEpochSecond
            }

            SparkAggMetrics(
              appAttemptId = appAttemptId,
              timeTaken = r.dateTimeOpt("end_time") match {
                case None if appStatus == RunStatus.TimeLimitExceeded => "Time limit exceeded"
                case None =>
                  val now = ZonedDateTime.now(ZoneId.of(r.string("user_timezone"))).toInstant.toEpochMilli
                  DateUtil.formatIntervalMillis(now - startTs)
                case Some(value) =>
                  val endTs = value.toInstant.toEpochMilli
                  DateUtil.formatIntervalMillis(endTs - startTs)
              },
              avgTaskRuntime = DateUtil.formatIntervalMillis(r.long("avg_task_runtime")),
              maxTaskRuntime = DateUtil.formatIntervalMillis(r.long("max_task_runtime")),
              totalExecutorRuntime = DateUtil.formatIntervalMillis(r.long("total_executor_runtime")),
              totalGCTime = DateUtil.formatIntervalMillis(r.long("total_gc_time")),
              status = appStatus,
              startTime,
              endTime
            )
          }
          .single()
          .apply()
      }
    }
  }

  override def getRawSparkAggMetrics(jobId: Long, runId: Long, appAttemptId: String): Future[Option[RawSparkMetric]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select total_executor_runtime,app_status,start_time, end_time, total_gc_time, avg_task_runtime, max_task_runtime from
             events_spark_app where dep_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}
           """
          .map { r =>
            val startTime  = r.dateTime("start_time")
            val endTime    = r.dateTimeOpt("end_time")
            val appRuntime = endTime.map(zd => zd.toInstant.toEpochMilli - startTime.toInstant.toEpochMilli)
            RawSparkMetric(
              appAttemptId = appAttemptId,
              avgTaskRuntime = r.long("avg_task_runtime"),
              maxTaskRuntime = r.long("max_task_runtime"),
              totalExecutorRuntime = r.long("total_executor_runtime"),
              totalGCTime = r.long("total_gc_time"),
              appRuntime = appRuntime,
              status = RunStatus.withName(r.string("app_status"))
            )
          }
          .single()
          .apply()
      }
    }
  }

  override def listExecutorSummaries(jobId: Long, runId: Long): Future[Seq[ExecutorMetricSummary]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT jvm_total_max, executor_id, AVG(jvm_cpu_process_usage) as avg_cpu_usage, AVG(jvm_total_used) as avg_mem_used
             from metrics_spark_runtime metric
             where dep_run_id = ${runId} GROUP BY executor_id, jvm_total_max limit 10;
           """
          .map { r =>
            ExecutorMetricSummary(
              executorId = r.string("executor_id"),
              maxMemory = r.double("jvm_total_max").toLong,
              avgUsedMemory = r.double("avg_mem_used").toLong,
              avgProcessCpuUsage = r.double("avg_cpu_usage")
            )
          }
          .list()
          .apply()
      }
    }
  }

  private[repo] def extractRuntimeMetrics(x: WrappedResultSet): InstantMetric = {

    val cpuMetric = CPUMetric(x.double("jvm_cpu_process_usage"), x.double("jvm_cpu_usage"))
    val overallUsage = JVMMemOverallUsage(
      swapSize = x.long("jvm_swap_size"),
      swapFree = x.long("jvm_swap_free"),
      freeMemory = x.long("jvm_mem_free"),
      totalUsed = x.long("jvm_total_used")
    )
    val gcTime = JVMGCTime(
      marksweepCount = x.long("jvm_gc_marksweep_count"),
      marksweepTime = x.long("jvm_gc_marksweep_time"),
      scavengeCount = x.long("jvm_gc_collection_count"),
      scavengeTime = x.long("jvm_gc_collection_time")
    )
    val heapUsage = JVMHeapUsage(
      init = x.long("jvm_heap_init"),
      used = x.long("jvm_heap_used"),
      max = x.long("jvm_heap_max"),
      fracUsage = x.double("jvm_heap_usage"),
      edenSpaceUsed = x.long("jvm_pools_gc_eden_space_max"),
      oldGenUsed = x.long("jvm_pools_gc_old_gen_used"),
      survivorSpaceUsed = x.long("jvm_pools_gc_survivor_space_used")
    )
    val nonHeapUsage = JVMNonHeapUsage(
      used = x.long("jvm_non_heap_used"),
      max = x.long("jvm_non_heap_max"),
      fracUsage = x.double("jvm_non_heap_usage"),
      init = x.long("jvm_non_heap_init"),
      codeCacheUsed = x.long("jvm_pools_code_cache_used"),
      compressedClassSpaceUsed = x.long("jvm_pools_compressed_class_space_used"),
      metaspaceUsed = x.long("jvm_pools_metaspace_used")
    )
    InstantMetric(x.dateTime("ts").toEpochSecond, 0f, heapUsage, nonHeapUsage, gcTime, overallUsage, cpuMetric)

  }

  override def getExecutorRuntimeMetrics(jobId: Long, runId: Long, executorId: String): Future[Option[ExecutorRuntimeMetric]] = Future {
    blocking {
      DB localTx { implicit session =>
        var cpus: Long         = 0
        var cpuAllocated       = 0
        var address            = ""
        var status             = ""
        var memSize: Long      = 0L
        var memAllocated: Long = 0L
        val runtimeMetrics =
          sql"""select jvm_gc_marksweep_count, jvm_gc_marksweep_time, jvm_gc_collection_count, jvm_gc_collection_time, ts,
            jvm_heap_usage, jvm_heap_init, jvm_heap_max, jvm_heap_used, jvm_pools_gc_eden_space_max, jvm_pools_gc_old_gen_used, jvm_pools_gc_survivor_space_used,
            jvm_non_heap_used, jvm_non_heap_usage, jvm_non_heap_max, jvm_non_heap_init, jvm_pools_code_cache_used, jvm_pools_compressed_class_space_used, jvm_pools_metaspace_used,
            jvm_cpu_usage, jvm_cpu_process_usage, jvm_swap_size, jvm_swap_free, jvm_mem_free, jvm_total_used, jvm_cpu_num_available, ex.status, ex.address,
            ex.cores, jvm_mem_size, jvm_total_max FROM metrics_spark_runtime me
            INNER JOIN events_spark_executors ex ON me.executor_id = ex.executor_id AND me.dep_run_id = ex.dep_run_id
             WHERE me.executor_id = ${executorId} AND me.dep_run_id = ${runId}
             """
            .map { r =>
              cpus = r.long("jvm_cpu_num_available")
              memSize = r.long("jvm_mem_size")
              memAllocated = r.long("jvm_total_max")
              cpuAllocated = r.int("cores")
              address = r.string("address")
              status = r.string("status")
              extractRuntimeMetrics(r)
            }
            .list()
            .apply()

        if (runtimeMetrics.isEmpty) {
          None
        } else {
          val duplicateByTime = runtimeMetrics
            .groupBy(_.timestamp)
            .mapValues(_.last)
            .map(x => x._2.copy(timestamp = x._2.timestamp * 1000))
            .toSeq
            .sortWith((x, y) => x.timestamp < y.timestamp)
          val extractedMetric =
            ExecutorRuntimeMetric(executorId, status, address, cpuAllocated, memSize, memAllocated, cpus, duplicateByTime)

          Some(extractedMetric)
        }
      }
    }
  }

  override def getExecutionMetrics(runId: Long, attemptId: String): Future[Option[ExecutionMetricSummary]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val metric = sql"""SELECT executor_id, status, address, max_storage_memory, used_storage FROM events_spark_executors
             WHERE dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r => (r.string("executor_id"), r.string("address"), r.long("max_storage_memory"), r.long("used_storage")))
          .list()
          .apply()

        val intervalDetail =
          sql"""SELECT start_time, end_time, user_timezone FROM events_spark_app WHERE dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
            .map(r =>
              (r.dateTime("start_time"), r.dateTimeOpt("end_time").getOrElse(ZonedDateTime.now(ZoneId.of(r.string("user_timezone"))))))
            .single()
            .apply()

        intervalDetail.flatMap {
          case (start, end) =>
            val maxHeap =
//              sql"""SELECT MAX(jvm_heap_used) FROM metrics_spark_runtime WHERE dep_run_id = ${runId}) AND ts between ${start} AND ${end} """
//                .map(r => (r.long("jvm_heap_max"), r.long("jvm_heap_used")))
//                .single()
//                .apply()
//            val maxHeap =
              sql"""SELECT AVG(jvm_heap_max) as jvm_heap_max, MAX(jvm_heap_used) AS jvm_heap_used, dep_run_id
                     FROM metrics_spark_runtime  WHERE dep_run_id = ${runId} group by dep_run_id"""
                .map(r => (r.long("jvm_heap_max"), r.long("jvm_heap_used")))
                .single()
                .apply()

            maxHeap.map {
              case (maxHeap, heapUsed) =>
                val executorsCount = metric.size
                metric.foldLeft(ExecutionMetricSummary(0, executorsCount, heapUsed, maxHeap, 0, 0, Set.empty[String])) {
                  case (am, (_, workerId, maxStorage, usedStorage)) =>
                    val workers             = Set(workerId) ++ am.workers
                    val peakStorageMemory   = if (usedStorage > am.storageMemoryUsed) usedStorage else am.storageMemoryUsed
                    val availableStorageMem = if (peakStorageMemory == usedStorage) maxStorage else am.storageMemoryAllocated
                    am.copy(workersCount = workers.size,
                            workers = workers,
                            storageMemoryUsed = peakStorageMemory,
                            storageMemoryAllocated = availableStorageMem)
                }
            }
        }
      }
    }
  }

  override def getWorkersRuntimeMetrics(runId: Long, attemptId: String, workerId: String): Future[Option[WorkerRuntimeMetrics]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT start_time, end_time, user_timezone FROM events_spark_app WHERE dep_run_id = ${runId} AND app_attempt_id = ${attemptId}"""
          .map(r =>
            (r.dateTime("start_time"), r.dateTimeOpt("end_time").getOrElse(ZonedDateTime.now(ZoneId.of(r.string("user_timezone"))))))
          .single()
          .apply() flatMap {
          case (startTime, endTime) =>
            val executors = sql"""SELECT executor_id FROM events_spark_executors WHERE dep_run_id = ${runId} AND
              app_attempt_id = ${attemptId} AND address = ${workerId}"""
              .map(r => r.string("executor_id"))
              .list()
              .apply()

            val workerMetrics =
              sql"""SELECT tmp.executor_id, AVG(tmp.jvm_mem_used) as jvm_mem_used, AVG(tmp.worker_mem) as worker_mem, AVG(tmp.cpu_cores) as cpu_cores, with_time FROM
                (SELECT  msr.executor_id, AVG(jvm_total_used) as jvm_mem_used, AVG(jvm_mem_size) as worker_mem,
                                AVG(jvm_cpu_num_available) as cpu_cores,
                                               time_bucket_gapfill('5 seconds', ts) as with_time
                                               FROM metrics_spark_runtime as msr
                                               INNER JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
                                               WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${startTime} AND ${endTime}
                                               AND address = ${workerId}
                                               GROUP BY msr.executor_id, with_time) as tmp
                                               GROUP BY tmp.executor_id, with_time
                                               having AVG(tmp.jvm_mem_used) IS NOT NULL
                                               ORDER BY with_time"""
                .map(
                  r =>
                    (r.string("executor_id"),
                     r.int("cpu_cores"),
                     r.double("jvm_mem_used").toLong,
                     r.long("worker_mem"),
                     r.dateTime("with_time")))
                .list()
                .apply()

            if (workerMetrics.nonEmpty) {
              val (_, cpuCores, _, workerMem, _) = workerMetrics.head
              val wm                             = WorkerRuntimeMetrics(workerId, cpuCores, workerMem, Seq())

              val executorMetrics = workerMetrics
                .map {
                  case (executorId, _, jvmMemUsed, _, ts) => (executorId, jvmMemUsed, ts)
                }
                .groupBy {
                  case (str, _, _) => str
                }
                .map {
                  case (execId, xs) =>
                    ExecutorMemoryTimeserie(execId, xs.map {
                      case (_, l, time) => TotalMemoryTimeSerie(time.toInstant.toEpochMilli, l)
                    })

                }

              Some(wm.copy(executorsMemory = executorMetrics.toSeq))

            } else None

        }
      }
    }
  }

  override def getExecutorMetric(runId: Long, attemptId: String): Future[Option[ExecutorMetricResponse]] = Future {
    blocking {
      DB localTx { implicit session =>
        var totalCores          = 0
        var totalActiveTasks    = 0L
        var totalFailedTasks    = 0L
        var totalCompletedTasks = 0L
        var totalTaskTime       = 0L
        var totalMemoryUsed     = 0L
        var totalMaxMemory      = 0L
        var totalDiskUsed       = 0L

        val execMetrics = sql"""select cores, status, executor_id, rdd_blocks, max_storage_memory, used_storage, disk_used, active_tasks,
             failed_tasks, completed_tasks, task_time, input_bytes, shuffle_read, shuffle_write from
             events_spark_executors where dep_run_id = ${runId} AND app_attempt_id = ${attemptId}
           """
          .map { r =>
            totalCores += r.int("cores")
            totalActiveTasks += r.long("active_tasks")
            totalFailedTasks += r.long("failed_tasks")
            totalCompletedTasks += r.long("completed_tasks")
            totalTaskTime += r.long("task_time")
            totalMemoryUsed += r.longOpt("used_storage").getOrElse(0L)
            totalMaxMemory += r.longOpt("max_storage_memory").getOrElse(1L)
            totalDiskUsed += r.long("disk_used")

            ExecutorMetricInstance(
              cores = r.int("cores"),
              status = r.string("status"),
              execId = r.string("executor_id"),
              rddBlocks = r.int("rdd_blocks"),
              maxStorageMemory = MetricConverter.toReadableSize(r.long("max_storage_memory")),
              usedMemory = MetricConverter.toReadableSize(r.long("used_storage")),
              diskUsed = MetricConverter.toReadableSize(r.long("disk_used")),
              activeTasks = r.long("active_tasks"),
              failedTasks = r.long("failed_tasks"),
              completedTasks = r.long("completed_tasks"),
              taskRuntime = DateUtil.formatIntervalMillis(r.long("task_time")),
              inputSize = MetricConverter.toReadableSize(r.long("input_bytes")),
              shuffleRead = MetricConverter.toReadableSize(r.long("shuffle_read")),
              shuffleWrite = MetricConverter.toReadableSize(r.long("shuffle_write"))
            )
          }
          .list()
          .apply()
        if (execMetrics.size == 0) {
          None
        } else {
          val memoryPct = if (totalMaxMemory > 0) {
            BigDecimal(totalMemoryUsed / totalMaxMemory.toDouble).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble * 100
          } else {
            0D
          }
          val m = ExecutorMetricResponse(
            totalCores,
            totalActiveTasks,
            totalFailedTasks,
            totalCompletedTasks,
            DateUtil.formatIntervalMillis(totalTaskTime),
            memoryPct,
            MetricConverter.toReadableSize(totalMaxMemory),
            MetricConverter.toReadableSize(totalMemoryUsed),
            MetricConverter.toReadableSize(totalDiskUsed),
            execMetrics
          )
          Some(m)
        }
      }
    }
  }

  override def fetchRunComparison(run: JobRun): Future[Option[CompareResponse]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select jobs.name, job_history.status, run_index, avg_cpu_usage, avg_memory_usage, runtime FROM job_history INNER JOIN
              jobs ON job_history.job_id = jobs.id
              LEFT OUTER JOIN job_run_stats ON job_history.id = job_run_stats.run_id AND
              job_history.job_id = jobs.id
               where job_history.id = ${run.runId}  AND job_history.job_id = ${run.projectId} """
          .map(r =>
            CompareResponse(
              run.projectId,
              run.runId,
              r.int("run_index"),
              DateUtil.formatIntervalMillis(r.long("runtime")),
              r.string("name"),
              r.string("status"),
              r.doubleOpt("avg_memory_usage").getOrElse(0D),
              r.doubleOpt("avg_cpu_usage").getOrElse(0D),
              RuntimeCounters(0, 0),
              SlowestRuntime(0, "", 0, ""),
              ExecutorCounters(0, 0, 0, 0, 0),
              SparkConfProps("", "", "", "", 0, "")
          ))
          .single()
          .apply()
          .flatMap { initialResponse =>
            sql"""select count(*) as executors_count, SUM(used_storage) as used_storage, SUM(max_storage_memory) as total_storage,
                 SUM(total_tasks) as total_tasks, SUM(input_bytes) as input_read_size FROM events_spark_executors
                 WHERE dep_run_id = ${run.runId}
                 """
              .map(
                r =>
                  initialResponse.copy(
                    executors = ExecutorCounters(r.int("executors_count"),
                                                 r.long("used_storage"),
                                                 r.long("total_storage"),
                                                 r.int("total_tasks"),
                                                 r.long("input_read_size"))))
              .single()
              .apply()
          }
          .flatMap { cr =>
            sql"""select COUNT(*) as job_count, SUM(num_stages) as total_stages FROM events_spark_jobs WHERE  dep_run_id = ${run.runId}"""
              .map(r => cr.copy(counters = RuntimeCounters(r.int("job_count"), r.int("total_stages"))))
              .single()
              .apply()
          }
          .flatMap { cr =>
            sql"""SELECT spark_version, master, executor_mem, driver_mem, executor_cores, memory_overhead FROM
               events_spark_app WHERE dep_run_id = ${run.runId}"""
              .map(
                r =>
                  SparkConfProps(r.string("spark_version"),
                                 r.string("master"),
                                 r.string("executor_mem"),
                                 r.string("driver_mem"),
                                 r.int("executor_cores"),
                                 r.string("memory_overhead")))
              .list()
              .apply()
              .headOption
              .map(v => cr.copy(config = v))
          }
          .flatMap { cr =>
            val slowestJob = sql"""SELECT job_id, EXTRACT(EPOCH FROM (end_time - start_time)) as run_time FROM events_spark_jobs
                       WHERE EXTRACT(EPOCH FROM (end_time - start_time)) = (
               SELECT MAX(EXTRACT(EPOCH FROM (end_time - start_time))) as run_time FROM events_spark_jobs WHERE dep_run_id = ${run.runId} GROUP BY dep_run_id
               ) """
              .map(r => (r.int("job_id"), DateUtil.formatInterval(r.double("run_time").toLong)))
              .single()
              .apply()

            val slowestStage = sql"""SELECT stage_id, EXTRACT(EPOCH FROM (end_time - start_time)) as run_time FROM events_spark_stage
                 WHERE EXTRACT(EPOCH FROM (end_time - start_time)) = (
               SELECT MAX(EXTRACT(EPOCH FROM (end_time - start_time))) as run_time FROM events_spark_stage
               WHERE dep_run_id = ${run.runId} AND stage_status = ${RunStatus.Succeeded.toString} GROUP BY dep_run_id
             )"""
              .map(r => (r.int("stage_id"), DateUtil.formatInterval(r.double("run_time").toLong)))
              .single()
              .apply()

            for {
              (jobId, jobRuntime)     <- slowestJob
              (stageId, stageRuntime) <- slowestStage
            } yield {
              cr.copy(slowest = SlowestRuntime(jobId, jobRuntime, stageId, stageRuntime))
            }
          }

      }
    }

  }

  override def getSparkAggMetricsAll(jobId: Long, runId: Long): Future[Option[SparkAppInfo]] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val sparkInfoMap =
            sql"""SELECT JO.name, app_id, dh.id as dep_run_id, dh.status as deployment_run_status,
dh.dt_started as dep_started, dh.dt_last_updated as dep_ended,
                   dh.run_index, end_time, start_time, user_timezone, app_attempt_id, app_status,
                  total_executor_runtime, total_gc_time, avg_task_runtime, max_task_runtime from
                  events_spark_app as esa RIGHT OUTER JOIN deployment_history as dh ON esa.dep_run_id = dh.id
                  INNER JOIN deployment_config as dc ON dh.deployment_id = dc.id
                  INNER JOIN jobs as JO ON dc.job_id = JO.id
                  WHERE dh.id = ${runId}
           """.map { r =>
                val startTs   = r.dateTimeOpt("start_time").getOrElse(r.dateTime("dep_started")).toInstant.toEpochMilli
                val startTime = r.dateTimeOpt("start_time").getOrElse(r.dateTime("dep_started")).toEpochSecond
                val endTime = r.dateTimeOpt("end_time") match {
                  case Some(value) => value.toEpochSecond
                  case None if(RunStatus.withName(r.string("deployment_run_status")) == RunStatus.Running)  => ZonedDateTime.now().toEpochSecond
                  case _ => r.dateTime("dep_ended").toEpochSecond
                }
                val hasAppId = r.stringOpt("app_id").isDefined

                (r.string("name"), r.stringOpt("app_id").getOrElse(""), r.long("dep_run_id"),
                  r.long("run_index")) -> SparkAggMetrics(
                  appAttemptId = r.stringOpt("app_attempt_id").getOrElse(""),
                  timeTaken = r.dateTimeOpt("end_time") match {
                    case None if RunStatus.withName(r.stringOpt("app_status").getOrElse(r.string("deployment_run_status"))) == RunStatus.TimeLimitExceeded => "Unknown"
                    case None =>
                      DateUtil.formatIntervalMillis(DateUtil.now.toInstant.toEpochMilli - startTs)
                    case Some(value) =>
                      val endTs = value.toInstant.toEpochMilli
                      DateUtil.formatIntervalMillis(endTs - startTs)
                  },
                  avgTaskRuntime = r.longOpt("avg_task_runtime") match {
                    case None        => "--"
                    case Some(value) => DateUtil.formatIntervalMillis(value)
                  },
                  maxTaskRuntime = r.longOpt("max_task_runtime") match {
                    case None        => "--"
                    case Some(value) => DateUtil.formatIntervalMillis(value)
                  },
                  totalExecutorRuntime = r.longOpt("total_executor_runtime") match {
                    case None        => "--"
                    case Some(value) => DateUtil.formatIntervalMillis(value)
                  },
                  totalGCTime = r.longOpt("total_gc_time") match {
                    case None        => "--"
                    case Some(value) => DateUtil.formatIntervalMillis(value)
                  },
                  status = RunStatus.withName(r.stringOpt("app_status").getOrElse(r.string("deployment_run_status"))),
                  startTime = startTime,
                  endTime = endTime
                )
              }
              .list()
              .apply()
              .groupBy(_._1)
          if (sparkInfoMap.size == 0) {
            None
          } else {
            val info = sparkInfoMap.map {
              case ((name, appId, runId, runSeq), metrics) =>
                val allStatus = metrics.map(_._2.status)
                val attemptMetrics = if(appId.isEmpty) Seq() else metrics.map(_._2)
                val finalStatus = {
                  if (allStatus.size == 1) {
                    allStatus.head
                  } else if (allStatus.contains(RunStatus.Succeeded)) {
                    RunStatus.Succeeded
                  } else if (allStatus.contains(RunStatus.Running)) {
                    RunStatus.Running
                  } else {
                    RunStatus.Failed
                  }
                }
                SparkAppInfo(name, runId, runSeq, appId, finalStatus, attemptMetrics)
            }.head
            Some(info)
          }

        }
      }
    }

  override def getSparkJobMetrics(jobId: Long,
                                  runId: Long,
                                  appAttemptId: String,
                                  startTime: Long,
                                  endTime: Long): Future[JobMetricResponse] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val dtStarted   = ZonedDateTime.ofInstant(Instant.ofEpochSecond(startTime), ZoneId.systemDefault())
          val dtCompleted = ZonedDateTime.ofInstant(Instant.ofEpochSecond(endTime), ZoneId.systemDefault())
          val jobCount =
            sql"""select count(job_id) as count FROM events_spark_jobs
                 WHERE dep_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}"""
              .map(_.int("count"))
              .single()
              .apply()
              .getOrElse(0)
          val jobs = sql"""select job_id,name, job_status, num_stages, num_stages_completed, failed_reason, start_time, end_time from
             events_spark_jobs where dep_run_id = ${runId}  AND app_attempt_id = ${appAttemptId}
             AND ((start_time BETWEEN ${dtStarted} AND ${dtCompleted}) OR ((start_time <= ${dtStarted})
             AND (end_time IS NULL OR end_time >= ${dtStarted} )))
           """
            .map { r =>
              val runtime = DateUtil.timeElapsed(r.dateTime("start_time"), r.dateTimeOpt("end_time"))
              JobState(
                jobId = r.long("job_id"),
                name = r.string("name"),
                startedAt = DateUtil.timeElapsed(r.dateTime("start_time"), None) + " ago",
                runtime = runtime,
                currentStatus = r.string("job_status"),
                numStages = r.int("num_stages"),
                completedStages = r.int("num_stages_completed"),
                startTime = r.dateTime("start_time").toEpochSecond * 1000,
                endTime = r.dateTimeOpt("end_time") match {
                  case None        => ZonedDateTime.now().toEpochSecond * 1000
                  case Some(value) => value.toEpochSecond * 1000
                }
              )
            }
            .list()
            .apply()
            .sortWith((x, y) => x.jobId > y.jobId)
          JobMetricResponse(jobCount, jobs)
        }
      }
    }

  override def getSparkStageMetrics(jobId: Long, runId: Long, appAttemptId: String, sparkJobId: Int): Future[Seq[StageState]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select stage_id, start_time,name, end_time, stage_attempt_id, stage_status, num_tasks, num_tasks_completed,
              shuffle_read_records_total, shuffle_read_size_total,shuffle_write_size_total,shuffle_write_records_total,
              records_read, records_written, bytes_read, bytes_written from
             events_spark_stage where dep_run_id = ${runId} AND app_attempt_id = ${appAttemptId} and parent_job_id = ${sparkJobId}
           """
          .map { r =>
            val stageStatus = r.string("stage_status")
            val runtime = r.dateTimeOpt("end_time") match {
              case None =>
                if (stageStatus.equalsIgnoreCase(RunStatus.Running.toString) || stageStatus.equalsIgnoreCase(RunStatus.Failed.toString)) {
                  DateUtil.timeElapsed(r.dateTime("start_time"), None)
                } else stageStatus
              case Some(value) => DateUtil.timeElapsed(r.dateTime("start_time"), Some(value))
            }
            StageState(
              stageId = r.long("stage_id"),
              stageAttemptId = r.int("stage_attempt_id"),
              name = r.string("name"),
              startedAt = DateUtil.timeElapsed(r.dateTime("start_time"), None) + " ago",
              runtime = runtime,
              currentStatus = r.string("stage_status"),
              numTasks = r.int("num_tasks"),
              completedTasks = r.int("num_tasks_completed"),
              shuffleRecordsRead = r.long("shuffle_read_records_total"),
              shuffleRecordsWritten = r.long("shuffle_write_records_total"),
              recordsRead = r.long("records_read"),
              recordsWritten = r.long("records_written"),
              shuffleInputSize = MetricConverter.toReadableSize(r.long("shuffle_read_size_total")),
              shuffleWriteSize = MetricConverter.toReadableSize(r.long("shuffle_write_size_total")),
              inputSize = MetricConverter.toReadableSize(r.long("bytes_read")),
              outputSize = MetricConverter.toReadableSize(r.long("bytes_written"))
            )
          }
          .list()
          .apply()
          .sortWith((x, y) => x.stageId < y.stageId)
      }
    }
  }

  override def saveRuntimeMetric(workspaceId: Long,
                                 runId: Long,
                                 timestampSec: Long,
                                 tz: String,
                                 appId: String,
                                 executorId: String,
                                 metricWithVals: List[(String, Double)]): Future[Int] = {

    Future {
      blocking {
        DB localTx { implicit session =>
          val eventTime    = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestampSec), ZoneId.of(tz))
          val tsFormat     = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
          val timestampStr = tsFormat.format(new Date(eventTime.toInstant.toEpochMilli))
          val filteredMetrics = metricWithVals.filter {
            case (str, _) => jvmRuntimeMetrics.find(p => p.equalsIgnoreCase(str)).isDefined
          }
          val metricColNames = filteredMetrics.map(_._1).mkString(", ")
          val metricColVals  = filteredMetrics.map(_._2).mkString(", ")
          SQL(
            s"insert into metrics_spark_runtime(dep_run_id, app_id, executor_id, ts, ${metricColNames}) VALUES(${runId}, '${appId}', '${executorId}', '${timestampStr}', ${metricColVals})")
            .update()
            .apply()
        }

      }
    }
  }
}

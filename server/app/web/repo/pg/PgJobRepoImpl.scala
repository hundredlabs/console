package web.repo.pg

import java.sql.PreparedStatement
import java.time.{ZoneId, ZonedDateTime}

import com.gigahex.commons.models.JobType.JobType
import com.gigahex.commons.models.{ DeploymentJob, ErrorSource, JVMException, JobConfig, JobType, NewDeploymentRequest, RunStatus}
import com.gigahex.commons.models.RunStatus.RunStatus
import web.models.spark.JobInfoId
import web.models.{DeploymentDetail, JobFormats, JobInstanceSnip, JobInstanceView, JobListView, JobRunInfo, JobRunSnip, JobStats, JobSummary, JobValue, JobView, JobViewsBuilder, RegisterJob, RegisterJobRequest, SparkProjectConfig, TaskInstance, TaskMeta}
import web.repo.JobRepository
import web.utils.DateUtil
import org.postgresql.util.PGobject
import scalikejdbc.DB

import scala.concurrent.{ExecutionContext, Future, blocking}
import scalikejdbc._
import play.api.libs.json.Json

class PgJobRepoImpl(blockingEC: ExecutionContext) extends JobRepository with JobFormats {
  implicit val ec = blockingEC

  case class ProjectRunStats(runId: Long,
                             executorAddr: String,
                             netMemUsage: Double,
                             maxMem: Double,
                             netCpuUsage: Double,
                             executorCount: Int)
  case class ProjectRunStatsByHost(runId: Long,
                                   status: String,
                                   runIndex: Int,
                                   deploymentId: Long,
                                   deploymentName: String,
                                   timeStarted: ZonedDateTime,
                                   execAddress: String,
                                   runtime: Long,
                                   netMemUsage: Double,
                                   maxMem: Double,
                                   cpuUsage: Double,
                                   execCount: Int)

  private def insertToJobs(workspaceId: Long, jobReq: RegisterJobRequest): Long = {
    DB localTx { implicit session =>
      val jobId =
        sql"""insert into jobs(name, description, workspace_id, job_type, dt_created, last_updated)
              values(${jobReq.jobConfig.jobName}, 'No description', ${workspaceId},${jobReq.jobType.toString}, ${ZonedDateTime
          .now()}, ${ZonedDateTime.now()})"""
          .updateAndReturnGeneratedKey()
          .apply()

      jobId
    }
  }

  private def updateProject(orgId: Long, projectId: Long, jobReq: RegisterJobRequest): Boolean = {
    DB localTx { implicit session =>

        sql"update jobs SET name = ${jobReq.jobConfig.jobName}, last_updated = ${ZonedDateTime.now()} WHERE id = ${projectId}"
          .update()
          .apply() > 0

    }
  }

  override def getLatestJobHistory(jobId: Long): Future[Seq[JobInstanceView]] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val statsByRunPerHost = sql"""SELECT tmp.dep_run_id, tmp.name, tmp.dep_id, tmp.run_index, tmp.status, tmp.dt_started,
                       tmp.runtime,
                       case
                       WHEN tmp.address IS NULL THEN '..'
                       ELSE tmp.address
                       END AS address,
                       case
                       WHEN SUM(tmp.avg_mem_usage) IS NULL THEN 0
                       ELSE SUM(tmp.avg_mem_usage)
                       END AS net_mem_usage,
                       case
                        WHEN tmp.max_mem is null THEN 1
                        ELSE tmp.max_mem
                        END AS max_mem,
                       case
                        WHEN SUM(tmp.avg_cpu) is null THEN 0
                        ELSE SUM(tmp.avg_cpu)
                        end as net_cpu_usage
             FROM ( SELECT dh.id as dep_run_id, dc.name, dc.id as dep_id, dh.run_index, dh.dt_started,dh.status, msr.executor_id, ese.address,
              AVG(jvm_total_used) as avg_mem_usage, AVG(jvm_mem_size) as max_mem,
              EXTRACT(EPOCH FROM (dh.dt_last_updated - dh.dt_started)) as runtime,
             AVG(jvm_cpu_process_usage) as avg_cpu
           FROM metrics_spark_runtime as msr
           RIGHT JOIN deployment_history as dh ON msr.dep_run_id = dh.id
           INNER JOIN deployment_config as dc ON dh.deployment_id = dc.id
           LEFT JOIN events_spark_executors as ese ON dh.id = ese.dep_run_id AND msr.executor_id = ese.executor_id
           where dc.job_id = $jobId
           group by msr.executor_id, ese.address, dh.id, dh.run_index,dh.status, dc.id, dc.name, dh.dt_started, dh.dt_last_updated) as tmp
           group by dep_run_id, address, max_mem, run_index,status, dt_started, name, dep_id, runtime order by dep_run_id desc"""
            .map { r =>
              ProjectRunStatsByHost(
                r.long("dep_run_id"),
                r.string("status"),
                r.int("run_index"),
                r.long("dep_id"),
                r.string("name"),
                r.zonedDateTime("dt_started"),
                r.string("address"),
                r.long("runtime"),
                r.double("net_mem_usage"),
                r.double("max_mem"),
                r.double("net_cpu_usage"),
                0
              )
            }
            .list()
            .apply()

          if (statsByRunPerHost.size > 0) {
            statsByRunPerHost
              .groupBy(_.runId)
              .map {
                case (l, statsExecs) =>
                  val head = statsExecs.head
                  statsExecs.foldLeft(
                    ProjectRunStatsByHost(l,
                                          head.status,
                                          head.runIndex,
                      head.deploymentId,
                                          head.deploymentName,
                                          head.timeStarted,
                                          "all",
                                          head.runtime,
                                          0D,
                                          0D,
                                          0D,
                                          statsExecs.size)) {
                    case (x, y) =>
                      x.copy(netMemUsage = x.netMemUsage + y.netMemUsage, maxMem = x.maxMem + y.maxMem, cpuUsage = x.cpuUsage + y.cpuUsage)
                  }
              }
              .map { stats =>
                val fracMemUsage = stats.netMemUsage / stats.maxMem
                val fracCpuUsage = stats.cpuUsage / stats.execCount
                JobInstanceView(
                  id = stats.runId,
                  runIndex = stats.runIndex,
                  deploymentId = stats.deploymentId,
                  deploymentName = stats.deploymentName,
                  runtime = DateUtil.formatInterval(stats.runtime),
                  status = stats.status,
                  startedAt = DateUtil.timeElapsed(stats.timeStarted, None) + " ago",
                  avgMemUsed = f"${fracMemUsage * 100}%1.1f" + "%",
                  avgCpuUsed = f"${fracCpuUsage * 100}%1.1f" + "%"
                )
              }
              .toSeq
              .sortWith((x, y) => x.id < y.id)
              .zipWithIndex
              .map {
                case (view, i) => view.copy(runIndex = i + 1)
              }
              .takeRight(10)

          } else Seq()

        }
      }
    }

  override def getDeploymentDetail(workspaceId: Long, deploymentId: Long): Future[Option[DeploymentDetail]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""select dc.name as dep_name, config, c.name as cluster_name, c.id as cluster_id, provider, status FROM deployment_config as dc
             INNER JOIN clusters as c ON dc.target_id = c.id WHERE dc.id = ${deploymentId}
             AND c.workspace_id = $workspaceId"""
          .map(r => DeploymentDetail(
            clusterId = r.long("cluster_id"),
            clusterName = r.string("cluster_name"),
            status = r.string("status"),
            deploymentName = r.string("dep_name"),
            jobConfig = Json.parse(r.string("config")).as[JobConfig]
          )).single().apply()
      }
    }
  }

  override def getSparkTask(jobId: Long, runId: Long): Future[Option[Long]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT th.id from       as th INNER JOIN
             job_history as jh ON th.job_run_id = jh.id
              WHERE th.job_run_id = ${runId} AND jh.job_id = ${jobId}""".map(_.long("id")).single().apply()
      }
    }
  }


  override def upsert(workspaceId: Long, jobReq: RegisterJobRequest, projectId: Option[Long]): Future[Long] =
    Future {
      blocking {
        DB localTx { implicit session =>
          projectId match {
            case None =>
              val jobId = sql"SELECT id from jobs WHERE name = ${jobReq.jobConfig.jobName} AND workspace_id = ${workspaceId}"
                .map(rs => rs.long("id"))
                .single()
                .apply()
              jobId match {
                case None     =>
                  val newJobId = insertToJobs(workspaceId, jobReq)
                  val strJobConf = Json.toJson(jobReq.jobConfig).toString()
                  val jsonObj    = new PGobject()
                  jsonObj.setType("json")
                  jsonObj.setValue(strJobConf)

                  val jobConfigJson = ParameterBinder(
                    value = strJobConf,
                    binder = (stmt: PreparedStatement, idx: Int) => stmt.setObject(idx, jsonObj)
                  )

                  sql"""INSERT INTO deployment_config(job_id, name, config, target_id, dt_created)
                  VALUES($newJobId, ${jobReq.depId}, $jobConfigJson, ${jobReq.clusterId}, ${DateUtil.now})
                  """.update().apply()
                  newJobId

                case Some(id) => id
              }
            case Some(id) =>
              if (updateProject(workspaceId, id, jobReq)) id else 0
          }

        }
      }
    }

  override def updateDescription(jobId: Long, workspaceId: Long, text: String): Future[Boolean] = Future {
    blocking {
       DB localTx { implicit session =>
         sql"""UPDATE jobs SET  description = ${text} WHERE id = ${jobId} AND workspace_id = ${workspaceId}"""
           .update()
           .apply() == 1
      }
    }
  }

  override def addDeploymentConfig(workspaceId: Long, jobId: Long, clusterId: Long, request: NewDeploymentRequest ): Future[Long] = Future {
    blocking {
      DB localTx { implicit session =>

        val strJobConf = Json.toJson(request.jobConfig).toString()
        val jsonObj    = new PGobject()
        jsonObj.setType("json")
        jsonObj.setValue(strJobConf)

        val jobConfigJson = ParameterBinder(
          value = strJobConf,
          binder = (stmt: PreparedStatement, idx: Int) => stmt.setObject(idx, jsonObj)
        )

        sql"""INSERT INTO deployment_config(job_id, name, config, target_id, dt_created)
              VALUES($jobId, ${request.depId}, $jobConfigJson, $clusterId, ${DateUtil.now})
             """.updateAndReturnGeneratedKey().apply()
        jobId
      }
    }
  }

  override def saveDeploymentJob(orgId: Long, workspaceId: Long, job: DeploymentJob): Future[Long] = Future {
    blocking {
      DB localTx { implicit session =>

        val strJobConf = Json.toJson(job.jobConfig).toString()
        val jsonObj    = new PGobject()
        jsonObj.setType("json")
        jsonObj.setValue(strJobConf)

        val jobConfigJson = ParameterBinder(
          value = strJobConf,
          binder = (stmt: PreparedStatement, idx: Int) => stmt.setObject(idx, jsonObj)
        )

        val jobId = sql"""INSERT INTO jobs(name, job_type, description, workspace_id, dt_created, last_updated)
              VALUES(${job.jobConfig.jobName}, ${job.jobType.toString},'No description', $workspaceId, ${DateUtil.now}, ${DateUtil.now})
             """.updateAndReturnGeneratedKey().apply()

        sql"""INSERT INTO deployment_config(job_id, name, config, target_id, dt_created)
              VALUES($jobId, ${job.depId}, $jobConfigJson, ${job.clusterId}, ${DateUtil.now})
             """.updateAndReturnGeneratedKey().apply()

        jobId

      }
    }
  }

  override def listJobs(orgId: Long, workspaceId: Long): Future[Seq[JobListView]] = Future {
    blocking {
      DB localTx { implicit session =>
        val jobListView =
          sql"""SELECT j.id, j.name, j.job_type, j.last_updated, dh.id as run_id, dh.run_index,
                dh.status, dh.dt_started FROM jobs as j LEFT OUTER JOIN deployment_config as dc ON j.id = dc.job_id
                LEFT OUTER JOIN deployment_history as dh ON dc.id = dh.deployment_id
                WHERE workspace_id = ${workspaceId}"""
            .map { r =>
              JobViewsBuilder.buildJobInstanceView(r)
            }
            .list()
            .apply()

        val groupedJobByRuns =
          jobListView.sortWith((x, y) => x.jobLastUpdated > y.jobLastUpdated).groupBy(j => (j.jobId, j.name, j.jobType))
        //get jobs with no runs
        val newJobs = groupedJobByRuns
          .filter {
            case (l, records) =>
              (records.size == 1 && records.head.runId.isEmpty)
          }
          .mapValues {
            case head :: tl => head
          }
          .map {
            case ((id, name, jobType), record) => JobListView(id, name, Seq(), jobType)
          }
        val jobsWithHistory = groupedJobByRuns
          .filter {
            case (l, records) =>
              val s = records.size
              records.size >= 1 && records.exists(_.runId.isDefined)
          }
          .mapValues(runs => runs.filter(_.runId.isDefined).sortWith((x, y) => x.startedTs.get < y.startedTs.get))
          .map {
            case ((id, name, jobType), runs) =>
              val runsSnip = runs.map(r => JobInstanceSnip(r.runId.get, r.runIndex, r.status.get))
              val sortedRuns = runsSnip.sortWith((x,y) => x.runId < y.runId).zipWithIndex.map(snip => snip._1.copy(runSeqId = snip._2 + 1)).takeRight(10) 
              JobListView(id, name, sortedRuns.take(10), jobType)
          }
          .toSeq
        newJobs.toSeq ++ jobsWithHistory

      }
    }
  }

  /**
    * Job submission for a set of tasks
    * @param jobId
    * @param task
    * @return
    */
  override def addJobRun(jobId: Long, task: TaskMeta, maxJobRun: Int, tz: String): Future[Long] = Future {
    blocking {
      val newStatus = "waiting"
      val now       = ZonedDateTime.now(ZoneId.of(tz))
      DB localTx { implicit session =>
        //get the list of job runs for this job
        val jobRuns = sql"""select id, run_index from job_history where job_id = $jobId AND soft_deleted IS FALSE"""
          .map(r => (r.long("id"), r.long("run_index")))
          .list()
          .apply()

        //Delete the earliest job run
        if (jobRuns.size == maxJobRun) {
          val minJobRunId = jobRuns.map(_._1).min
          sql"""update job_history SET soft_deleted = TRUE where id = ${minJobRunId}""".update().apply()
        }

        val runIndex = if (jobRuns.nonEmpty) {
          jobRuns.map(_._2).max + 1
        } else 1

        sql"""insert into job_history(job_id, status, run_index, deployment_run_id, action_id, dt_started, last_updated)
               values(${jobId}, ${newStatus}, ${runIndex},${task.deploymentRunId.getOrElse(null)}, ${task.actionId
          .getOrElse(null)}, ${now}, ${now})"""
          .updateAndReturnGeneratedKey()
          .apply()

      }
    }
  }

  def getTaskHistory(jobId: Long, taskName: String): Future[Seq[TaskInstance]] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"""SELECT jh.id as run_id, jh.job_id, th.name, th.task_type,
               th.seq_id, th.status, th.start_time, th.end_time FROM job_history as jh
               INNER JOIN task_history as th ON th.job_run_id = jh.id WHERE jh.job_id = ${jobId} AND th.name = ${taskName}"""
          .map(rs =>
            TaskInstance(
              runId = None,
              jobRunId = rs.long("run_id"),
              name = rs.string("name"),
              taskType = rs.string("task_type"),
              seqId = rs.int("seq_id"),
              status = rs.string("status"),
              startTime = rs.zonedDateTimeOpt("start_time"),
              endTime = rs.zonedDateTimeOpt("end_time")
          ))
          .list()
          .apply()

      }
    }
  }

  override def find(jobId: Long): Future[Option[JobView]] = Future {
    blocking {
      DB autoCommit { implicit session =>
        val res =
          sql"select j.id, j.name, j.description, t.id as task_id, t.name, t.seq_id, t.task_type, t.active from jobs as j JOIN task_list as t where j.id = t.job_id"
            .map(rs => rs)
            .list()
            .apply()
        JobViewsBuilder.buildJobView(res)
      }
    }
  }

  override def getProjectByName(orgId: Long, name: String): Future[Option[Long]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"SELECT id from jobs where org_id = $orgId AND name = ${name.trim}".map(_.long("id")).single().apply()
      }
    }
  }

  override def listJobsByOrgs(orgIds: Seq[Long], jobType: JobType): Future[Seq[JobInfoId]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT name, id FROM jobs WHERE org_id IN (${orgIds}) AND job_type = ${jobType.toString}"""
          .map(r => JobInfoId(r.string("name"), r.long("id")))
          .list()
          .apply()
      }
    }
  }

  override def listJobRuns(jobId: Long): Future[Seq[JobRunInfo]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT id, run_index FROM job_history WHERE job_id = ${jobId} AND soft_deleted = false """
          .map(r => JobRunInfo(r.long("id"), r.int("run_index")))
          .list()
          .apply()
      }
    }
  }

  override def getJobSummary(jobId: Long): Future[Option[JobSummary]] = Future {
    blocking {
      DB localTx { implicit session =>
        val projectMeta = sql"SELECT name, description from jobs where id = $jobId"
          .map(x => (x.string("name"), x.string("description")))
          .single()
          .apply()
        sql"""SELECT id, name, description, AVG(avg_cpu_usage) as avg_cpu_usage, AVG(avg_memory_usage) as avg_memory_usage, AVG(runtime) as runtime
               FROM jobs LEFT OUTER JOIN
              job_run_stats ON jobs.id = job_run_stats.job_id WHERE jobs.id = ${jobId}
              GROUP BY id, name,description"""
          .map { r =>
            val avgRuntime = r.floatOpt("runtime").map(v => DateUtil.formatIntervalMillis(v.toLong)).getOrElse("--")
            val avgMem     = r.floatOpt("avg_memory_usage").map(v => f"${v * 100}%1.1f").getOrElse("--")
            val avgCPU     = r.floatOpt("avg_cpu_usage").map(v => f"${v * 100}%1.1f").getOrElse("--")
            JobSummary(jobId, r.string("name"), r.string("description"), JobStats(avgRuntime, avgMem, avgCPU))
          }
          .single()
          .apply()

      }
    }
  }

  override def fetchTimelimitExceededJobRuns(): Future[Seq[Long]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT dh.id FROM deployment_history as dh INNER JOIN deployment_config as dc ON dh.deployment_id = dc.id
             INNER JOIN jobs ON dc.job_id = jobs.id INNER JOIN workspaces as w ON jobs.workspace_id = w.id
             INNER JOIN usage_plans as up ON w.org_id = up.org_id
             WHERE EXTRACT(EPOCH FROM (NOW() - dh.dt_started)) > up.max_job_duration
             AND status = ${RunStatus.Running.toString}
             LIMIT 10"""
          .map(_.long("id"))
          .list().apply()
      }
    }
  }

  override def listJobsByCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Seq[JobListView]] = Future {
    blocking {
      DB localTx { implicit session =>
        val jobListView =
          sql"""select j.id, j.name, j.job_type, j.last_updated, jh.id as run_id, jh.run_index, jh.soft_deleted,
                jh.status, jh.dt_started FROM jobs as j LEFT OUTER JOIN job_history as jh
              ON j.id = jh.job_id where workspace_id = ${workspaceId} AND deployment_target = $clusterId"""
            .map { r =>
              JobViewsBuilder.buildJobInstanceView(r)
            }
            .list()
            .apply()
            .filter(!_.isSoftDeleted)

        val groupedJobByRuns =
          jobListView.sortWith((x, y) => x.jobLastUpdated > y.jobLastUpdated).groupBy(j => (j.jobId, j.name, j.jobType))
        //get jobs with no runs
        val newJobs = groupedJobByRuns
          .filter {
            case (_, records) =>
              (records.size == 1 && records.head.runId.isEmpty)
          }
          .mapValues {
            case head :: tl => head
          }
          .map {
            case ((id, name, jobType), _) => JobListView(id, name, Seq(), jobType)
          }
        val jobsWithHistory = groupedJobByRuns
          .filter {
            case (l, records) =>
              val s = records.size
              records.size >= 1 && records.find(_.runId.isDefined).nonEmpty
          }
          .mapValues(runs => runs.sortWith((x, y) => x.startedTs.get < y.startedTs.get))
          .map {
            case ((id, name, jobType), runs) =>
              val runsSnip = runs.map(r => JobInstanceSnip(r.runId.get, r.runIndex, r.status.get))
              JobListView(id, name, runsSnip, jobType)
          }
          .toSeq
        newJobs.toSeq ++ jobsWithHistory

      }
    }
  }

  case class JobWithRunsRow(jobId: Long,

                            jobType: JobType,
                            name: String,
                            runId: Option[Long],
                            status: Option[RunStatus],
                            startedTs: Option[Long])
  override def getJobValue(jobId: Long): Option[JobValue] = {

    DB readOnly { implicit session =>
      val result = sql"""select j.id as job_id, j.name, job_type, jh.id as job_run_id, jh.status, jh.dt_started
                    FROM jobs j LEFT JOIN job_history jh
                  ON j.id = jh.job_id
                  where j.id = ${jobId}
             """
        .map(rs =>
          JobWithRunsRow(
            rs.long("job_id"),

            JobType.withNameOpt(rs.string("job_type")),
            rs.string("name"),
            rs.longOpt("job_run_id"),
            rs.stringOpt("status").map(RunStatus.withNameOpt(_)),
            rs.zonedDateTimeOpt("dt_started").map(_.toEpochSecond)
        ))
        .list()
        .apply()

      val mapOfVals = result.groupBy(x => (x.jobId,  x.jobType, x.name))
      if (mapOfVals.size > 0) {
        val ((jobId,  jType, name), others) = mapOfVals.head
        val runs =
          if (others.map(x => x.runId).count(_.isEmpty) > 0) List()
          else {
            others.filter(_.runId.isDefined).map {
              case JobWithRunsRow(_, _, _, Some(runId), Some(status), Some(startedTs)) => JobRunSnip(runId, status, startedTs)
            }
          }
        Some(JobValue(jobId, jType, name, runs))
      } else None

    }
  }

  override def getJobRunDetail(jobId: Long, runId: Long): Future[String] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT jh.status as job_status, jh.job_id FROM job_history as jh
               WHERE jh.id = ${runId} AND jh.job_id = ${jobId}"""
          .map(rs => rs.string("job_status"))
          .single() 
          .apply()
          .getOrElse("unknown")

      }
    }
  }

  override def updateJobStatus(status: String, runId: Long, tz: String): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val now = ZonedDateTime.now(ZoneId.of(tz))
        val jobType =
          sql"""SELECT j.id, job_type from jobs as j INNER JOIN deployment_config as dc
                ON j.id = dc.job_id INNER JOIN deployment_history as dh ON dc.id = dh.deployment_id AND dh.id = ${runId}"""
          .map(r => (r.long("id"), r.string("job_type")))
          .single()
          .apply()

        val runStatus = RunStatus.withNameOpt(status)
        val updateCount = sql"update deployment_history set status = ${status}, dt_last_updated = ${ZonedDateTime.now()} where id = ${runId}"
          .update()
          .apply()
        jobType match {
          case Some((jobId, jtype)) if JobType.withNameOpt(jtype) == JobType.spark =>
            if (runStatus == RunStatus.Succeeded || runStatus == RunStatus.Completed || runStatus == RunStatus.Failed || runStatus == RunStatus.TimeLimitExceeded) {
              updateJobRunStats(jobId, runId, status, now)
            }
            if (runStatus == RunStatus.TimeLimitExceeded) {
              updateJobRunStats(jobId, runId, status, now)
              sql"UPDATE events_spark_app SET app_status = ${status} WHERE dep_run_id = ${runId}".update().apply()
            } else {
              sql"UPDATE events_spark_app SET app_status = ${status}, end_time=${now} WHERE dep_run_id = ${runId}".update().apply()
            }

          case None => {}

        }
        updateCount > 0
      }
    }
  }

  private def getMemoryAndCPUUsage(runId: Long, endTime: ZonedDateTime): Option[(Double, Double, Long, Double, Long)] = {
    DB readOnly { implicit session =>

      val startAndEndTime = sql"""SELECT dt_started FROM job_history WHERE id = ${runId}"""
        .map(r => r.dateTime("dt_started"))
        .single()
        .apply()
      startAndEndTime.flatMap {
        case (starTime) =>
          val runtime = endTime.toInstant.toEpochMilli - starTime.toInstant.toEpochMilli
          sql"""SELECT AVG(agg.cpu_used) as avg_cpu_used, AVG(agg.mem_used)/AVG(agg.cluster_mem) as avg_mem_used,
                      AVG(agg.cluster_mem) as cluster_mem, AVG(agg.mem_used) as avg_mem_used_val FROM
(SELECT AVG(cluster.executor_process_used) as cpu_used, SUM(cluster.mem_used) as mem_used, SUM(cluster.net_mem) as cluster_mem, cluster.with_time FROM(
SELECT tmp.with_time, tmp.address, SUM(tmp.jvm_cpu_process_usage) as executor_process_used, SUM(tmp.net_mem) as mem_used, AVG(max_mem) as net_mem FROM
(SELECT  ese.address,msr.executor_id,
		AVG(jvm_total_used) as net_mem, AVG(jvm_mem_size) as max_mem,
                  AVG(jvm_cpu_process_usage) as jvm_cpu_process_usage,
                  time_bucket_gapfill('5 seconds', ts) as with_time
                  FROM metrics_spark_runtime as msr
                  LEFT JOIN events_spark_executors as ese ON msr.job_run_id = ese.job_run_id AND msr.executor_id = ese.executor_id
                  WHERE msr.job_run_id = ${runId} AND ts BETWEEN ${starTime} AND ${endTime}
                  GROUP BY msr.executor_id, address,with_time order by with_time) as tmp
                  GROUP BY address, with_time
		having AVG(tmp.jvm_cpu_process_usage) IS NOT NULL) as cluster
		GROUP BY cluster.with_time
		) as agg"""
            .map(
              r =>
                (r.doubleOpt("avg_cpu_used").getOrElse(0D),
                 r.doubleOpt("avg_mem_used").getOrElse(0D),
                 r.longOpt("cluster_mem").getOrElse(0L),
                 r.doubleOpt("avg_mem_used_val").getOrElse(0D),
                 runtime))
            .single()
            .apply()
      }

    }
  }

  private def updateJobRunStats(jobId: Long, runId: Long, status: String, endTime: ZonedDateTime): Boolean = {
    DB localTx { implicit session =>
      getMemoryAndCPUUsage(runId, endTime) match {
        case None => false
        case Some((avgCpuUsed, avgMemUsed, cluserMem, clusterMemUsed, runtime)) =>
          val updateStatus = sql"""INSERT INTO job_run_stats(job_id, run_id, runtime, status, avg_memory_usage, avg_cpu_usage, cluster_mem, cluster_mem_used)
           VALUES(${jobId}, ${runId}, ${runtime}, ${status}, ${avgMemUsed}, ${avgCpuUsed}, ${cluserMem}, ${clusterMemUsed})
           ON CONFLICT(run_id)
           DO UPDATE SET
           runtime = ${runtime},
           status = ${status},
           avg_memory_usage = ${avgMemUsed},
           avg_cpu_usage = ${avgCpuUsed},
           cluster_mem = ${cluserMem},
           cluster_mem_used = ${clusterMemUsed}
           """
            .update()
            .apply() == 1

          updateStatus

      }
    }
  }

  override def updateTaskStatus(orgId: Long, jobId: Long, jobRunId: Long, seqId: Int, status: String): Future[Boolean] = Future {
    blocking {
      DB autoCommit { implicit session =>
        val updateCnt = status match {
          case s if (s.equals("completed") || s.equals("passed") || s.equals("failed") || s.equals("succeeded")) =>
            sql"update task_history set status = ${status} , end_time = ${ZonedDateTime
              .now()} where job_run_id = ${jobRunId} AND seq_id = ${seqId}"
              .update()
              .apply()
          case s if (s.equals("running")) =>
            sql"update task_history set status = ${status} , start_time = ${ZonedDateTime
              .now()} where job_run_id = ${jobRunId} AND seq_id = ${seqId}"
              .update()
              .apply()
          case _ =>
            sql"update task_history set status = ${status} where job_run_id = ${jobRunId} AND seq_id = ${seqId}"
              .update()
              .apply()
        }
        updateCnt == 1
      }
    }
  }

  override def remove(jobId: Long): Future[Boolean] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"delete from jobs where id = ${jobId}"
          .update()
          .apply() == 1
      }
    }
  }

  override def saveJVMException(runId: Long, attemptId: String, e: JVMException, errJson: String): Future[Boolean] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"""INSERT INTO errors(job_run_id,attempt_id, cause, severity_level, error_source, error_object, dt_created)
             VALUES(${runId}, ${attemptId}, ${e.message},${e.level.toString}, ${ErrorSource.SOURCE_CODE.toString}, ${errJson}, ${DateUtil.now})"""
          .update()
          .apply() > 0
      }

    }
  }

  override def getError(runId: Long, attemptId: String): Future[Seq[String]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT error_object FROM errors WHERE job_run_id = ${runId} AND attempt_id = ${attemptId}"""
          .map(r => r.string("error_object"))
          .list()
          .apply()
      }

    }
  }
}

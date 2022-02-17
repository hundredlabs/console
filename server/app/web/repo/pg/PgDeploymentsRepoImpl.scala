package web.repo.pg

import java.sql.PreparedStatement
import java.time.ZonedDateTime

import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{
  ClusterStatus,
  DeploymentAction,
  DeploymentActionLog,
  DeploymentRun,
  DeploymentRunInstance,
  DeploymentRunResponse,
  DeploymentRunResult,
  DeploymentView,
  JobConfig,
  JobType,
  NewDeploymentRequest,
  NewDeploymentRun,
  RunStatus,
  TriggerMethod
}
import com.gigahex.commons.models.TriggerMethod.TriggerMethod
import com.google.cloud.dataproc.v1.OrderedJob.JobTypeCase
import web.models.{DeploymentJsonFormat, DeploymentRunHistory, JobFormats}
import web.repo.DeploymentsRepo
import web.utils.DateUtil
import org.postgresql.util.PGobject
import play.api.libs.json.Json
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future, blocking}

class PgDeploymentsRepoImpl(blockingEC: ExecutionContext) extends DeploymentsRepo with JobFormats {
  implicit val ec = blockingEC

  override def upsertDeploymentRun(workspaceId: Long, run: NewDeploymentRun): Future[Option[Long]] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val optActions =
            sql"""select dc.id as deployment_id from deployment_config as dc INNER JOIN jobs ON dc.job_id = jobs.id
                 INNER JOIN workspaces as w ON jobs.workspace_id = w.id
                 WHERE dc.id = ${run.deploymentId} AND w.id = ${workspaceId}"""
              .map(r => r.long("deployment_id"))
              .single()
              .apply()

          optActions.map { _ =>
            val count = sql"""select count(*) as count FROM deployment_history where deployment_id = ${run.deploymentId}"""
              .map(_.long("count"))
              .single()
              .apply()
              .get

            val deploymentRunId =
              sql"""insert into deployment_history(deployment_id, status,run_index, trigger_method, dt_last_updated, dt_started)
             VALUES(${run.deploymentId}, ${run.runStatus.toString}, ${count + 1}, ${run.triggerMethod.toString}, ${DateUtil.now}, ${DateUtil.now})"""
                .updateAndReturnGeneratedKey()
                .apply()

            deploymentRunId
          }

        }
      }
    }.recover {
      case e: Exception =>
        e.printStackTrace()
        None
    }
  case class DBRowDeploymentConfig(agentId: Option[String],
                                   agentName: Option[String],
                                   status: Option[ClusterStatus],
                                   configName: String,
                                   configId: Long,
                                   runId: Option[Long],
                                   runIndex: Option[Long],
                                   runStatus: Option[RunStatus])
  override def listDeployments(workspaceId: Long, jobId: Long): Future[Seq[DeploymentView]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val depConfig = sql"""select cl.id as cluster_id, cl.name as cluster_name, cl.status as cluster_status,
             dc.name, dc.id as dep_id, dh.id as run_id, dh.run_index,  dh.status as run_status from clusters cl RIGHT OUTER JOIN
             deployment_config dc ON cl.id = dc.target_id LEFT OUTER JOIN
             deployment_history dh ON dc.id = dh.deployment_id INNER JOIN jobs ON jobs.id = dc.job_id WHERE
             jobs.workspace_id = $workspaceId AND dc.job_id = ${jobId}
             order by case when dh.id is null then 1 else 0 end, dh.id"""
          .map { result =>
            DBRowDeploymentConfig(
              result.stringOpt("cluster_id"),
              result.stringOpt("cluster_name"),
              result.stringOpt("cluster_status").map(x => ClusterStatus.withNameOpt(x)),
              result.string("name"),
              result.long("dep_id"),
              result.longOpt("run_id"),
              result.longOpt("run_index"),
              result.stringOpt("run_status").map(RunStatus.withNameOpt(_))
            )
          }
          .list()
          .apply()

        val depConfigWithNoRuns = depConfig.filter(x => x.runId.isEmpty).map { x =>
          DeploymentView(x.configId, x.configName, x.agentId, x.agentName, x.status, Seq.empty[DeploymentRun])
        }

        val depConfigWithRuns = depConfig.filter(x => x.runId.isDefined).groupBy(_.configId).map {
          case (l, configs) =>
            val runs =
              configs
                .map(c => DeploymentRun(c.runId.get, c.runIndex.get, c.runStatus.get))
                .takeRight(10)
                .sortWith((x, y) => x.runId > y.runId)
            DeploymentView(l, configs.head.configName, configs.head.agentId, configs.head.agentName, configs.head.status, runs)
        }

        depConfigWithNoRuns ++ depConfigWithRuns

      }
    }

  }

  override def delete(workspaceId: Long, jobId: Long, deploymentId: Long): Future[Boolean] = Future {
    DB localTx { implicit session =>
      sql"""SELECT id from jobs where id = ${jobId} AND workspace_id = ${workspaceId}"""
        .map(_.long("id"))
        .single().apply() match {
        case None => false
        case Some(_) =>
          sql"""delete from deployment_config where id = ${deploymentId} AND job_id = $jobId"""
            .update()
            .apply() == 1
      }

    }
  }

  def getDeploymentConfig(orgId: Long, projectId: Long, deploymentId: Long): Future[Option[NewDeploymentRequest]] = Future {
    blocking {
      DB readOnly { implicit session =>
        ???
      }
    }
  }

  case class DBRowDeploymentRun(deploymentConfigId: Long,
                                runIndex: Int,
                                triggerMethod: TriggerMethod,
                                depRunStatus: RunStatus,
                                actions: Seq[DeploymentAction],
                                actionId: String,
                                actionName: String,
                                tsStarted: Option[ZonedDateTime],
                                tsLastUpdated: ZonedDateTime,
                                index: Int,
                                actionStatus: RunStatus,
                                jobRunId: Option[Long],
                                jobId: Option[Long])

  override def updateDeployment(jobId: Long, workspaceId: Long, deploymentId: Long, jobConfig: JobConfig): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val strJobConf = Json.toJson(jobConfig).toString()
        val jsonObj    = new PGobject()
        jsonObj.setType("json")
        jsonObj.setValue(strJobConf)

        val jobConfigJson = ParameterBinder(
          value = strJobConf,
          binder = (stmt: PreparedStatement, idx: Int) => stmt.setObject(idx, jsonObj)
        )
        sql"""UPDATE deployment_config SET config = ${jobConfigJson} WHERE job_id = ${jobId} AND id = ${deploymentId} """
          .update()
          .apply() > 0
      }
    }
  }

  override def getDeploymentResult(orgId: Long, deploymentRunId: Long): Future[Option[DeploymentRunResult]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val deploymentActions = sql"""select df.id, actions, dh.status as dep_run_status,dh.run_index, dh.trigger_method,
                               dh.id as deployment_run_id, dal.action_id, jh.id as job_run_id, jh.job_id,
                              dal.status as action_status, dal.dt_started, dal.dt_last_updated, dal.action_name, dal.seq from deployment_config df
              INNER JOIN deployment_history dh ON df.id = dh.deployment_id
               INNER JOIN deployment_action_log dal ON dal.deployment_run_id = dh.id
               LEFT OUTER JOIN job_history jh ON dal.deployment_run_id = jh.deployment_run_id AND dal.action_id = jh.action_id
             WHERE df.org_id = ${orgId}  AND dh.id = ${deploymentRunId}"""
          .map { r =>
            DBRowDeploymentRun(
              deploymentConfigId = r.long("id"),
              runIndex = r.int("run_index"),
              triggerMethod = TriggerMethod.withNameOpt(r.string("trigger_method")),
              depRunStatus = RunStatus.withNameOpt(r.string("dep_run_status")),
              actions = Seq.empty,
              actionId = r.string("action_id"),
              actionName = r.string("action_name"),
              tsStarted = r.zonedDateTimeOpt("dt_started"),
              tsLastUpdated = r.zonedDateTime("dt_last_updated"),
              index = r.int("seq"),
              actionStatus = RunStatus.withNameOpt(r.string("action_status")),
              jobRunId = r.longOpt("job_run_id"),
              jobId = r.longOpt("job_id")
            )
          }
          .list()
          .apply()
        if (deploymentActions.size > 0) {
          val runDetails = deploymentActions
            .groupBy(_.deploymentConfigId)
            .map {
              case (configId, runs) =>
                val actionLogs = runs.map { run =>
                  val duration = run.actionStatus match {

                    case com.gigahex.commons.models.RunStatus.Running =>
                      DateUtil.timeElapsed(run.tsStarted.getOrElse(ZonedDateTime.now()), None)
                    case com.gigahex.commons.models.RunStatus.Skipped =>
                      "Skipped"
                    case com.gigahex.commons.models.RunStatus.NotRunning =>
                      "Not Started"
                    case com.gigahex.commons.models.RunStatus.Completed =>
                      DateUtil.timeElapsed(run.tsStarted.getOrElse(ZonedDateTime.now()), Some(run.tsLastUpdated))
                    case com.gigahex.commons.models.RunStatus.NotStarted =>
                      "Not Started"
                    case com.gigahex.commons.models.RunStatus.Waiting =>
                      "Not Started"
                    case com.gigahex.commons.models.RunStatus.TimeLimitExceeded =>
                      "Unknown"
                    case com.gigahex.commons.models.RunStatus.Unknown =>
                      "Unknown"
                    case _ =>
                      DateUtil.timeElapsed(run.tsStarted.getOrElse(ZonedDateTime.now()), Some(run.tsLastUpdated))
                  }
                  DeploymentActionLog(run.actionName, run.index, run.actionId, run.actionStatus, duration, run.jobRunId, run.jobId)
                }
                val runtime = runs
                DeploymentRunResult(
                  depConfigId = configId,
                  runIndex = runs.head.runIndex,
                  triggerMethod = runs.head.triggerMethod,
                  actionResults = actionLogs,
                  status = runs.head.depRunStatus
                )
            }
            .head
          Some(runDetails)
        } else {
          None
        }

      }
    }
  }

  override def listDeploymentHistory(workspaceId: Long, projectId: Long, deploymentId: Long): Future[Option[DeploymentRunHistory]] =
    Future {
      blocking {
        DB readOnly { implicit session =>
          sql"""select
             dh.status, dh.dt_started, dc.name, dh.id as run_id, dh.dt_last_updated, dh.run_index, dh.trigger_method from deployment_history dh
             INNER JOIN deployment_config dc ON dh.deployment_id = dc.id WHERE
             dc.id = ${deploymentId} AND dc.job_id = ${projectId} order by dh.run_index desc limit 20"""
            .map { r =>
              val status    = RunStatus.withNameOpt(r.string("status"))
              val startTime = r.zonedDateTime("dt_started")

              val runtime = status match {
                case com.gigahex.commons.models.RunStatus.Completed =>
                  DateUtil.timeElapsed(startTime, Some(r.zonedDateTime("dt_last_updated")))
                case com.gigahex.commons.models.RunStatus.Failed =>
                  DateUtil.timeElapsed(startTime, Some(r.zonedDateTime("dt_last_updated")))
                case _ => "--"

              }
              (r.string("name"),
               DeploymentRunInstance(
                 r.long("run_id"),
                 r.int("run_index"),
                 status,
                 r.string("trigger_method") + " " + DateUtil.timeElapsed(startTime, None) + " ago",
                 runtime
               ))
            }
            .list()
            .apply()
            .groupBy(_._1)
            .headOption
            .map {
              case (name, runs) => DeploymentRunHistory(name, runs.map(_._2))
            }
        }
      }
    }

  override def getDeployment(orgId: Long, agentId: String, deploymentRunId: Long): Future[Option[NewDeploymentRequest]] = Future {
    blocking {
      DB readOnly { implicit session =>
        ???

      }
    }
  }

  override def getDeploymentRunInstance(workspaceId: Long, clusterId: Long): Future[Option[DeploymentRunResponse]] = Future {
    blocking {
      DB localTx { implicit session =>
       sql"""UPDATE clusters SET status = ${RunStatus.Running.toString} , last_updated = ${DateUtil.now}
             WHERE id = $clusterId AND workspace_id = $workspaceId"""
          .update().apply()

        sql"""SELECT dh.id as deployment_run_id, jobs.job_type, dc.config, dc.name FROM deployment_history as dh
             INNER JOIN deployment_config as dc ON dh.deployment_id = dc.id
             INNER JOIN jobs ON dc.job_id = jobs.id
             WHERE jobs.workspace_id = $workspaceId AND dc.target_id = $clusterId AND dh.status = ${RunStatus.Waiting.toString}"""
          .map { r =>
            DeploymentRunResponse(
              runId = r.long("deployment_run_id"),
              request = NewDeploymentRequest(
                clusterId = clusterId,
                jobType = JobType.withName(r.string("job_type")),
                depId = r.string("name"),
                jobConfig = Json.parse(r.string("config")).as[JobConfig]
              )
            )
          }
          .list()
          .apply()
          .headOption

      }
    }
  }

  override def getDepoymentByName(orgId: Long, depName: String, project: String): Future[Option[NewDeploymentRequest]] = Future {
    blocking {
      DB readOnly { implicit session =>
        ???
      }
    }
  }

  private def getMemoryAndCPUUsage(runId: Long, endTime: ZonedDateTime): Option[(Double, Double, Long, Double, Long)] = {
    DB readOnly { implicit session =>

      val startAndEndTime = sql"""SELECT dt_started FROM deployment_history WHERE id = ${runId}"""
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
                  LEFT JOIN events_spark_executors as ese ON msr.dep_run_id = ese.dep_run_id AND msr.executor_id = ese.executor_id
                  WHERE msr.dep_run_id = ${runId} AND ts BETWEEN ${starTime} AND ${endTime}
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

  override def updateDeploymentRun(runId: Long, status: RunStatus): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        status match {
          case com.gigahex.commons.models.RunStatus.Starting =>
            sql"""update deployment_history set status = ${status.toString} , dt_started = ${DateUtil.now} WHERE id = ${runId}"""
              .update()
              .apply() == 1

          case _ =>
            val updateCount = sql"""update events_spark_app set app_status = ${status.toString} where dep_run_id = $runId"""
              .update()
              .apply()
            if(updateCount == 1){
              val jobId =
                sql"""select jobs.id from jobs INNER JOIN deployment_config as dc
                  ON dc.job_id = jobs.id INNER JOIN deployment_history as dh ON dh.deployment_id = dc.id
                  WHERE dh.id = $runId"""
                .map(_.long("id"))
                .single().apply().get

              if (status == RunStatus.Succeeded || status == RunStatus.Completed || status == RunStatus.Failed || status == RunStatus.TimeLimitExceeded) {
                updateJobRunStats(jobId, runId, status.toString, DateUtil.now)
              }
            }
            sql"""update deployment_history set status = ${status.toString} , dt_last_updated = ${DateUtil.now} WHERE id = ${runId}"""
              .update()
              .apply() == 1
        }
      }
    }
  }

  override def updateActionStatus(deploymentWorkId: Long, actionId: String, status: RunStatus, logs: Seq[String]): Future[Boolean] =
    Future {
      blocking {
        DB localTx { implicit session =>
          status match {
            case com.gigahex.commons.models.RunStatus.Running =>
              sql"""update deployment_action_log set status = ${status.toString}, dt_started = ${DateUtil.now}
             WHERE deployment_run_id = ${deploymentWorkId} AND action_id = ${actionId}"""
                .update()
                .apply() == 1
            case _ =>
              sql"""update deployment_action_log set status = ${status.toString}, dt_last_updated = ${DateUtil.now}
             WHERE deployment_run_id = ${deploymentWorkId} AND action_id = ${actionId}"""
                .update()
                .apply() == 1
          }
        }
      }
    }

}

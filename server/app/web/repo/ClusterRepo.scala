package web.repo

import com.gigahex.commons.models.ClusterProvider.ClusterProvider
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{
  ClusterMiniView,
  ClusterNode,
  ClusterPingResponse,
  ClusterProvider,
  ClusterRegistrationResponse,
  ClusterState,
  ClusterStatus,
  ClusterView,
  JobType,
  NewCluster,
  RegisterAgent,
  RunStatus,
  TriggerMethod
}
import web.models.cluster.LocalSparkConfig
import web.models.{
  ClusterDeploymentHistory,
  ClusterMetric,
  ClusterUsage,
  ContainerAppDef,
  ContainerSandbox,
  DBSandboxCluster,
  DistributedService,
  LastClusterPing,
  NewSandboxCluster,
  OrgUsagePlan,
  SandboxCluster,
  ServerHost,
  ServiceComponent,
  ServiceOption,
  ServiceOptionId,
  ServicePort
}
import web.utils.DateUtil
import play.api.libs.json.{Json, OFormat}
import scalikejdbc._
import utils.auth.RandomGenerator

import scala.concurrent.{ExecutionContext, Future, blocking}

trait ClusterRepo {

  val ec: ExecutionContext

  def listClusterViewByStatus(status: ClusterStatus): Future[Seq[ClusterView]]

  def checkUsage(orgId: Long, provider: ClusterProvider, plan: OrgUsagePlan): Future[Boolean]

  def listClusterStatusByWorkspace(workspaceId: Long, status: ClusterStatus*): Future[Seq[ClusterMiniView]]

  def getOrgId(workspaceId: Long): Future[Option[Long]]

  def listClustersByProvider(workspaceId: Long, provider: String): Future[Seq[ClusterMiniView]]

  def clusterExists(workspaceId: Long, name: String, provider: String): Future[Option[Long]]

  def listDeploymentHistory(workspaceId: Long, clusterId: Long): Future[Seq[ClusterDeploymentHistory]]

  def listLocalHostByWorkspace(workspaceId: Long): Future[Seq[ServerHost]]

  def saveSandboxCluster(workspaceId: Long, sandbox: NewSandboxCluster): Future[Long]

  def listLastPingTimestamps(status: ClusterStatus): Future[Seq[LastClusterPing]]

  def inactivateCluster(clusterId: Long): Future[Boolean]

  def save(request: RegisterAgent, orgId: Long): Future[ClusterRegistrationResponse]

  def getClusterPackages(name: String): Future[Seq[String]]

  def listSandboxVersions(): Future[Seq[DBSandboxCluster]]

  def orgUsagePlan(orgId: Long): Future[Option[OrgUsagePlan]]

  def fetchClusterMetric(workspaceId: Long, clusterId: Long): Future[Option[ClusterMetric]]

  def addCluster(workspaceId: Long, request: NewCluster, sandboxCluster: Option[NewSandboxCluster]): Future[Long]

  def saveClusterState(workspaceId: Long, state: ClusterState): Future[Boolean]

  def listClusterIds(): Future[Map[String, Seq[Long]]]

  def listAllClusters(orgId: Long, workspaceId: Long): Future[Seq[ClusterView]]

  def updateClusterStatus(orgId: Long, clusterId: String, status: ClusterStatus): Future[ClusterPingResponse]

  def removeCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Boolean]

  def updateCluster(workspaceId: Long, clusterId: Long, status: ClusterStatus, detail: String): Future[Boolean]

  def getClusterUsage(orgId: Long): Future[Option[ClusterUsage]]

  def updateClusterProcess(clusterId: Long, name: String, status: RunStatus, detail: String, processId: Option[Long]): Future[Boolean]

  def listLocalSparkClusters(): Future[Seq[LocalSparkConfig]]

}

class ClusterRepoImpl(blockingEC: ExecutionContext) extends ClusterRepo {
  implicit val ec: ExecutionContext                                       = blockingEC
  private implicit val distributedServiceFmt: OFormat[DistributedService] = Json.format[DistributedService]
  private implicit val serviceOptFmt                                      = Json.format[ServiceOption]
  private implicit val sandboxClusterFmt: OFormat[SandboxCluster]         = Json.format[SandboxCluster]

  override def listClusterViewByStatus(status: ClusterStatus): Future[Seq[ClusterView]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select id, name, provider, provider_cluster_id, dt_added, status FROM clusters where status = ${status.toString}"""
          .map { r =>
            ClusterView(
              cid = r.long("id"),
              name = r.string("name"),
              serviceName = r.string("service_name"),
              serviceVersion = r.string("service_version"),
              clusterId = r.string("provider_cluster_id"),
              provider = ClusterProvider.withName(r.string("provider")),
              status = ClusterStatus.withNameOpt(r.string("status")),
              created = DateUtil.timeElapsed(r.zonedDateTime("dt_added"), None) + " ago",
            )
          }
          .list()
          .apply()

      }
    }
  }

  private def checkForLocalCluster(orgId: Long, plan: OrgUsagePlan): Boolean = DB readOnly { implicit session =>
    val existingClusters = sql"""select count(cl.id) as count FROM clusters AS cl INNER JOIN workspaces as w ON cl.workspace_id = w.id
           WHERE provider IN(${ClusterProvider.SANDBOX.toString}, ${ClusterProvider.SANDBOX_DOCKER.toString})
           AND status != ${ClusterStatus.DELETED.toString}
          AND w.org_id = $orgId"""
      .map(_.int("count"))
      .single()
      .apply()
      .getOrElse(0)
    existingClusters < plan.maxLocalClusters
  }

  private def checkForRemoteCluster(orgId: Long, plan: OrgUsagePlan): Boolean = DB readOnly { implicit session =>
    val existingClusters = sql"""select count(cl.id) as count FROM clusters AS cl INNER JOIN workspaces as w ON cl.workspace_id = w.id
           where status IN(${ClusterProvider.ONPREM_SPARK_STANDALONE.toString}, ${ClusterProvider.ONPREM_K8S.toString},
            ${ClusterProvider.ONPREM_YARN.toString})
          AND w.org_id = $orgId"""
      .map(_.int("count"))
      .single()
      .apply()
      .getOrElse(0)
    existingClusters < plan.maxRemoteClusters
  }

  override def checkUsage(orgId: Long, provider: ClusterProvider, plan: OrgUsagePlan): Future[Boolean] = Future {
    blocking {
      provider match {
        case com.gigahex.commons.models.ClusterProvider.SANDBOX                 => checkForLocalCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.SANDBOX_DOCKER          => checkForLocalCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.SANDBOX_K8s             => checkForLocalCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.ONPREM_SPARK_STANDALONE => checkForRemoteCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.ONPREM_K8S              => checkForRemoteCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.ONPREM_YARN             => checkForRemoteCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.AWS_EMR                 => checkForRemoteCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.GCP_DATAPROC            => checkForRemoteCluster(orgId, plan)
        case com.gigahex.commons.models.ClusterProvider.DATABRICKS              => checkForRemoteCluster(orgId, plan)
      }
    }
  }

  override def listClusterStatusByWorkspace(workspaceId: Long, status: ClusterStatus*): Future[Seq[ClusterMiniView]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select id, name, provider, provider_cluster_id FROM clusters where status IN(${status
          .map(_.toString)}) AND workspace_id = $workspaceId"""
          .map { r =>
            ClusterMiniView(
              id = r.long("id"),
              name = r.string("name"),
              providerClusterId = r.string("provider_cluster_id"),
              provider = ClusterProvider.withName(r.string("provider"))
            )
          }
          .list()
          .apply()
      }
    }
  }

  override def getOrgId(workspaceId: Long): Future[Option[Long]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT org_id FROM workspaces where id = $workspaceId"""
          .map(_.long("org_id"))
          .single()
          .apply()
      }
    }
  }

  override def listClustersByProvider(workspaceId: Long, provider: String): Future[Seq[ClusterMiniView]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select id, name, provider, provider_cluster_id FROM clusters where provider = $provider AND workspace_id = $workspaceId"""
          .map { r =>
            ClusterMiniView(
              id = r.long("id"),
              name = r.string("name"),
              providerClusterId = r.string("provider_cluster_id"),
              provider = ClusterProvider.withName(r.string("provider"))
            )
          }
          .list()
          .apply()
      }
    }
  }

  override def clusterExists(workspaceId: Long, name: String, provider: String): Future[Option[Long]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select id FROM clusters where provider = $provider AND name = $name AND workspace_id = $workspaceId"""
          .map(_.long("id"))
          .single()
          .apply()
      }
    }
  }

  override def listDeploymentHistory(workspaceId: Long, clusterId: Long): Future[Seq[ClusterDeploymentHistory]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT dc.id as deployment_id, jobs.name as job_name, jobs.job_type, dh.id as run_id, dc.job_id, dh.status, dh.dt_started, dh.trigger_method, dh.dt_last_updated, dc.name
              FROM deployment_config as dc INNER JOIN deployment_history as dh
               ON dc.id = dh.deployment_id INNER JOIN jobs ON dc.job_id = jobs.id WHERE dc.target_id = $clusterId
               AND jobs.workspace_id = $workspaceId"""
          .map { r =>
            var internalJobRunId: Option[String] = None
            JobType.withName(r.string("job_type")) match {
              case com.gigahex.commons.models.JobType.spark =>
                val runId = r.long("run_id")
                internalJobRunId = sql"""SELECT app_id FROM events_spark_app WHERE dep_run_id = $runId"""
                  .map(_.string("app_id"))
                  .list()
                  .apply()
                  .headOption

              case _ =>
                internalJobRunId = None
            }

            ClusterDeploymentHistory(
              deploymentName = r.string("name"),
              jobName = r.string("job_name"),
              depId = r.long("deployment_id"),
              jobId = r.long("job_id"),
              deploymentRunId = r.long("run_id"),
              triggerMethod = TriggerMethod.withName(r.string("trigger_method")),
              status = r.string("status"),
              started = DateUtil.timeElapsed(r.dateTime("dt_started"), None) + " ago",
              runtime = DateUtil.timeElapsed(r.dateTime("dt_started"), r.dateTimeOpt("dt_last_updated")),
              internalJobRunId = internalJobRunId
            )
          }
          .list()
          .apply()
      }
    }
  }

  override def listLocalHostByWorkspace(workspaceId: Long): Future[Seq[ServerHost]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val hosts =
          sql"""SELECT sh.id, sh.name, sh.host_ip, sh.status, c.service_version, c.service_name, sh.dt_added, c.provider FROM server_hosts sh
               INNER JOIN clusters c ON sh.cluster_id = c.id
              WHERE c.status <> ${ClusterStatus.DELETED.toString}"""
            .map { row =>
              ServerHost(
                id = row.long("id"),
                name = row.string("name"),
                provider = ClusterProvider.withName(row.string("provider")),
                components = Seq(ServiceComponent(row.string("service_name"), row.string("service_version"))),
                status = ClusterStatus.withName(row.string("status")),
                dtAddedEpoch = row.dateTime("dt_added").toEpochSecond
              )
            }
            .list()
            .apply()

        val withComponents = hosts.groupBy(_.name).map {
          case (_, serverHosts) =>
            val components = serverHosts.flatMap(_.components)
            serverHosts.head.copy(components = components)
        }
        withComponents.toSeq

      }
    }
  }

  override def saveSandboxCluster(workspaceId: Long, sandbox: NewSandboxCluster): Future[Long] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT id FROM clusters WHERE workspace_id = $workspaceId AND name = ${sandbox.name} AND status != ${ClusterStatus.DELETED.toString}"""
          .map(_.long("id"))
          .single()
          .apply() match {
          case None =>
            sql"""INSERT INTO clusters(name, provider, provider_cluster_id, status, workspace_id, dt_added, last_updated,
             sandbox_id, service_option_id)
             VALUES(${sandbox.name}, ${ClusterProvider.SANDBOX.toString}, ${RandomGenerator
              .generate()}, ${ClusterStatus.INACTIVE.toString}, $workspaceId,
             ${DateUtil.now}, ${DateUtil.now}, ${sandbox.sandboxId}, ${sandbox.serviceOptionId})"""
              .updateAndReturnGeneratedKey()
              .apply()
          case Some(v) => v
        }
      }
    }
  }

  override def listLastPingTimestamps(status: ClusterStatus): Future[Seq[LastClusterPing]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT id, last_updated FROM clusters WHERE status = ${status.toString}"""
          .map(r => LastClusterPing(r.long("id"), r.dateTime("last_updated")))
          .list()
          .apply()
      }
    }
  }

  override def inactivateCluster(clusterId: Long): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""update clusters set status = ${ClusterStatus.INACTIVE.toString} WHERE id = $clusterId"""
          .update()
          .apply() == 1
      }
    }
  }

  override def save(request: RegisterAgent, orgId: Long): Future[ClusterRegistrationResponse] = Future {
    blocking {
      DB localTx { implicit session =>
        val optClusterId = sql"""select id from agents where id = ${request.agentId} and org_id = $orgId"""
          .map(_.string("id"))
          .single()
          .apply()

        optClusterId match {
          case None =>
            val insertCount = sql"""INSERT INTO agents(id, org_id, name, status, dt_registered, dt_ping)
              VALUES(${request.agentId}, $orgId, ${request.name},${ClusterStatus.STARTING.toString},
              ${DateUtil.now}, ${DateUtil.now})"""
              .update()
              .apply()
            ClusterRegistrationResponse(insertCount == 1)

          case Some(_) =>
            //Update the status of the agent
            val updateCount =
              sql"""update agents set status = ${ClusterStatus.RUNNING.toString}, dt_ping = ${DateUtil.now} where id = ${request.agentId}
                 and org_id = $orgId""".update().apply()
            ClusterRegistrationResponse(updateCount == 1, "Cluster status updated")
        }
      }
    }
  }

  override def getClusterPackages(name: String): Future[Seq[String]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select version FROM package_repository where name = ${name} AND is_deprecated = false"""
          .map(_.string("version"))
          .list()
          .apply()
      }
    }
  }

  override def listSandboxVersions(): Future[Seq[DBSandboxCluster]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select id, version, service_options FROM sandbox_cluster"""
          .map { r =>
            val serviceOptions = Json.parse(r.string("service_options")).as[Seq[ServiceOption]]
            DBSandboxCluster(r.long("id"), SandboxCluster(r.string("version"), serviceOptions))
          }
          .list()
          .apply()
      }
    }
  }

  override def orgUsagePlan(orgId: Long): Future[Option[OrgUsagePlan]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select name, max_local_clusters_count, max_remote_cluster_count, max_remote_cluster_size,
               max_jobs_count, max_workspace_count, org_id FROM usage_plans WHERE org_id = $orgId"""
          .map { r =>
            OrgUsagePlan(
              name = r.string("name"),
              maxLocalClusters = r.int("max_local_clusters_count"),
              maxRemoteClusters = r.int("max_remote_cluster_count"),
              maxJobsCount = r.int("max_jobs_count"),
              maxRemoteClusterSize = r.int("max_remote_cluster_size"),
              maxWorkspaceCount = r.int("max_workspace_count")
            )
          }
          .single()
          .apply()
      }
    }
  }

  private def getContainerConfig(serviceOptions: Seq[ServiceOption],
                                 version: String,
                                 optionName: String,
                                 addOns: Seq[String]): ContainerSandbox = {
    val serviceOption = serviceOptions.find(_.id.equalsIgnoreCase(optionName)).get
    val imageName     = "gigahex/" + serviceOption.services.map(_.name).sortBy(_.charAt(0)).mkString("-") + ":" + version
    val apps = serviceOption.services.map { ds =>
      ContainerAppDef(ds.name, ds.version, ServiceOptionId.getServicePorts(ServiceOptionId.withName(serviceOption.id), ds.name))
    }
    ContainerSandbox(imageName, apps, addOns)
  }

  override def fetchClusterMetric(workspaceId: Long, clusterId: Long): Future[Option[ClusterMetric]] = Future {
    blocking {
      DB readOnly { implicit session =>
        var sandboxCluster: Option[SandboxCluster] = None
        val containerSandbox =
          sql"""SELECT service_option_id, sc.version, sc.service_options, cl.extra_services  FROM clusters as cl INNER JOIN
             sandbox_cluster as sc ON cl.sandbox_id = sc.id WHERE cl.id = $clusterId and workspace_id = $workspaceId"""
            .map { r =>
              val clusterOptions    = Json.parse(r.string("service_options")).as[Seq[ServiceOption]]
              val selectedServiceId = r.string("service_option_id")
              (clusterOptions, selectedServiceId, r.string("version"), r.stringOpt("extra_services").map(_.split(",")).getOrElse(Array()))
            }
            .single()
            .apply()
            .map {
              case (options, str, version, extraServices) => getContainerConfig(options, version, str, extraServices)
            }

        sql"""select name, provider_cluster_id, provider, status, region from clusters
                where id = $clusterId and workspace_id = $workspaceId"""
          .map { r =>
            NewCluster(
              name = r.string("name"),
              provider = ClusterProvider.withName(r.string("provider")),
              region = r.stringOpt("region").getOrElse(""),
              providerClusterId = r.stringOpt("provider_cluster_id"),
              status = ClusterStatus.withName(r.string("status"))
            )
          }
          .single()
          .apply()
          .map { cluster =>
            val clusterState =
              sql"""SELECT active_apps, cnm.id as node_id, cnm.host, cnm.port, cnm.total_cpu, cnm.total_memory, completed_apps, failed_apps FROM cluster_metric as cm
                     INNER JOIN cluster_node_metric as cnm ON cm.id = cnm.cluster_id WHERE cm.id = $clusterId"""
                .map { r =>
                  val clusterCounters =
                    ClusterState(clusterId, r.int("active_apps"), r.int("completed_apps"), r.int("failed_apps"), Array.empty[ClusterNode])
                  val clusterNode =
                    ClusterNode(r.string("node_id"), r.string("host"), r.int("total_cpu"), r.long("total_memory"), r.intOpt("port"))
                  (clusterCounters, clusterNode)
                }
                .list()
                .apply()

            val optClusterState = clusterState.headOption.map {
              case (state, _) =>
                val nodes = clusterState.map(_._2)
                state.copy(nodes = nodes.toArray)
            }

            ClusterMetric(cluster, optClusterState, containerSandbox)
          }

      }
    }
  }

  override def addCluster(workspaceId: Long, request: NewCluster, sandboxCluster: Option[NewSandboxCluster]): Future[Long] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT id from clusters where workspace_id = $workspaceId AND name = ${request.name.trim} AND status != ${ClusterStatus.DELETED.toString}"""
          .map(_.long("id"))
          .single()
          .apply() match {
          case None =>
            sandboxCluster match {
              case None =>
                sql"""INSERT INTO clusters(name, provider, provider_cluster_id, region, workspace_id, status, dt_added,
              last_updated)
             VALUES(${request.name}, ${request.provider.toString}, ${request.providerClusterId}, ${request.region}, $workspaceId,
             ${request.status.toString}, ${DateUtil.now}, ${DateUtil.now})"""
                  .updateAndReturnGeneratedKey()
                  .apply()
              case Some(sandbox) =>
                sql"""INSERT INTO clusters(name, provider, provider_cluster_id, region, workspace_id, status, dt_added,
              last_updated, sandbox_id, service_option_id, extra_services)
             VALUES(${request.name}, ${request.provider.toString}, ${request.providerClusterId}, ${request.region}, $workspaceId,
             ${request.status.toString}, ${DateUtil.now}, ${DateUtil.now}, ${sandbox.sandboxId}, ${sandbox.serviceOptionId}, ${sandbox.addOns
                  .mkString(",")})"""
                  .updateAndReturnGeneratedKey()
                  .apply()
            }

          case Some(id) =>
            println(id)
            id
        }
      }
    }
  }

  override def saveClusterState(workspaceId: Long, state: ClusterState): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val now = DateUtil.now
        sql"""INSERT INTO cluster_metric(id, active_apps, completed_apps, failed_apps, last_updated)
             VALUES(${state.clusterId}, ${state.activeApps}, ${state.completedApps}, ${state.failedApps}, $now)
             ON CONFLICT (id)
            DO
            UPDATE SET active_apps = ${state.activeApps},
            completed_apps = ${state.completedApps},
            failed_apps = ${state.failedApps},
            last_updated = $now
            """
          .update()
          .apply()
        var count = 0
        val dbNodes = sql"""SELECT id, host, port, total_cpu, total_memory FROM cluster_node_metric WHERE cluster_id = ${state.clusterId}"""
          .map(r => ClusterNode(r.string("id"), r.string("host"), r.int("total_cpu"), r.long("total_memory"), r.intOpt("port")))
          .list()
          .apply()
        val toBeRemovedNodes = dbNodes.filter(n => state.nodes.map(_.id).find(x => x == n.id).isEmpty)
        toBeRemovedNodes.foreach { n =>
          sql"""DELETE FROM cluster_node_metric WHERE cluster_id = ${state.clusterId} AND id = ${n.id}"""
            .update()
            .apply()
        }

        state.nodes.foreach { node =>
          count = count + 1
          sql"""INSERT INTO cluster_node_metric(id, cluster_id, host, port, total_cpu, total_memory,  last_updated)
             VALUES(${node.id}, ${state.clusterId}, ${node.host}, ${node.port}, ${node.cpuCores}, ${node.memory}, $now)
             ON CONFLICT (id, cluster_id)
            DO
            UPDATE SET total_cpu = ${node.cpuCores},
            total_memory = ${node.memory},
            last_updated = $now"""
            .update()
            .apply()
        }
        count == state.nodes.length
      }
    }
  }

  override def listAllClusters(orgId: Long, workspaceId: Long): Future[Seq[ClusterView]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select id, name, service_name, service_version, provider_cluster_id, provider, status, dt_added FROM clusters where
              workspace_id = $workspaceId AND status != ${ClusterStatus.DELETED.toString}"""
          .map { r =>
            ClusterView(
              cid = r.long("id"),
              name = r.string("name"),
              serviceName = r.string("service_name"),
              serviceVersion = r.string("service_version"),
              clusterId = r.string("provider_cluster_id"),
              provider = ClusterProvider.withName(r.string("provider")),
              status = ClusterStatus.withNameOpt(r.string("status")),
              created = DateUtil.timeElapsed(r.zonedDateTime("dt_added"), None) + " ago",
            )
          }
          .list()
          .apply()
      }
    }
  }

  override def listClusterIds(): Future[Map[String, Seq[Long]]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT id, service_name FROM clusters WHERE status <> ${ClusterStatus.DELETED.toString} """
          .map { row =>
            (row.long("id"), row.string("service_name"))
          }
          .list()
          .apply()
          .groupBy(_._2)
          .view.mapValues(_.map(_._1)).toMap
      }
    }
  }

  override def updateClusterStatus(orgId: Long, clusterId: String, status: ClusterStatus): Future[ClusterPingResponse] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""update clusters set status = ${status.toString}, dt_ping = ${DateUtil.now} where id = $clusterId
                 and org_id = $orgId""".update().apply()

        val workIds =
          sql"""select dh.id from deployment_history dh INNER JOIN deployment_config df ON dh.deployment_id = df.id WHERE df.org_id = $orgId
             AND df.agent_id = $clusterId AND dh.status = ${RunStatus.Waiting.toString}"""
            .map(_.long("id"))
            .list()
            .apply()

        ClusterPingResponse(status, workIds)
      }
    }
  }

  override def removeCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val deleteCount = sql"""update clusters SET status = ${ClusterStatus.DELETED.toString} WHERE id = $clusterId
                                AND workspace_id = $workspaceId""".update().apply()
        deleteCount == 1
      }
    }
  }

  override def updateCluster(workspaceId: Long, clusterId: Long, status: ClusterStatus, detail: String): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val updateCount = sql"""update clusters SET status = ${status.toString}, status_detail = ${detail} WHERE id = $clusterId
                                AND workspace_id = $workspaceId""".update().apply()
        if(status == ClusterStatus.STARTING){
          sql"""update cluster_processes SET status = ${RunStatus.Starting.toString} WHERE cluster_id = $clusterId"""
            .update().apply()
        }
        updateCount == 1
      }
    }
  }

  override def getClusterUsage(orgId: Long): Future[Option[ClusterUsage]] = Future {
    blocking {
      DB localTx { implicit session =>
        val clusterUsageCounts = sql"""SELECT provider, count(*) FROM clusters as cl
                        INNER JOIN workspaces ON cl.workspace_id = workspaces.id  WHERE
                         workspaces.org_id = ${orgId} AND cl.status != ${ClusterStatus.DELETED.toString} GROUP BY provider
                         """
          .map { result =>
            (ClusterProvider.withName(result.string("provider")), result.int("count"))
          }
          .list()
          .apply()
        var sandboxes       = 0
        var remoteConnected = 0

        clusterUsageCounts.foreach {
          case (v, count) if v == ClusterProvider.SANDBOX => sandboxes = count
          case (_, count)                                 => remoteConnected = remoteConnected + count
        }

        sql"""SELECT max_local_clusters_count, max_remote_cluster_count FROM usage_plans WHERE
            org_id = ${orgId}"""
          .map(r => (r.int("max_local_clusters_count"), r.int("max_remote_cluster_count")))
          .single()
          .apply()
          .map {
            case (maxLocal, maxRemote) => ClusterUsage(sandboxes, remoteConnected, maxLocal, maxRemote)
          }

      }
    }
  }

  override def listLocalSparkClusters(): Future[Seq[LocalSparkConfig]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT id, workspace_id, version FROM clusters c INNER JOIN
             spark_clusters sc ON c.id = sc.cluster_id"""
          .map { row =>
            LocalSparkConfig(clusterId = row.long("id"), workspaceId = row.long("workspace_id"), sparkVersion = row.string("version"))
          }
          .list()
          .apply()

      }
    }
  }

  override def updateClusterProcess(clusterId: Long,
                                    name: String,
                                    status: RunStatus,
                                    detail: String,
                                    processId: Option[Long]): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val updateCount = processId match {
          case None        => sql"""update cluster_processes SET status = ${status.toString} WHERE cluster_id = $clusterId
                                AND name = $name """.update().apply()
          case Some(value) => sql"""update cluster_processes SET status = ${status.toString},
                         pid = ${value} WHERE cluster_id = $clusterId
                                AND name = $name """.update().apply()
        }
        updateCount == 1
      }
    }
  }
}

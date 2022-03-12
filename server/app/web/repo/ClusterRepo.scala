package web.repo

import com.gigahex.commons.models.ClusterProvider.ClusterProvider
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{
  ClusterNode,
  ClusterProvider,
  ClusterState,
  ClusterStatus,
  ClusterView,
  NewCluster,
  RunStatus,
}
import web.models.cluster.LocalSparkConfig
import web.models.{
  ClusterMetric,
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

  def getOrgId(workspaceId: Long): Future[Option[Long]]

  def clusterExists(workspaceId: Long, name: String, provider: String): Future[Option[Long]]

  def listLocalHostByWorkspace(workspaceId: Long): Future[Seq[ServerHost]]

  def saveSandboxCluster(workspaceId: Long, sandbox: NewSandboxCluster): Future[Long]

  def listLastPingTimestamps(status: ClusterStatus): Future[Seq[LastClusterPing]]

  def inactivateCluster(clusterId: Long): Future[Boolean]

  def getClusterPackages(name: String): Future[Seq[String]]

  def listSandboxVersions(): Future[Seq[DBSandboxCluster]]

  def orgUsagePlan(orgId: Long): Future[Option[OrgUsagePlan]]

  def addCluster(workspaceId: Long, request: NewCluster, sandboxCluster: Option[NewSandboxCluster]): Future[Long]

  def listClusterIds(): Future[Map[String, Seq[Long]]]

  def listAllClusters(orgId: Long, workspaceId: Long): Future[Seq[ClusterView]]

  def removeCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Boolean]

  def updateCluster(workspaceId: Long, clusterId: Long, status: ClusterStatus, detail: String): Future[Boolean]

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

  override def listLocalHostByWorkspace(workspaceId: Long): Future[Seq[ServerHost]] = Future {
    blocking {
      DB readOnly { implicit session =>
        val hosts =
          sql"""SELECT sh.id, sh.name, sh.cluster_id, sh.host_ip, sh.status, c.service_version, c.service_name, sh.dt_added, c.provider FROM server_hosts sh
               INNER JOIN clusters c ON sh.cluster_id = c.id
              WHERE c.status <> ${ClusterStatus.DELETED.toString}"""
            .map { row =>
              ServerHost(
                id = row.long("id"),
                name = row.string("name"),
                provider = ClusterProvider.withName(row.string("provider")),
                components = Seq(ServiceComponent(row.long("cluster_id"), row.string("service_name"), row.string("service_version"))),
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

  override def getClusterPackages(name: String): Future[Seq[String]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select version FROM package_repository where name = $name AND is_deprecated = false"""
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
          .view
          .mapValues(_.map(_._1))
          .toMap
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
        if (status == ClusterStatus.STARTING) {
          sql"""update cluster_processes SET status = ${RunStatus.Starting.toString} WHERE cluster_id = $clusterId"""
            .update()
            .apply()
        }
        updateCount == 1
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

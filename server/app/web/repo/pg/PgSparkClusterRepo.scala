package web.repo.pg

import java.io.{BufferedReader, InputStreamReader}

import com.gigahex.commons.models.{ClusterProvider, ClusterStatus, RunStatus}
import javax.inject.Inject
import web.actors.clusters.spark.SparkProcesses
import web.models.cloud.ClusterProcess
import web.models.cluster.{
  ClusterPackage,
  KafkaPackage,
  LocalSparkConfig,
  SparkClusterInfo,
  SparkClusterProcess,
  SparkConfigurationRequest,
  SparkPackage
}
import web.repo.clusters.{ServicesNames, SparkClusterRepo}
import web.services.SecretStore
import web.utils.DateUtil
import play.api.cache.SyncCacheApi
import scalikejdbc._
import utils.auth.RandomGenerator

import scala.concurrent.{ExecutionContext, Future, blocking}

class PgSparkClusterRepo @Inject()(blockingEC: ExecutionContext, secretStore: SecretStore) extends SparkClusterRepo {

  private implicit val ec = blockingEC
  private val sparkProcesses = Seq(
    SparkClusterProcess(SparkProcesses.MASTER, "localhost", 8080),
    SparkClusterProcess(SparkProcesses.WORKER, "localhost", 8081),
    SparkClusterProcess(SparkProcesses.SHS, "localhost", 18080)
  )

  override def saveSparkConfig(request: SparkConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val hasSparkCluster =
            sql"""SELECT id FROM clusters WHERE provider = ${ClusterProvider.LOCAL.toString}
              AND service_name = ${ServicesNames.SPARK} AND workspace_id = $workspaceId AND status <> ${ClusterStatus.DELETED.toString}"""
              .map(_.long("id"))
              .single()
              .apply()
              .isDefined

          if (hasSparkCluster) {
            -1L
          } else {
            val clusterId =
              sql"""INSERT INTO clusters(name, service_name, service_version, provider, provider_cluster_id, status, workspace_id, dt_added, last_updated)
             VALUES(${request.name}, ${ServicesNames.SPARK}, ${request.version}, ${ClusterProvider.LOCAL.toString}, ${RandomGenerator
                .generate()}, ${ClusterStatus.NEW.toString}, $workspaceId,
             ${DateUtil.now}, ${DateUtil.now})"""
                .updateAndReturnGeneratedKey()
                .apply()
            val pKey = request.privateKey.flatMap(key =>
              secretStore.getWorkspaceKeyPair(workspaceId, workspaceKeyCache).map(kp => secretStore.encrypt(key, kp)))
            val usernameOfHost = if (request.isLocal) System.getProperty("user.name") else request.username

            sql"""INSERT INTO spark_clusters(cluster_id, version, scala_version, configuration_params, host_username, encrypted_pkey, hosts, cluster_manager)
             VALUES(${clusterId}, ${request.version}, ${request.scalaVersion}, ${request.configParams.mkString("\n")}, ${usernameOfHost},
             ${pKey.getOrElse("")}, ${request.hosts.mkString("\n")}, ${request.clusterManager})"""
              .executeUpdate()
              .apply()

            sparkProcesses.foreach(process => {
              sql"""INSERT INTO cluster_processes(cluster_id, name, host_ip, port, status)
             VALUES(${clusterId}, ${process.name}, ${process.host}, ${process.port}, ${RunStatus.NotStarted.toString})"""
                .executeUpdate()
                .apply()
            })

            val hostname = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("hostname").getInputStream()))
              .readLine();

            sql"""INSERT INTO server_hosts(name, host_ip ,cluster_id, status, dt_added)
             VALUES(${hostname}, '127.0.0.1', ${clusterId}, ${ClusterStatus.RUNNING.toString}, ${DateUtil.now})"""
              .executeUpdate()
              .apply()

            clusterId

          }

        }
      }
    }

  def updateDownloadProgress(clusterId: Long, workspaceId: Long, progress: String): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""UPDATE clusters SET status = ${ClusterStatus.DOWNLOADING.toString},
              status_detail = ${progress}
               WHERE id = $clusterId AND
             workspace_id = $workspaceId""".executeUpdate().apply() > 0

      }
    }
  }

  override def getLocalSparkConfig(clusterId: Long, workspaceId: Long): Future[Option[LocalSparkConfig]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT version, configuration_params FROM spark_clusters as sc INNER JOIN
           clusters as c ON sc.cluster_id = c.id WHERE c.id = ${clusterId} AND c.workspace_id = ${workspaceId} """
          .map { row =>
            val configParams = row.stringOpt("configuration_params").map(_.split("\n").toSeq).getOrElse(Seq.empty)
            LocalSparkConfig(clusterId, workspaceId, row.string("version"), configuration = configParams)
          }
          .single()
          .apply()
      }
    }
  }

  override def getClusterProcess(clusterId: Long, workspaceId: Long, name: String): Future[Option[SparkClusterProcess]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT cp.name, host_ip, cp.port, cp.status FROM cluster_processes cp INNER JOIN
             clusters cl ON cp.cluster_id = cl.id WHERE cl.id = ${clusterId} AND cl.workspace_id = ${workspaceId}
             AND cp.name ILIKE ${name}"""
          .map { row =>
            SparkClusterProcess(row.string("name"), row.string("host_ip"), row.int("port"), RunStatus.withName(row.string("status")))
          }
          .single()
          .apply()
      }
    }
  }

  override def getClusterPackages(clusterIds: Seq[Long]): Future[Seq[ClusterPackage]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT id, c.name, c.status as cluster_status, c.workspace_id, sc.version, cp.name as process_name,
               cp.host_ip, cp.port, sc.scala_version, sc.configuration_params FROM clusters c INNER JOIN
             spark_clusters sc ON c.id = sc.cluster_id
             INNER JOIN cluster_processes cp ON c.id = cp.cluster_id
             WHERE sc.cluster_id IN (${clusterIds})"""
          .map { row =>
            SparkPackage(
              clusterId = row.long("id"),
              workspaceId = row.long("workspace_id"),
              status = ClusterStatus.withName(row.string("cluster_status")),
              name = row.string("name"),
              version = row.string("version"),
              scalaVersion = row.string("scala_version"),
              userConfig = row.stringOpt("configuration_params").map(_.split("\n").toSeq).getOrElse(Seq.empty),
              processes = Seq(ClusterProcess(row.string("process_name"), row.string("host_ip"), row.int("port")))
            )
          }
          .list()
          .apply()
          .groupBy(_.clusterId)
          .map {
            case (_, packages) => packages.head.copy(processes = packages.flatMap(_.processes))
          }
          .toSeq

      }
    }
  }

  override def getSparkCluster(clusterId: Long, workspaceId: Long): Future[Option[SparkClusterInfo]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT cluster_id, c.name, version, scala_version, c.status, cluster_manager,
             c.status_detail FROM spark_clusters as sc INNER JOIN
             clusters as c ON sc.cluster_id = c.id
              WHERE c.id = ${clusterId} AND c.workspace_id = ${workspaceId}
             """
          .map { row =>
            SparkClusterInfo(
              name = row.string("name"),
              version = row.string("version"),
              scalaVersion = row.string("scala_version"),
              clusterManager = row.string("cluster_manager"),
              status = ClusterStatus.withName(row.string("status")),
              processes = Seq(),
              statusDetail = row.stringOpt("status_detail")
            )
          }
          .single()
          .apply() map { clusterInfo =>
          val processes = sql"""SELECT name, host_ip, port, pid, status FROM cluster_processes WHERE cluster_id = ${clusterId}"""
            .map { row =>
              ClusterProcess(name = row.string("name"),
                             host = row.string("host_ip"),
                             port = row.int("port"),
                             status = RunStatus.withName(row.string("status")),
                             pid = row.longOpt("pid"))
            }
            .list()
            .apply()
          val masterCount = processes.count(_.name.equalsIgnoreCase(SparkProcesses.MASTER))
          val workerCount = processes.count(_.name.equalsIgnoreCase(SparkProcesses.WORKER))
          clusterInfo.copy(
            processes = processes,
            metrics = clusterInfo.metrics.copy(
              properties = Map(SparkProcesses.MASTER -> masterCount.toString, SparkProcesses.WORKER -> workerCount.toString))
          )
        }

      }
    }
  }
}

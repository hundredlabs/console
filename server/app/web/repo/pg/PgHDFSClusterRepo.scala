package web.repo.pg

import java.io.{BufferedReader, InputStreamReader}

import com.gigahex.commons.models.{ClusterProvider, ClusterStatus, RunStatus}
import javax.inject.Inject
import web.models.cloud.ClusterProcess
import web.models.cluster.{ClusterPackage, HDFSClusterInfo, HDFSConfigurationRequest, HDFSProcesses, HadoopPackage, KafkaClusterInfo, KafkaConfigurationRequest, KafkaPackage, KafkaProcesses}
import web.repo.clusters.{HDFSClusterRepo, KafkaClusterRepo, ServicesNames}
import web.services.SecretStore
import web.utils.DateUtil
import play.api.cache.SyncCacheApi
import scalikejdbc._
import utils.auth.RandomGenerator

import scala.concurrent.{ExecutionContext, Future, blocking}

class PgHDFSClusterRepo @Inject()(blockingEC: ExecutionContext, secretStore: SecretStore) extends HDFSClusterRepo {

  private implicit val ec = blockingEC
  private val hdfsProcesses = Seq(
    ClusterProcess(HDFSProcesses.NAME_NODE, "localhost", 9075),
    ClusterProcess(HDFSProcesses.DATA_NODE, "localhost", 50075)
  )

  override def saveHDFSConfig(request: HDFSConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long] =
    Future {
      blocking {
        DB localTx { implicit session =>

          val hasHDFSCluster =
            sql"""SELECT id FROM clusters WHERE provider = ${ClusterProvider.LOCAL.toString}
              AND service_name = ${ServicesNames.HADOOP}
              AND workspace_id = $workspaceId AND status <> ${ClusterStatus.DELETED.toString}"""
              .map(_.long("id"))
              .single()
              .apply()
              .isDefined

          if (hasHDFSCluster) {
            -1L
          } else
          {
            val clusterId = sql"""INSERT INTO clusters(name, provider, provider_cluster_id, service_name, service_version,
                       status, workspace_id, dt_added, last_updated)
             VALUES(${request.name}, ${ClusterProvider.LOCAL.toString}, ${RandomGenerator
              .generate()}, ${ServicesNames.HADOOP}, ${request.version}, ${ClusterStatus.NEW.toString}, $workspaceId,
             ${DateUtil.now}, ${DateUtil.now})"""
              .updateAndReturnGeneratedKey()
              .apply()
            val pKey = request.privateKey.flatMap(key =>
              secretStore.getWorkspaceKeyPair(workspaceId, workspaceKeyCache).map(kp => secretStore.encrypt(key, kp)))
            val usernameOfHost = if (request.isLocal) System.getProperty("user.name") else request.username

            sql"""INSERT INTO hadoop_clusters(cluster_id, version, hdfs_site, core_site, host_username, encrypted_pkey, hosts)
             VALUES(${clusterId}, ${request.version}, ${request.hdfsSite.mkString("\n")}, ${request.coreSite.mkString("\n")}, ${usernameOfHost},
             ${pKey.getOrElse("")}, ${request.hosts.mkString("\n")})"""
              .executeUpdate()
              .apply()

            hdfsProcesses.foreach(process => {
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

  override def getClusterPackages(clusterIds: Seq[Long]): Future[Seq[ClusterPackage]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT id, c.name, c.workspace_id, c.status as cluster_status, hc.version, cp.name as process_name,
               cp.host_ip, cp.port, cp.pid, cp.status,
              hc.core_site, hc.hdfs_site FROM clusters c INNER JOIN
             hadoop_clusters hc ON c.id = hc.cluster_id
             INNER JOIN cluster_processes cp ON c.id = cp.cluster_id
             WHERE hc.cluster_id IN (${clusterIds})"""
          .map{ row =>
            HadoopPackage(clusterId = row.long("id"),
              workspaceId = row.long("workspace_id"),
              status = ClusterStatus.withName(row.string("cluster_status")),
              name = row.string("name"),
              version = row.string("version"),
              coreSite = row.string("core_site").split("\n"),
              hdfsSite = row.string("hdfs_site").split("\n"),
              processes = Seq(ClusterProcess(row.string("process_name"), row.string("host_ip"),
                row.int("port"), pid = row.longOpt("pid"), status = RunStatus.withName(row.string("status"))))
            )
          }
          .list()
          .apply()
          .groupBy(_.clusterId)
          .map {
            case (_, packages) => packages.head.copy(processes = packages.flatMap(_.processes))
          }.toSeq

      }
    }
  }

  override def getHDFSCluster(clusterId: Long, workspaceId: Long): Future[Option[HDFSClusterInfo]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT cluster_id, c.name, c.service_name, version, core_site, hdfs_site, c.status, c.status_detail FROM hadoop_clusters as sc INNER JOIN
             clusters as c ON sc.cluster_id = c.id
              WHERE c.id = ${clusterId} AND c.workspace_id = ${workspaceId}
             """
          .map { row =>
            HDFSClusterInfo(
              id = row.long("cluster_id"),
              name = row.string("name"),
              version = row.string("version"),
              service = row.string("service_name"),
              coreSite = row.string("core_site").split("\n"),
              hdfsSite = row.string("hdfs_site").split("\n"),
              status = ClusterStatus.withName(row.string("status")),
              processes = Seq(),
              statusDetail = row.stringOpt("status_detail")
            )
          }
          .single()
          .apply() map { clusterInfo =>
          val processes = sql"""SELECT name, host_ip, port, status, pid FROM cluster_processes WHERE cluster_id = ${clusterId}"""
            .map { row =>
              ClusterProcess(name = row.string("name"),
                host = row.string("host_ip"),
                port = row.int("port"),
                status = RunStatus.withName(row.string("status")),
                pid = row.intOpt("pid").map(_.toLong))
            }
            .list()
            .apply()

          clusterInfo.copy(processes = processes)
        }

      }
    }
  }

}

package web.repo.pg

import java.io.{BufferedReader, InputStreamReader}

import com.gigahex.commons.models.{ClusterProvider, ClusterStatus, RunStatus}
import javax.inject.Inject
import web.models.cloud.ClusterProcess
import web.models.cluster.{ClusterPackage, KafkaClusterInfo, KafkaConfigurationRequest, KafkaPackage, KafkaProcesses}
import web.repo.clusters.{KafkaClusterRepo, ServicesNames}
import web.services.SecretStore
import web.utils.DateUtil
import play.api.cache.SyncCacheApi
import scalikejdbc._
import utils.auth.RandomGenerator

import scala.concurrent.{ExecutionContext, Future, blocking}

class PgKafkaClusterRepo @Inject()(blockingEC: ExecutionContext, secretStore: SecretStore) extends KafkaClusterRepo {

  private implicit val ec = blockingEC
  private val kafkaProcesses = Seq(
    ClusterProcess(KafkaProcesses.KAFKA_SERVER, "localhost", 9092),
    ClusterProcess(KafkaProcesses.ZOOKEEPER, "localhost", 2181)
  )

  override def saveKafkaConfig(request: KafkaConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long] =
    Future {
      blocking {
        DB localTx { implicit session =>

          val hasKafkaCluster =
            sql"""SELECT id FROM clusters WHERE provider = ${ClusterProvider.LOCAL.toString}
              AND service_name = ${ServicesNames.KAFKA}
              AND workspace_id = $workspaceId AND status <> ${ClusterStatus.DELETED.toString}"""
              .map(_.long("id"))
              .single()
              .apply()
              .isDefined

          if (hasKafkaCluster) {
            -1L
          } else
           {
             val clusterId = sql"""INSERT INTO clusters(name, provider, provider_cluster_id, service_name, service_version,
                       status, workspace_id, dt_added, last_updated)
             VALUES(${request.name}, ${ClusterProvider.LOCAL.toString}, ${RandomGenerator
               .generate()}, ${ServicesNames.KAFKA}, ${request.version}, ${ClusterStatus.NEW.toString}, $workspaceId,
             ${DateUtil.now}, ${DateUtil.now})"""
               .updateAndReturnGeneratedKey()
               .apply()
             val pKey = request.privateKey.flatMap(key =>
               secretStore.getWorkspaceKeyPair(workspaceId, workspaceKeyCache).map(kp => secretStore.encrypt(key, kp)))
             val usernameOfHost = if (request.isLocal) System.getProperty("user.name") else request.username

             sql"""INSERT INTO kafka_clusters(cluster_id, version, scala_version, configuration_params, host_username, encrypted_pkey, hosts)
             VALUES(${clusterId}, ${request.version}, ${request.scalaVersion}, ${request.configParams.mkString("\n")}, ${usernameOfHost},
             ${pKey.getOrElse("")}, ${request.hosts.mkString("\n")})"""
               .executeUpdate()
               .apply()

             kafkaProcesses.foreach(process => {
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
        sql"""SELECT id, c.name, c.workspace_id, c.status as cluster_status, kc.version, cp.name as process_name,
               cp.host_ip, cp.port, cp.pid, cp.status,
              kc.scala_version FROM clusters c INNER JOIN
             kafka_clusters kc ON c.id = kc.cluster_id
             INNER JOIN cluster_processes cp ON c.id = cp.cluster_id
             WHERE kc.cluster_id IN (${clusterIds})"""
          .map{ row =>
            KafkaPackage(clusterId = row.long("id"),
              workspaceId = row.long("workspace_id"),
              status = ClusterStatus.withName(row.string("cluster_status")),
              name = row.string("name"),
              version = row.string("version"),
              scalaVersion = row.string("scala_version"),
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

  override def getKafkaCluster(clusterId: Long, workspaceId: Long): Future[Option[KafkaClusterInfo]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT cluster_id, c.name, c.service_name, version, scala_version, c.status, c.status_detail FROM kafka_clusters as sc INNER JOIN
             clusters as c ON sc.cluster_id = c.id
              WHERE c.id = ${clusterId} AND c.workspace_id = ${workspaceId}
             """
          .map { row =>
            KafkaClusterInfo(
              id = row.long("cluster_id"),
              name = row.string("name"),
              version = row.string("version"),
              service = row.string("service_name"),
              scalaVersion = row.string("scala_version"),
              status = ClusterStatus.withName(row.string("status")),
              processes = Seq(),
              statusDetail = row.stringOpt("status_detail")
            )
          }
          .single()
          .apply() map { clusterInfo =>
          val processes = sql"""SELECT name, host_ip, port, status FROM cluster_processes WHERE cluster_id = ${clusterId}"""
            .map { row =>
              ClusterProcess(name = row.string("name"),
                             host = row.string("host_ip"),
                             port = row.int("port"),
                             status = RunStatus.withName(row.string("status")))
            }
            .list()
            .apply()
          val brokers = processes.count(_.name.equalsIgnoreCase(KafkaProcesses.KAFKA_SERVER))

          clusterInfo.copy(processes = processes, metrics = clusterInfo.metrics.copy(properties = Map("brokers" -> brokers.toString)))
        }

      }
    }
  }

}

package web.repo.clusters

import web.models.cluster.{ClusterPackage, KafkaClusterInfo, KafkaConfigurationRequest}
import play.api.cache.SyncCacheApi

import scala.concurrent.Future

trait KafkaClusterRepo {

  def getKafkaCluster(clusterId: Long, workspaceId: Long): Future[Option[KafkaClusterInfo]]

  def saveKafkaConfig(request: KafkaConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long]

  def getClusterPackages(clusterIds: Seq[Long]) : Future[Seq[ClusterPackage]]

}

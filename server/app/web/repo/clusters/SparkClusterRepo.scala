package web.repo.clusters

import web.models.cluster.{ClusterPackage, LocalSparkConfig, SparkClusterInfo, SparkClusterProcess, SparkConfigurationRequest}
import play.api.cache.SyncCacheApi

import scala.concurrent.Future

trait SparkClusterRepo {

  def getClusterPackages(cIds: Seq[Long]): Future[Seq[ClusterPackage]]

  def getSparkCluster(clusterId: Long, workspaceId: Long): Future[Option[SparkClusterInfo]]

  def saveSparkConfig(request: SparkConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long]

  def updateDownloadProgress(clusterId: Long, workspaceId: Long, progress: String): Future[Boolean]

  def getLocalSparkConfig(clusterId: Long, workspaceId: Long): Future[Option[LocalSparkConfig]]

  def getClusterProcess(clusterId: Long, workspaceId: Long, name: String): Future[Option[SparkClusterProcess]]

}

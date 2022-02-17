package web.repo.clusters

import web.models.cluster.{ClusterPackage, HDFSClusterInfo, HDFSConfigurationRequest}
import play.api.cache.SyncCacheApi

import scala.concurrent.Future

trait HDFSClusterRepo {

    def getHDFSCluster(clusterId: Long, workspaceId: Long): Future[Option[HDFSClusterInfo]]

    def saveHDFSConfig(request: HDFSConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long]

    def getClusterPackages(clusterIds: Seq[Long]) : Future[Seq[ClusterPackage]]

}

package web.services

import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.{ClusterPingResponse, ClusterState, ClusterView, NewCluster}
import javax.inject.Inject
import web.models.cluster.{
  ClusterPackage,
  HDFSClusterInfo,
  HDFSConfigurationRequest,
  KafkaClusterInfo,
  KafkaConfigurationRequest,
  LocalSparkConfig,
  SparkClusterInfo,
  SparkClusterProcess,
  SparkConfigurationRequest
}
import web.models.{DBSandboxCluster, LastClusterPing, NewSandboxCluster, ServerHost}
import web.repo.ClusterRepo
import web.repo.clusters.{HDFSClusterRepo, KafkaClusterRepo, ServicesNames, SparkClusterRepo}
import play.api.cache.SyncCacheApi

import scala.concurrent.{ExecutionContext, Future}

trait ClusterService {

  def listAllClusters(orgId: Long, workspaceId: Long): Future[Seq[ClusterView]]

  def clusterExists(workspaceId: Long, name: String, provider: String): Future[Option[Long]]

  def listSandboxVersions(): Future[Seq[DBSandboxCluster]]

  def saveSandboxCluster(workspaceId: Long, sandbox: NewSandboxCluster): Future[Long]

  def listLocalHostByWorkspace(workspaceId: Long): Future[Seq[ServerHost]]

  def listSandboxes(workspaceId: Long): Future[Seq[ServerHost]]

  def listClusterPackages(): Future[Seq[ClusterPackage]]

  def getOrgId(workspaceId: Long): Future[Option[Long]]

  def addCluster(orgId: Long,
                 workspaceId: Long,
                 request: NewCluster,
                 sandboxCluster: Option[NewSandboxCluster] = None): Future[Either[Throwable, Long]]

  def listClustersByStatus(status: ClusterStatus): Future[Seq[ClusterView]]

  def listLocalSparkClusters(): Future[Seq[LocalSparkConfig]]

  def listLastPingTimestamps(status: ClusterStatus): Future[Seq[LastClusterPing]]

  def inactivateCluster(clusterId: Long): Future[Boolean]

  def removeCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Boolean]

  def updateCluster(workspaceId: Long, clusterId: Long, status: ClusterStatus, detail: Option[String] = None): Future[Boolean]

  def saveLocalSparkConfiguration(sparkConfigurationRequest: SparkConfigurationRequest,
                                  workspaceId: Long,
                                  workspaceKeyCache: SyncCacheApi): Future[Long]

  def saveLocalKafkaConfiguration(config: KafkaConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long]

  def saveLocalHDFSConfiguration(config: HDFSConfigurationRequest, workspaceId: Long, workspaceKeyCache: SyncCacheApi): Future[Long]

  def getKafkaCluster(clusterId: Long, workspaceId: Long): Future[Option[KafkaClusterInfo]]

  def getHDFSCluster(clusterId: Long, workspaceId: Long): Future[Option[HDFSClusterInfo]]

  def getSparkCluster(clusterId: Long, workspaceId: Long): Future[Option[SparkClusterInfo]]

  def getClusterPackageInfo(clusterId: Long, name: String): Future[Option[ClusterPackage]]

  def getClusterProcess(clusterId: Long, workspaceId: Long, name: String): Future[Option[SparkClusterProcess]]

  def getClusterPackages(name: String): Future[Seq[String]]

  def getLocalSparkConfig(clusterId: Long, workspaceId: Long): Future[Option[LocalSparkConfig]]

  def updateDownloadProgress(clusterId: Long, workspaceId: Long, progress: String): Future[Boolean]

  def updateClusterProcess(clusterId: Long,
                           name: String,
                           status: RunStatus,
                           detail: String,
                           processId: Option[Long] = None): Future[Boolean]

}

class ClusterServiceImpl @Inject()(clusterRepo: ClusterRepo,
                                   sparkClusterRepo: SparkClusterRepo,
                                   kafkaClusterRepo: KafkaClusterRepo,
                                   hdfsClusterRepo: HDFSClusterRepo)
    extends ClusterService {

  implicit val ec: ExecutionContext = clusterRepo.ec

  override def listAllClusters(orgId: Long, workspaceId: Long): Future[Seq[ClusterView]] = clusterRepo.listAllClusters(orgId, workspaceId)

  override def clusterExists(workspaceId: Long, name: String, provider: String): Future[Option[Long]] =
    clusterRepo.clusterExists(workspaceId, name, provider)

  override def listSandboxVersions(): Future[Seq[DBSandboxCluster]] = clusterRepo.listSandboxVersions()

  override def saveSandboxCluster(workspaceId: Long, sandbox: NewSandboxCluster): Future[Long] =
    clusterRepo.saveSandboxCluster(workspaceId, sandbox)


  override def listClusterPackages(): Future[Seq[ClusterPackage]] = {
    clusterRepo.listClusterIds().flatMap { ids =>
      val futures = ids.map {
        case (service, cIds) if service.equals(ServicesNames.KAFKA)  => kafkaClusterRepo.getClusterPackages(cIds)
        case (service, cIds) if service.equals(ServicesNames.SPARK)  => sparkClusterRepo.getClusterPackages(cIds)
        case (service, cIds) if service.equals(ServicesNames.HADOOP) => hdfsClusterRepo.getClusterPackages(cIds)
      }
      Future.sequence(futures).map(_.flatten)
    } map (_.toSeq)
  }

  override def getOrgId(workspaceId: Long): Future[Option[Long]] = clusterRepo.getOrgId(workspaceId)

  override def listLocalHostByWorkspace(workspaceId: Long): Future[Seq[ServerHost]] =
    clusterRepo.listLocalHostByWorkspace(workspaceId)

  override def listSandboxes(workspaceId: Long): Future[Seq[ServerHost]] = clusterRepo.listLocalHostByWorkspace(workspaceId)

  override def getClusterPackageInfo(clusterId: Long, name: String): Future[Option[ClusterPackage]] = name match {
    case ServicesNames.SPARK  => sparkClusterRepo.getClusterPackages(Seq(clusterId)).map(_.headOption)
    case ServicesNames.KAFKA  => kafkaClusterRepo.getClusterPackages(Seq(clusterId)).map(_.headOption)
    case ServicesNames.HADOOP => hdfsClusterRepo.getClusterPackages(Seq(clusterId)).map(_.headOption)
  }

  override def addCluster(orgId: Long,
                          workspaceId: Long,
                          request: NewCluster,
                          sandboxCluster: Option[NewSandboxCluster] = None): Future[Either[Throwable, Long]] = {
    for {
      orgUsage <- clusterRepo.orgUsagePlan(orgId)
      canAdd <- orgUsage match {
        case None    => Future.successful(false)
        case Some(v) => clusterRepo.checkUsage(orgId, request.provider, v)
      }
      result <- if (canAdd) {
        clusterRepo.addCluster(workspaceId, request, sandboxCluster).map(Right(_))
      } else {
        Future.successful(Left(new RuntimeException(s"You've reached the maximum limit of clusters that can be created.")))
      }
    } yield result
  }

  override def listClustersByStatus(status: ClusterStatus): Future[Seq[ClusterView]] = clusterRepo.listClusterViewByStatus(status)

  override def listLocalSparkClusters(): Future[Seq[LocalSparkConfig]] = clusterRepo.listLocalSparkClusters()

  override def listLastPingTimestamps(status: ClusterStatus): Future[Seq[LastClusterPing]] = clusterRepo.listLastPingTimestamps(status)

  override def inactivateCluster(clusterId: Long): Future[Boolean] = clusterRepo.inactivateCluster(clusterId)

  override def removeCluster(orgId: Long, workspaceId: Long, clusterId: Long): Future[Boolean] =
    clusterRepo.removeCluster(orgId, workspaceId, clusterId)

  override def updateCluster(workspaceId: Long, clusterId: Long, status: ClusterStatus, detail: Option[String] = None): Future[Boolean] =
    clusterRepo.updateCluster(workspaceId, clusterId, status, detail.getOrElse(""))

  override def saveLocalSparkConfiguration(sparkConfigurationRequest: SparkConfigurationRequest,
                                           workspaceId: Long,
                                           workspaceKeyCache: SyncCacheApi): Future[Long] = {
    sparkClusterRepo.saveSparkConfig(sparkConfigurationRequest, workspaceId, workspaceKeyCache)
  }

  override def saveLocalKafkaConfiguration(config: KafkaConfigurationRequest,
                                           workspaceId: Long,
                                           workspaceKeyCache: SyncCacheApi): Future[Long] =
    kafkaClusterRepo.saveKafkaConfig(config, workspaceId, workspaceKeyCache)

  override def saveLocalHDFSConfiguration(config: HDFSConfigurationRequest,
                                          workspaceId: Long,
                                          workspaceKeyCache: SyncCacheApi): Future[Long] =
    hdfsClusterRepo.saveHDFSConfig(config, workspaceId, workspaceKeyCache)

  override def getKafkaCluster(clusterId: Long, workspaceId: Long): Future[Option[KafkaClusterInfo]] =
    kafkaClusterRepo.getKafkaCluster(clusterId, workspaceId)

  override def getHDFSCluster(clusterId: Long, workspaceId: Long): Future[Option[HDFSClusterInfo]] =
    hdfsClusterRepo.getHDFSCluster(clusterId, workspaceId)

  override def getClusterProcess(clusterId: Long, workspaceId: Long, name: String): Future[Option[SparkClusterProcess]] =
    sparkClusterRepo.getClusterProcess(clusterId, workspaceId, name)

  override def getClusterPackages(name: String): Future[Seq[String]] =
    clusterRepo.getClusterPackages(name)

  override def getSparkCluster(clusterId: Long, workspaceId: Long): Future[Option[SparkClusterInfo]] =
    sparkClusterRepo.getSparkCluster(clusterId, workspaceId)

  override def getLocalSparkConfig(clusterId: Long, workspaceId: Long): Future[Option[LocalSparkConfig]] =
    sparkClusterRepo.getLocalSparkConfig(clusterId, workspaceId)

  override def updateDownloadProgress(clusterId: Long, workspaceId: Long, progress: String): Future[Boolean] =
    sparkClusterRepo.updateDownloadProgress(clusterId, workspaceId, progress)

  override def updateClusterProcess(clusterId: Long,
                                    name: String,
                                    status: RunStatus,
                                    detail: String,
                                    processId: Option[Long] = None): Future[Boolean] =
    clusterRepo.updateClusterProcess(clusterId, name, status, detail, processId)
}

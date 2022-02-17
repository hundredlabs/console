package web.models

import java.time.ZonedDateTime

import com.gigahex.commons.models.ClusterProvider.ClusterProvider
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.TriggerMethod.TriggerMethod
import com.gigahex.commons.models.{
  ClusterIdResponse,
  ClusterMiniView,
  ClusterNode,
  ClusterPingResponse,
  ClusterProvider,
  ClusterRegistrationResponse,
  ClusterState,
  ClusterStatus,
  ClusterView,
  NewCluster,
  RegisterAgent,
  TriggerMethod,
  UpdateStatus
}
import play.api.libs.json._

case class ServicePort(port: Int, name: String, isWebPort: Boolean = true)
case class ContainerAppDef(name: String, version: String, ports: Seq[ServicePort])
case class ContainerSandbox(image: String, apps: Seq[ContainerAppDef], addOns: Seq[String] = Seq())
case class ClusterMetric(info: NewCluster, state: Option[ClusterState], sandboxContainer: Option[ContainerSandbox])
case class ClusterUsage(sandboxCreated: Int, clustersConnected: Int, maxSandboxAllowed: Int, maxClustersConnections: Int)
case class OrgUsagePlan(name: String,
                        maxLocalClusters: Int,
                        maxRemoteClusters: Int,
                        maxRemoteClusterSize: Int,
                        maxJobsCount: Int,
                        maxWorkspaceCount: Int)
object OrgUsagePlan {
  def freePlan(): OrgUsagePlan = OrgUsagePlan("Free", 2, 1, 3, 5, 1)

  def teamsPlan(): OrgUsagePlan = OrgUsagePlan("Teams", Int.MaxValue, Int.MaxValue, Int.MaxValue, 100, 100)
}
case class ClusterDeploymentHistory(deploymentName: String,
                                    jobName: String,
                                    depId: Long,
                                    jobId: Long,
                                    deploymentRunId: Long,
                                    triggerMethod: TriggerMethod,
                                    status: String,
                                    started: String,
                                    runtime: String,
                                    internalJobRunId: Option[String])
object ServiceNames {
  val SPARK    = "spark"
  val HADOOP   = "hadoop"
  val POSTGRES = "postgreSQL"
  val MYSQL    = "mySQL"
  val FLINK    = "flink"
  val KAFKA    = "kafka"
}
case class DistributedService(name: String, version: String)
case class ServiceOption(id: String, services: Seq[DistributedService], image: Option[String] = None)
object ServiceOptionId extends Enumeration {
  type ServiceOptionId = Value
  val SPARK_STANDALONE = Value("Spark Standalone")
  val SPARK_YARN       = Value("Spark on YARN")
  val SPARK_KAFKA      = Value("Spark with Kafka")
  val FLINK_KAFKA      = Value("Flink with Kafka")

  def getServicePorts(v: Value, serviceName: String): Seq[ServicePort] = v match {

    case SPARK_STANDALONE =>
      Seq(ServicePort(18080, "Spark History Server"), ServicePort(8080, "Master Web UI"))

    case SPARK_YARN if serviceName.equalsIgnoreCase(ServiceNames.HADOOP) =>
      Seq(ServicePort(8088, "Resource Manager"),
          ServicePort(50070, "HDFS Web UI"),
          ServicePort(50075, "Data Node"),
          ServicePort(9000, "HDFS Servcie", false))

    case SPARK_YARN if serviceName.equalsIgnoreCase(ServiceNames.SPARK) =>
      Seq(ServicePort(18080, "Spark History Server"))
  }

}
case class SandboxCluster(version: String, serviceOptions: Seq[ServiceOption])
case class NewSandboxCluster(name: String, sandboxId: Int, serviceOptionId: String, addOns: Seq[String] = Seq())
case class DBSandboxCluster(id: Long, cluster: SandboxCluster)
case class VerifyCluster(name: String, provider: ClusterProvider)
case class LastClusterPing(id: Long, lastPingTS: ZonedDateTime)
case class ServiceComponent(name: String, version: String)
case class ServerHost(id: Long,
                      name: String,
                      provider: ClusterProvider,
                      components: Seq[ServiceComponent],
                      dtAddedEpoch: Long,
                      status: ClusterStatus)

trait ClusterJsonFormat {

  implicit val clusterTypeFmt         = Json.formatEnum(ClusterProvider)
  implicit val clusterStatusFmt       = Json.formatEnum(ClusterStatus)
  implicit val serviceComponentFmt    = Json.format[ServiceComponent]
  implicit val serverHostFmt          = Json.format[ServerHost]
  implicit val registerClusterFmt     = Json.format[RegisterAgent]
  implicit val clusterJsonFormatFmt   = Json.format[ClusterRegistrationResponse]
  implicit val updateStatusFmt        = Json.format[UpdateStatus]
  implicit val clusterViewFmt         = Json.format[ClusterView]
  implicit val newClusterFmt          = Json.format[NewCluster]
  implicit val clusterPingResponseFmt = Json.format[ClusterPingResponse]
  implicit val clusterMiniViewFmt     = Json.format[ClusterMiniView]
  implicit val clusterIdResponseFmt   = Json.format[ClusterIdResponse]
  implicit val clusterNodeFmt         = Json.format[ClusterNode]
  implicit val clusterStateFmt        = Json.format[ClusterState]
  implicit val servicePortFmt         = Json.format[ServicePort]
  implicit val containerAppDefFmt     = Json.format[ContainerAppDef]
  implicit val containerSandboxFmt    = Json.format[ContainerSandbox]
  implicit val clusterUsageFmt        = Json.format[ClusterUsage]

  implicit val clusterMetricFmt            = Json.format[ClusterMetric]
  implicit val triggerMethodFmt            = Json.formatEnum(TriggerMethod)
  implicit val clusterDeploymentHistoryFmt = Json.format[ClusterDeploymentHistory]
  implicit val distributedServiceFmt       = Json.format[DistributedService]
  implicit val serviceOptFmt               = Json.format[ServiceOption]
  implicit val sandboxClusterFmt           = Json.format[SandboxCluster]
  implicit val newSandboxClusterFmt        = Json.format[NewSandboxCluster]
  implicit val verifyClusterFmt            = Json.format[VerifyCluster]
  implicit val DBSandboxClusterFmt         = Json.format[DBSandboxCluster]

}

package web.models.cluster

import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import web.models.cloud.ClusterProcess
import web.utils.MetricConverter
import play.api.libs.json.Json

case class HDFSClusterMetric(totalMem: Long, cpuCores: Int)
case class HDFSConfigurationRequest(name: String,
                                    version: String,
                                    hdfsSite: Seq[String],
                                    coreSite: Seq[String],
                                    username: String,
                                    privateKey: Option[String] = None,
                                    hosts: Seq[String],
                                    isLocal: Boolean = true)
case class HDFSClusterInfo(id: Long,
                           name: String,
                           service: String,
                           version: String,
                           hdfsSite: Seq[String],
                           coreSite: Seq[String],
                           status: ClusterStatus,
                           processes: Seq[ClusterProcess],
                           metrics: HDFSClusterMetric = HDFSClusterMetric(MetricConverter.getRam, Runtime.getRuntime.availableProcessors()),
                           statusDetail: Option[String] = None)


object HDFSProcesses {
  val DATA_NODE = "datanode"
  val NAME_NODE = "namenode"

}

trait HDFSJsonFormats {
  implicit val runStatusFmt          = Json.formatEnum(RunStatus)
  implicit val clusterStatusFmt      = Json.formatEnum(ClusterStatus)
  implicit val kafkaConfigRequestFmt = Json.format[HDFSConfigurationRequest]
  implicit val clusterProcessFmt     = Json.format[ClusterProcess]
  implicit val clusterMetricFmt      = Json.format[HDFSClusterMetric]
  implicit val kafkaClusterInfoFmt   = Json.format[HDFSClusterInfo]
}



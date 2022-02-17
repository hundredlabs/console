package web.models.cluster

import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import com.gigahex.commons.models.RunStatus.RunStatus
import web.models.cloud.ClusterProcess
import web.utils.MetricConverter
import play.api.libs.json.Json

case class SparkConfigurationRequest(name: String,
                                     version: String,
                                     scalaVersion: String,
                                     clusterManager: String,
                                     configParams: Seq[String],
                                     username: String,
                                     privateKey: Option[String] = None,
                                     hosts: Seq[String],
                                     isLocal: Boolean = true)
case class LocalSparkConfig(clusterId: Long,
                            workspaceId: Long,
                            sparkVersion: String,
                            hadoopBinaryVersion: String = "2.7",
                            configuration: Seq[String] = Seq.empty[String])

case class SparkClusterMetric(totalMem: Long, cpuCores: Int, properties: Map[String, String])
case class SparkClusterInfo(name: String,
                            version: String,
                            scalaVersion: String,
                            clusterManager: String,
                            status: ClusterStatus,
                            processes: Seq[ClusterProcess],
                            metrics: SparkClusterMetric =
                              SparkClusterMetric(MetricConverter.getRam, Runtime.getRuntime.availableProcessors(), Map()),
                            statusDetail: Option[String] = None)
case class SparkClusterProcess(name: String, host: String, port: Int, status: RunStatus = RunStatus.NotStarted)
case class WorkerView(workerId: String,
                      address: String,
                      status: String,
                      coresUsed: Int,
                      coresAvailable: Int,
                      memoryUsed: String,
                      memoryAvailable: String)
case class SparkMasterSummary(url: String,
                              aliveWorkers: Int,
                              coresUsed: Int,
                              coresAvailable: Int,
                              memoryUsed: String,
                              status: String,
                              memoryAvailable: String,
                              workers: Seq[WorkerView])

trait SparkClusterJsonFormatter extends CommonJsonFormatter {
  implicit val sparkConfigurationRequestFmt = Json.format[SparkConfigurationRequest]
  implicit val sparkClusterMetricFmt        = Json.format[SparkClusterMetric]
//  implicit val runStatusFmt                 = Json.formatEnum(RunStatus)
//  implicit val clusterStatusFmt             = Json.formatEnum(ClusterStatus)
  implicit val sparkclusterProcessFmt = Json.format[ClusterProcess]
  implicit val clusterInfoFmt         = Json.format[SparkClusterInfo]
  implicit val sparkWorkerViewFmt     = Json.format[WorkerView]
  implicit val masterFmt              = Json.format[SparkMasterSummary]
}

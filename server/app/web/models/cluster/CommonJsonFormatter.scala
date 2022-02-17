package web.models.cluster

import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.{ClusterStatus, RunStatus}
import web.models.cloud.ClusterProcess
import play.api.libs.json.Json

trait CommonJsonFormatter {
  implicit val runStatusFmt     = Json.formatEnum(RunStatus)
  implicit val clusterStatusFmt = Json.formatEnum(ClusterStatus)
}

trait ClusterPackage {
  val clusterId: Long
  val workspaceId: Long
}
case class SparkPackage(clusterId: Long,
                        workspaceId: Long,
                        status: ClusterStatus,
                        name: String,
                        version: String,
                        scalaVersion: String,
                        userConfig: Seq[String],
                        processes: Seq[ClusterProcess],
                        hadoopBinaryVersion: String = "2.7")
    extends ClusterPackage
case class KafkaPackage(clusterId: Long,
                        workspaceId: Long,
                        status: ClusterStatus,
                        name: String,
                        version: String,
                        scalaVersion: String,
                        processes: Seq[ClusterProcess])
    extends ClusterPackage

case class HadoopPackage(clusterId: Long,
                         workspaceId: Long,
                         status: ClusterStatus,
                         name: String,
                         version: String,
                         coreSite: Seq[String],
                         hdfsSite: Seq[String],
                         processes: Seq[ClusterProcess]
                        ) extends ClusterPackage
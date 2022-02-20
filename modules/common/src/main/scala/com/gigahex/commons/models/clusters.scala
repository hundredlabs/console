package com.gigahex.commons.models

import com.gigahex.commons.models
import com.gigahex.commons.models.ClusterProvider.ClusterProvider
import com.gigahex.commons.models.ClusterStatus.ClusterStatus

case class ClusterNode(id: String,
                       host: String,
                       cpuCores: Int,
                       memory: Long,
                       port: Option[Int] = None)

case class ClusterState(clusterId: Long,
                        activeApps: Int,
                        completedApps: Int,
                        failedApps: Int,
                        nodes: Array[ClusterNode])

object ClusterProvider extends Enumeration {
  type ClusterProvider = Value

  val LOCAL = Value("local")
  val SANDBOX = Value("sandbox")
  val PRODUCTION = Value("production")
  val SANDBOX_DOCKER = Value("sandbox-docker")
  val SANDBOX_K8s = Value("sandbox-kubernetes")
  val ONPREM_SPARK_STANDALONE = Value("spark-standalone")
  val ONPREM_K8S = Value("k8s")
  val ONPREM_YARN = Value("yarn")
  val AWS_EMR = Value("emr")
  val GCP_DATAPROC: models.ClusterProvider.Value = Value("dataproc")
  val DATABRICKS = Value("databricks")

  def withResourceManager(rm: String): Value = rm match {
    case x if x.startsWith("spark://") => ONPREM_SPARK_STANDALONE
    case x if x.equalsIgnoreCase("yarn") => ONPREM_YARN
    case x if x.startsWith("k8s://") => ONPREM_K8S
  }

  def withNameOpt(s: String): Value =
    values.find(_.toString == s).getOrElse(LOCAL)
}

object ClusterStatus extends Enumeration {
  type ClusterStatus = Value

  val UNKNOWN = Value("unknown")
  val STARTING = Value("starting")
  val NEW = Value("new")
  val DOWNLOADING = Value("downloading")
  val BOOTSTRAPPING = Value("bootstrapping")
  val RUNNING = Value("running")
  val WAITING = Value("waiting")
  val TERMINATING = Value("terminating")
  val TERMINATED = Value("terminated")
  val INACTIVE = Value("inactive")
  val DELETED = Value("deleted")
  val STOPPING = Value("stopping")
  val TERMINATED_WITH_ERRORS = Value("terminated_with_errors")
  val FAILED_WHEN_DOWNLOADING = Value("failed_while_downloading")

  def withNameOpt(s: String): Value =
    values.find(_.toString == s).getOrElse(UNKNOWN)
}


case class NewCluster(name: String,
                      provider: ClusterProvider,
                      region: String,
                      providerClusterId: Option[String],
                      status: ClusterStatus)

case class ClusterView(cid: Long,
                        name: String,
                       clusterId: String,
                       status: ClusterStatus,
                       serviceName: String,
                       serviceVersion: String,
                       provider: ClusterProvider,
                       created: String)
case class UpdateStatus(status: ClusterStatus)
case class ClusterPingResponse(clusterStatus: ClusterStatus,
                               deploymentWorks: Seq[Long])
case class ClusterIdResponse(clusterId: Long)

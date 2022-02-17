package web.cloud.gcp

import com.gigahex.commons.models.ClusterProvider

import scala.collection.JavaConverters._
import com.google.cloud.dataproc.v1.{ClusterControllerClient, ClusterControllerSettings, ListClustersRequest}
import web.models.cloud.CloudCluster

class DataprocProvider {

  def listClusters(region: String, project: String): Seq[CloudCluster] = {
    val endPoint = s"${region}-dataproc.googleapis.com:443"

    val clusterControllerSettings =
      ClusterControllerSettings.newBuilder()
        .setEndpoint(endPoint).build();

    val result = ClusterControllerClient.create(clusterControllerSettings)
      .listClusters(ListClustersRequest.newBuilder()
          .setProjectId(project)
          .setRegion(region)
        .setFilter("status.state = ACTIVE")
        .build())

    result.iterateAll().asScala.map(c => CloudCluster(c.getClusterName, c.getClusterUuid, ClusterProvider.GCP_DATAPROC)).toSeq

  }

}

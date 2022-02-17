package web.models.cloud

case class EMRView(name: String, clusterId: String, status: String, clusterArn: String, normalizedHours: Int)


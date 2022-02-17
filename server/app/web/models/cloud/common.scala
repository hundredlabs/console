package web.models.cloud

import com.gigahex.commons.models.ClusterProvider.ClusterProvider
import com.gigahex.commons.models.RunStatus
import com.gigahex.commons.models.RunStatus.RunStatus

case class CloudCluster(name: String, id: String, provider: ClusterProvider)
case class ClusterProcess(name: String, host: String, port: Int, pid: Option[Long] = None, status: RunStatus = RunStatus.NotStarted)

package web.models

import com.gigahex.commons.models.{AuthMethod, BasicSparkConfig, ClusterStatus, DeploymentAction, DeploymentActionLog, DeploymentActionLogReq, DeploymentActionUpdate, DeploymentRun, DeploymentRunInstance, DeploymentRunResult, DeploymentView, JobConfig, NewDeploymentRun, ReqDeploymentJobByName, RunCommand, RunSSHCommand, RunScript, RunSparkAppAction, RunSparkSubmission, RunStatus, RuntimeSparkConfig, TriggerMethod}
import play.api.libs.json._

case class DeploymentRunHistory(name: String, runs: Seq[DeploymentRunInstance])


trait DeploymentJsonFormat {

  implicit val triggerMethodFmt       = Json.formatEnum(TriggerMethod)
  implicit val runStatusFmt           = Json.formatEnum(RunStatus)
  implicit val runScriptFmt           = Json.format[RunScript]
  implicit val runCmdFmt              = Json.format[RunCommand]
  implicit val runSparkCmdFmt         = Json.format[RunSparkSubmission]
  implicit val basicSparkConfigFmt    = Json.format[BasicSparkConfig]
  implicit val runtimeSparkConfigFmt  = Json.format[RuntimeSparkConfig]
  implicit val runSparkAppActionFmt   = Json.format[RunSparkAppAction]
  implicit val authMethodFmt          = Json.formatEnum(AuthMethod)
  implicit val runSshCmd              = Json.format[RunSSHCommand]
  implicit val deploymentActionFmt    = Json.format[DeploymentAction]
  implicit val updateDeploymentRunFmt = Json.format[NewDeploymentRun]

  implicit val deploymentActionUpdateFmt: OFormat[DeploymentActionLog] = Json.format[DeploymentActionLog]

  implicit val deploymentRunResultFmt    = Json.format[DeploymentRunResult]
  implicit val deploymentRunFmt          = Json.format[DeploymentRun]
  implicit val deploymentActionRunFmt    = Json.format[DeploymentActionUpdate]
  implicit val clusterStatusFmt          = Json.formatEnum(ClusterStatus)
  implicit val deploymentConfigList      = Json.format[DeploymentView]
  implicit val deploymentRunInstFmt      = Json.format[DeploymentRunInstance]
  implicit val deploymentRunHistoryFmt   = Json.format[DeploymentRunHistory]
  implicit val deploymentActionLogReqFmt = Json.format[DeploymentActionLogReq]
  implicit val reqDeploymentJobByNameFmt = Json.format[ReqDeploymentJobByName]
}

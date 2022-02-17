package com.gigahex.commons.models

import com.gigahex.commons.models.AuthMethod.AuthMethod
import com.gigahex.commons.models.ClusterProvider.ClusterProvider
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.gigahex.commons.models.JobType.JobType
import com.gigahex.commons.models.RunStatus.RunStatus
import com.gigahex.commons.models.TriggerMethod.TriggerMethod

sealed trait DeploymentAction {
  val displayName: String
  val id: String
  val index: Int
}
case class DeploymentJob(depId: String,
                         clusterId: Long,
                         clusterProvider: ClusterProvider,
                         clusterName: String,
                         region: Option[String],
                         isExisting: Boolean,
                         jobType: JobType,
                         jobConfig: JobConfig)
sealed trait JobConfig {
  val jobName: String
}

case class NewDeploymentRequest(clusterId: Long, jobType: JobType, depId: String, jobConfig: JobConfig)

case class SparkJobConfig(override val jobName: String,
                          className: String,
                          artifactPath: String,
                          applicationParams: String,
                          driverMemory: Int,
                          executorMemory: Int,
                          executorCores: Int,
                          numExecutors: Int,
                          extraConf: Seq[String])
  extends JobConfig {

  def getCommandString: Array[String] = {

    val advancedConfig = extraConf.filter(!_.isEmpty).filter(!_.startsWith("#")).map{ str =>
      val confParts = str.split(" ")
      if(confParts.length == 2){
        Some(s"--conf ${confParts(0).trim}=${confParts(1).trim}")
      } else None
    }.filter(_.nonEmpty).map(_.get)

    val strAdvanceConf = if(advancedConfig.length > 0) advancedConfig.mkString(" ") else ""

    val appParams = applicationParams.replaceAll("\n", "")
    s"""spark-submit --name ${jobName} --class $className --executor-memory ${executorMemory}g --driver-memory ${driverMemory}g --executor-cores $executorCores
       |--num-executors $numExecutors $strAdvanceConf $artifactPath $appParams""".stripMargin.split(" ")
  }

}


case class RunScript(displayName: String, id: String, index: Int, protocol: String, scriptPath: String) extends DeploymentAction
case class RunCommand(displayName: String, id: String, index: Int, commands: Seq[String])               extends DeploymentAction
case class RunSparkSubmission(displayName: String, id: String, sparkSubmitCmd: String, monitoringEnabled: Boolean)
case class BasicSparkConfig(name: String, className: String, appPath: String, appParams: String, monitoringEnabled: Boolean)
case class RuntimeSparkConfig(rm: String, driverMem: Int, executorMem: Int, execCores: Int, numExecutors: Int, rawConfigs: Seq[String])
case class RunSparkAppAction(displayName: String, id: String, index: Int, basicConfig: BasicSparkConfig, runtimeConfig: RuntimeSparkConfig)
    extends DeploymentAction
object AuthMethod extends Enumeration {
  type AuthMethod = Value

  val PASSWORD   = Value("password")
  val PRIVATESSH = Value("privateSSH")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(PASSWORD)
}
object TriggerMethod extends Enumeration {
  type TriggerMethod = Value

  val MANUAL        = Value("manually")
  val AGENT_TRIGGER = Value("by agent")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(MANUAL)
}
case class RunSSHCommand(displayName: String,
                         id: String,
                         index: Int,
                         hostName: String,
                         port: Int,
                         loginName: String,
                         authMode: AuthMethod,
                         commands: Seq[String],
                         password: Option[String],
                         keyParaphrase: Option[String],
                         privateSSHKey: Option[String])
    extends DeploymentAction

case class DeploymentRun(runId: Long, runSeqId: Long, status: RunStatus)
case class DeploymentView(depId: Long,
                          name: String,
                          clusterId: Option[String],
                          clusterName: Option[String],
                          clusterStatus: Option[ClusterStatus],
                          history: Seq[DeploymentRun])
case class ReqDeploymentJobByName(name: String, project: String)
case class NewDeploymentRun(deploymentId: Long,
                            triggerMethod: TriggerMethod = TriggerMethod.AGENT_TRIGGER,
                            runStatus: RunStatus = RunStatus.Waiting)
case class DeploymentActionLog(actionName: String,
                               index: Int,
                               actionId: String,
                               status: RunStatus,
                               runtime: String,
                               jobRunId: Option[Long] = None,
                               jobId: Option[Long] = None)
case class DeploymentActionUpdate(runId: Long, actionId: String, status: RunStatus, logs: Seq[String])
case class DeploymentUpdate(runId: Long, status: RunStatus)
case class DeploymentRunResult(depConfigId: Long,
                               runIndex: Int,
                               triggerMethod: TriggerMethod,
                               status: RunStatus,
                               actionResults: Seq[DeploymentActionLog])
case class DeploymentRunInstance(runId: Long,
                                 runSeq: Long,
                                 status: RunStatus,
                                 triggered: String,
                                 runtime: String)
case class DeploymentRunResponse(runId: Long, request: NewDeploymentRequest)
case class DeploymentActionLogReq(runId: Long, projectId: Long, actionId: String)

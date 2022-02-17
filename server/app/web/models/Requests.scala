package web.models

import com.gigahex.commons.models.JobType.JobType
import play.api.libs.json._

sealed trait ProjectConfig
case class SparkProjectConfig(eventsInterval: Int, metricsInterval: Int, logsInterval: Int) extends ProjectConfig

case class BetaSignupRequest(name: String, email: String, password: String, receiveUpdates: Boolean)
case class AccessRequest(name: String, email: String, serverRegion: String)
case class AlpahRequest(email: String)
case class NameChangeRequest(name: String)
case class PasswordChangeRequest(email: String, oldPassword: String, newPassword: String)
case class SignInRequest(rememberMe: Boolean)
case class ListBetaRequest(pageSize: Int, pageNum: Int)
case class ApproveRequest(email: String)
case class SendActivationCode(email: String, code: String)

trait AuthRequestsJsonFormatter {

  implicit val passwordChangeRequestFmt = Json.format[PasswordChangeRequest]
  implicit val nameChangeRequestFmt     = Json.format[NameChangeRequest]
  implicit val signUpFmt                = Json.format[BetaSignupRequest]
  implicit val signInFmt                = Json.format[SignInRequest]
  implicit val accessFmt                = Json.format[AccessRequest]
  implicit val alphaReqFmt              = Json.format[AlpahRequest]
  implicit val lstBetaFmt               = Json.format[ListBetaRequest]
  implicit val sacFmt                   = Json.format[SendActivationCode]
  implicit val approveRequestFmt        = Json.format[ApproveRequest]
}

case class RegisterTask(name: String, taskType: String)
case class RegisterJob(name: String, description: Option[String], jobType: JobType, config: ProjectConfig)
case class JobRegistrationResult(jobId: Option[Long], message: String = "Job registered")
case class JobSubmissionResult(jobId: Long, runId: Option[Long], message: String = "Job submitted")
case class JobStats(avgRuntime: String, avgMemUtilized: String, avgCpuUtilized: String)
case class JobSummary(id: Long, name: String, description: String,  stats: JobStats)
case class ProjectByName(name: String)
case class JobModified(jobId: Long, success: Boolean, message: String = "")
case class UpdateJobStatus(status: String)
case class TimeFilter(startTime: Long, endTime: Long)

trait JobRequestResponseFormat {

  implicit val taskFormat: OFormat[RegisterTask] = Json.format[RegisterTask]
  implicit val projectByNameFmt      = Json.format[ProjectByName]
  implicit val jobSubmitResultFormat = Json.format[JobSubmissionResult]
  implicit val jobUpdateFormat       = Json.format[UpdateJobStatus]
  implicit val jobModifiedFmt        = Json.format[JobModified]
  implicit val projectStatsFmt       = Json.format[JobStats]
  implicit val projectSummaryFmt     = Json.format[JobSummary]


}

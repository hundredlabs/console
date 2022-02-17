package web.models

import java.time.ZonedDateTime

import web.models.MeasuringUnit.MeasuringUnit
import web.models.NotificationChannel.NotificationChannel
import web.models.Operator.Operator
import web.models.SparkMetric.SparkMetric
import play.api.libs.json._

case class SparkMetricRule(metric: SparkMetric, operator: Operator, opValue: Int, mu: MeasuringUnit)

object MeasuringUnit extends Enumeration {
  type MeasuringUnit = Value
  val Millisec = Value("ms")
  val Seconds  = Value("secs")
  val Minutes  = Value("mins")
  val Hours    = Value("hrs")
  val HasSucceeded = Value("boolean")

  implicit val muFmt = Json.formatEnum(MeasuringUnit)
}

object Operator extends Enumeration {
  type Operator = Value
  val Above  = Value("exceeds")
  val Equals = Value("equals")
  implicit val opFmt = Json.formatEnum(Operator)
}

object SparkMetric extends Enumeration {
  type SparkMetric = Value
  val AppStatus = Value("appFinalStatus")
  val AppRuntime           = Value("appRuntime")
  val TotalExecutorRuntime = Value("totalExecutorRuntime")
  val TotalJVMGCtime       = Value("totalJVMGCtime")
  val MaxTaskRuntime       = Value("maxTaskRuntime")
  implicit val metricFmt = Json.formatEnum(SparkMetric)
}

object NotificationChannel extends Enumeration {
  type NotificationChannel = Value
  val Email = Value("email")
  val Slack = Value("slack")

  implicit val nfcFmt = Json.formatEnum(NotificationChannel)
}

trait AlertRuleFmt {

  implicit val metricFmt = Json.format[SparkMetricRule]
  implicit val alertRuleFmt = Json.format[AddAlertReq]
  implicit val deleteAlertReqFmt = Json.format[DeleteAlertReq]
  implicit val getAlert = Json.format[GetAlertConfig]
  implicit val updateReqFmt = Json.format[UpdateAlertReq]
  implicit val alertConfigFmt = Json.format[AlertConfig]
  implicit val alertConfigViewFmt = Json.format[AlertConfigView]

}

case class AlertConfig(jobId: Long, alertId: Long, name: String, rule: SparkMetricRule, channelType: NotificationChannel, subscribers: Seq[String], dateCreated: Option[ZonedDateTime] = None)
case class AlertConfigView(alertId: Long, name: String, metric: String, channelType: String, created: String)
case class DeleteAlertReq(jobId: Long, alertId: Long)
case class GetAlertConfig(taskName: String)
case class UpdateAlertReq(alertId: Long, name: String, rule: SparkMetricRule)
case class AddAlertReq(name: String, rule: SparkMetricRule)

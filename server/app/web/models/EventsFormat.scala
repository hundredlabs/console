package web.models

import com.gigahex.commons.events._
import com.gigahex.commons.models.{
  AddMetricRequest,
  AggMetric,
  DriverLogs,
  ExecutorMetricMeta,
  GxAppAttemptMetric,
  GxAppState,
  GxApplicationMetricReq,
  GxExecutorMetric,
  GxJobMetricReq,
  NewExecutor,
  StageAggMetrics,
  StageMetric,
  TaskTimeline
}
import play.api.libs.json.Json

trait EventsFormat {

  implicit val aggMetricFmt     = Json.format[AggMetric]
  implicit val aggStageMetric   = Json.format[StageAggMetrics]
  implicit val stageTimelineFmt = Json.format[TaskTimeline]
  implicit val stgFmt           = Json.format[StageMetric]
  implicit val jmFmt            = Json.format[GxJobMetricReq]
  implicit val execMetricFmt    = Json.format[GxExecutorMetric]
  implicit val appAttemtpFmt    = Json.format[GxAppAttemptMetric]

  implicit val amFmt                = Json.format[GxApplicationMetricReq]
  implicit val gxAS                 = Json.format[GxAppState]
  implicit val driverLogFmt         = Json.format[DriverLogs]
  implicit val sparkConfFmt         = Json.format[SparkConfProps]
  implicit val appStartedFormat     = Json.format[ApplicationStarted]
  implicit val appEndedFormat       = Json.format[ApplicationEnded]
  implicit val stageMetaFmt         = Json.format[StageMeta]
  implicit val jobStartedFormat     = Json.format[JobStarted]
  implicit val jobEndedFormat       = Json.format[JobEnded]
  implicit val stageSubmittedFormat = Json.format[StageSubmitted]
  implicit val stageCompletedFormat = Json.format[StageCompleted]
  implicit val taskStartedFormat    = Json.format[TaskStarted]

  implicit val taskInputMetricsFormat = Json.format[InputMetrics]
  implicit val taskOutpuMetricsFormat = Json.format[OutputMetrics]

  implicit val taskCompletedtFormat = Json.format[TaskCompleted]
  //implicit val sparkEventsFormat    = Json.format[SparkEvents]
  implicit val newExecutorFmt   = Json.format[NewExecutor]
  implicit val execMetricMeta   = Json.format[ExecutorMetricMeta]
  implicit val execMetricReqFmt = Json.format[AddMetricRequest]

}

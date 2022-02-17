package web.repo

import web.models.spark.{MetricObservationResult, SparkStageSummary}

import scala.concurrent.Future


trait SparkMetricsAnalyzer {

  def analyzeStagesMetrics(jobId: Long, runId: Long, attemptId: Long): Future[Seq[MetricObservationResult]]

  def analyzeShortTasks(jobId: Long, runId: Long, attemptId: Long): Future[Option[MetricObservationResult]]

  def findStragglerTask(jobId: Long, runId: Long, attemptId: Long): Future[Option[MetricObservationResult]]

  def taskSerDeVsComputeTime(jobId: Long, runId: Long, attemptId: Long): Future[Option[MetricObservationResult]]

}

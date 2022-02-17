package web.actors

import com.gigahex.commons.models.{GxApplicationMetricReq, RunStatus}
import com.typesafe.scalalogging.LazyLogging
import web.actors.JobAlertScheduler.{AlertDetail, AppFinalStatus}
import web.models.MeasuringUnit.MeasuringUnit
import web.models.SparkMetricRule

trait AlertRulesHandler extends LazyLogging {

  def getOpValue(mu: MeasuringUnit, opValue: Int): Long = {
    mu match {
      case web.models.MeasuringUnit.Millisec => opValue
      case web.models.MeasuringUnit.Seconds  => opValue * 1000
      case web.models.MeasuringUnit.Minutes  => (opValue * 60) * 1000
      case web.models.MeasuringUnit.Hours    => (opValue * 3600) * 1000
    }
  }

  def handleAppRuntime(rule: SparkMetricRule, metric: GxApplicationMetricReq): Seq[AlertDetail] = {
    val runtime = metric.endTime map (_ - metric.startTime)
    runtime
      .flatMap { appRuntime =>
        val opValue = getOpValue(rule.mu, rule.opValue)

        rule.operator match {
          case web.models.Operator.Above if appRuntime > opValue =>
            Some(AlertDetail("", rule.metric, s"Application runtime crossed the limit of ${rule.opValue} ${rule.mu.toString}"))
          case _ => None
        }
      }
      .fold(Seq.empty[AlertDetail])(alert => Seq(alert))
  }

  def handleTotalExecRuntime(rule: SparkMetricRule, metric: GxApplicationMetricReq): Seq[AlertDetail] = {
    val opAlerts = metric.attempts.map { metric =>
      val opValue = getOpValue(rule.mu, rule.opValue)
      rule.operator match {
        case web.models.Operator.Above if metric.totalExecutorRuntime > opValue =>
          Some(AlertDetail(metric.attemptId, rule.metric, s"Total executor runtime crossed the limit of ${rule.opValue} ${rule.mu.toString}"))
        case _ => None
      }
    }

    opAlerts.filter(_.nonEmpty).map(_.get)

  }

  def handleTotalJVMGCtime(rule: SparkMetricRule, metric: GxApplicationMetricReq): Seq[AlertDetail] = {
    val opAlerts = metric.attempts.map { metric =>
      val opValue = getOpValue(rule.mu, rule.opValue)
      rule.operator match {
        case web.models.Operator.Above if metric.totalJvmGCtime > opValue =>
          Some(AlertDetail(metric.attemptId, rule.metric, s"Total JVM GC time crossed the limit of ${rule.opValue} ${rule.mu.toString}"))
        case _ => None
      }
    }

    opAlerts.filter(_.nonEmpty).map(_.get)

  }

  def handleAppStatus(rule: SparkMetricRule, metric: GxApplicationMetricReq): Seq[AlertDetail] = {
    if (rule.opValue == 0 && metric.status.equalsIgnoreCase(RunStatus.Succeeded.toString)) {
      Seq(
        AlertDetail(
          metric.attempts.map(_.attemptId).mkString(","),
          rule.metric,
          s"application has ${RunStatus.Succeeded.toString}",
          Some(AppFinalStatus(RunStatus.Succeeded))
        ))
    } else if (rule.opValue == 1 && metric.status.equalsIgnoreCase(RunStatus.Failed.toString)) {
      Seq(
        AlertDetail(metric.attempts.map(_.attemptId).mkString(","),
                    rule.metric,
                    s"application has ${RunStatus.Failed.toString}",
                    Some(AppFinalStatus(RunStatus.Failed))))
    } else {
      Seq()
    }
  }

  def handleMaxTaskRuntime(rule: SparkMetricRule, metric: GxApplicationMetricReq): Seq[AlertDetail] = {
    val opAlerts = metric.attempts.map { metric =>
      val opValue = getOpValue(rule.mu, rule.opValue)
      rule.operator match {
        case web.models.Operator.Above if metric.maxTaskTime > opValue =>
          Some(AlertDetail(metric.attemptId, rule.metric, s"Max task runtime crossed the limit of ${rule.opValue} ${rule.mu.toString}"))
        case _ => None
      }
    }

    opAlerts.filter(_.nonEmpty).map(_.get)

  }

}

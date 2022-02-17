package web.services

import akka.actor.ActorRef
import javax.inject.Inject
import web.models.{AlertConfig, AlertConfigView, AlertRuleFmt, SparkMetricRule}
import web.repo.AlertRepository
import web.utils.DateUtil
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

trait AlertEngine {

  def update(jobId: Long, alertId: Long, rule: SparkMetricRule, taskName: String, alertsManager: ActorRef): Future[Boolean]

  def upsert(jobId: Long, name: String, rule: SparkMetricRule, alertsManger: ActorRef): Future[Long]

  def remove(jobId: Long, alertId: Long): Future[Boolean]

  def addEmailChannel(orgId: Long, email: String): Future[Boolean]

  def getAlert(jobId: Long, alertId: Long): Future[Option[AlertConfig]]

  def listByJob(jobId: Long): Future[List[AlertConfigView]]

  def listAll: Future[List[AlertConfig]]

}

class AlertEngineImpl @Inject()(alertRepo: AlertRepository) extends AlertEngine with AlertRuleFmt {
  implicit val exc: ExecutionContext              = alertRepo.ec
  override def listAll: Future[List[AlertConfig]] = alertRepo.listAll

  override def upsert(jobId: Long, name: String, rule: SparkMetricRule, alertsManger: ActorRef): Future[Long] = {
    alertRepo.upsert(jobId, name, Json.toJson(rule).toString())
  }

  override def addEmailChannel(orgId: Long, email: String): Future[Boolean] = alertRepo.addEmailChannel(orgId, email)

  override def getAlert(jobId: Long, alertId: Long): Future[Option[AlertConfig]] = alertRepo.getConfig(jobId, alertId)

  override def update(jobId: Long, alertId: Long, rule: SparkMetricRule, taskName: String, alertsManager: ActorRef): Future[Boolean] =
    alertRepo.update(jobId, alertId, Json.toJson(rule).toString(), taskName)

  override def remove(jobId: Long, alertId: Long): Future[Boolean] = alertRepo.remove(jobId, alertId)

  override def listByJob(jobId: Long): Future[List[AlertConfigView]] = alertRepo.listByJob(jobId).map { xs =>
    xs.map(
      x =>
        AlertConfigView(
          alertId = x.alertId,
          name = x.name,
          metric = x.rule.metric.toString,
          channelType = x.channelType.toString,
          created = x.dateCreated.map(dt => DateUtil.timeElapsed(dt, None)).getOrElse("unknown")
      ))
  }

}

package web.repo

import java.time.ZonedDateTime
import java.util.Date

import web.models.{AlertConfig, AlertRuleFmt, NotificationChannel, SparkMetricRule}
import scalikejdbc._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future, blocking}

trait AlertRepository {

  val ec: ExecutionContext

  def listAll: Future[List[AlertConfig]]

  def addEmailChannel(orgId: Long, email: String): Future[Boolean]

  def upsert(jobId: Long, name: String, rules: String): Future[Long]

  def update(jobId: Long, alertId: Long, rules: String, name: String): Future[Boolean]

  def remove(jobId: Long, alertId: Long): Future[Boolean]

  def listByJob(jobId: Long): Future[List[AlertConfig]]

  def getConfig(jobId: Long, alertId: Long): Future[Option[AlertConfig]]
}

class AlertRepositoryImpl(blockingEC: ExecutionContext) extends AlertRepository with AlertRuleFmt {
  override implicit val ec: ExecutionContext = blockingEC
  implicit val session                       = AutoSession

  override def listAll: Future[List[AlertConfig]] = Future {
    blocking {

      val result =
        sql"""select ar.name,ar.alert_id,ar.job_id, ar.alert_rule, nc.channel_type, nc.subscribers from alert_spark_metrics_rules as ar INNER JOIN
                 jobs as j ON ar.job_id = j.id INNER JOIN notification_channels as nc ON j.org_id = nc.org_id"""
          .map(rs =>
            AlertConfig(
              jobId = rs.long("job_id"),
              alertId = rs.long("alert_id"),
              name = rs.string("name"),
              rule = Json.parse(rs.string("alert_rule")).as[SparkMetricRule],
              channelType = NotificationChannel.withName(rs.string("channel_type")),
              subscribers = Json.parse(rs.string("subscribers")).as[Seq[String]]
            ))
          .list()
          .apply()
      result
    }
  }

  override def addEmailChannel(orgId: Long, email: String): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        val emailJson = Json.toJson(Seq(email)).toString()
        //get the alertIds for all the jobs owned by this member


          sql"""INSERT INTO notification_channels(org_id, channel_type,create_ts ,subscribers)
                VALUES(${orgId}, 'email', ${ZonedDateTime.now()}, ${emailJson})
           """
            .update()
            .apply() == 1

      }
    }
  }
  override def upsert(jobId: Long, name: String, rules: String): Future[Long] = Future {
    blocking {
      val alertId = new Date().getTime
      DB localTx { implicit session =>
        sql"""INSERT INTO alert_spark_metrics_rules(alert_id, job_id, name, alert_rule, create_ts)
              VALUES(${alertId}, ${jobId}, ${name}, ${rules}, ${ZonedDateTime.now()})
             ON DUPLICATE KEY UPDATE
             alert_rule = ${rules}
           """
          .update()
          .apply()
        val persistedId =
          sql"""SELECT alert_id FROM alert_spark_metrics_rules
               WHERE job_id = ${jobId} AND name = ${name}""".map(_.long("alert_id")).single().apply()
        persistedId.getOrElse(-1)
      }
    }
  }

  override def update(jobId: Long, alertId: Long, rules: String, name: String): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""UPDATE alert_spark_metrics_rules SET
              name = ${name},
             alert_rule = ${rules}
             WHERE alert_id = ${alertId}
             AND job_id = ${jobId}
           """
          .update()
          .apply() == 1
      }
    }
  }

  override def remove(jobId: Long, alertId: Long): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""DELETE FROM alert_spark_metrics_rules WHERE job_id = ${jobId} AND alert_id = ${alertId}"""
          .update()
          .apply() == 1
      }
    }
  }

  override def listByJob(jobId: Long): Future[List[AlertConfig]] = Future {
    blocking {
      val result =
        sql"""select ar.name,ar.alert_id, ar.alert_rule, nc.channel_type, ar.create_ts, nc.subscribers from alert_spark_metrics_rules as ar
              INNER JOIN jobs as j ON ar.job_id = j.id
              INNER JOIN notification_channels as nc ON j.org_id = nc.org_id
              WHERE ar.job_id = ${jobId} """
          .map(rs =>
            AlertConfig(
              jobId = jobId,
              alertId = rs.long("alert_id"),
              name = rs.string("name"),
              rule = Json.parse(rs.string("alert_rule")).as[SparkMetricRule],
              channelType = NotificationChannel.withName(rs.string("channel_type")),
              subscribers = rs.string("subscribers").split(","),
              dateCreated = rs.zonedDateTimeOpt("create_ts")
          ))
          .list()
          .apply()
      result
    }
  }

  override def getConfig(jobId: Long, alertId: Long): Future[Option[AlertConfig]] = Future {
    blocking {
      val result =
        sql"""select ar.name,ar.alert_id, ar.alert_rule, nc.channel_type, ar.create_ts, nc.subscribers from alert_spark_metrics_rules as ar
              INNER JOIN jobs as j ON ar.job_id = j.id
              INNER JOIN notification_channels as nc ON j.org_id = nc.org_id
              WHERE ar.job_id = ${jobId} AND ar.alert_id = ${alertId}"""
          .map(rs =>
            AlertConfig(
              jobId = jobId,
              alertId = rs.long("alert_id"),
              name = rs.string("name"),
              rule = Json.parse(rs.string("alert_rule")).as[SparkMetricRule],
              channelType = NotificationChannel.withName(rs.string("channel_type")),
              subscribers = rs.string("subscribers").split(","),
              dateCreated = rs.zonedDateTimeOpt("create_ts")
            ))
          .single()
          .apply()
      result
    }
  }
}

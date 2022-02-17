package web.actors

import java.util

import akka.actor.{Actor, ActorLogging, Props}
import web.actors.EmailSender.{MailSent, SendAlert}
import web.actors.JobAlertScheduler.AlertDetail
import org.apache.commons.text.StringSubstitutor
import play.api.libs.mailer.{Email, MailerClient}

import scala.io.{Codec, Source}

class EmailSender(mailer: MailerClient, from: String, to: Seq[String]) extends Actor with ActorLogging {

  override def receive: Receive = {
    case x: SendAlert =>
      try {
        val mailContent = buildEmail(x.alert, x.jobId, x.runId, x.appName)
        val id = mailer.send(mailContent)
        context.parent ! MailSent(Some(id), true)
      } catch {
        case e: Exception =>
          log.error(e.getMessage)
          context.parent ! MailSent(None, false)
      }
  }

  private def buildEmail(detail: AlertDetail, jobId: Long, runId: Long,  appName: String): Email = {
    val emailTitle = s"🔔 Alert for ${appName}: ${detail.alertMessage}"
    val emailHtmlBody = detail.metric match {
      case web.models.SparkMetric.AppStatus if detail.appFinalStatus.isDefined => detail.appFinalStatus.get.status match {
        case com.gigahex.commons.models.RunStatus.Succeeded =>
          Source.fromFile(this.getClass.getResource("/emails/app_succeeded.html").getFile)(Codec.UTF8).mkString("")
        case _ => Source.fromFile(this.getClass.getResource("/emails/time_exceeded.html").getFile)(Codec.UTF8).mkString("")
      }
      case _ => Source.fromFile(this.getClass.getResource("/emails/time_exceeded.html").getFile)(Codec.UTF8).mkString("")
    }
    val jMap       = new util.HashMap[String, String]()
    jMap.put("appUrl", s"https://app.gigahex.com/spark/jobs/${jobId}/run/${runId}/overview")
    jMap.put("message", s"${detail.alertMessage}")
    jMap.put("appName", s"${appName}")
    val sub       = new StringSubstitutor(jMap)
    val emailHTML = sub.replace(emailHtmlBody)

    Email(
      emailTitle,
      from = s"Gigahex Alerts <${from}>",
      to = to,
      bodyHtml = Some(emailHTML)
    )
  }

}

object EmailSender {
  case class MailSent(id: Option[String], success: Boolean)
  case class SendAlert(jobId: Long, runId: Long, appName: String, alert: AlertDetail)
  def props(mailerClient: MailerClient, from: String, to: Seq[String]): Props = Props(new EmailSender(mailerClient, from, to))
}

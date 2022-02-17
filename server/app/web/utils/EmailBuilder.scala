package web.utils

import java.util.Date

import play.api.libs.mailer.{Email, MailerClient}
object EmailBuilder {

  def buildShutDownReport(adminEmail: String): Email = {
    val currentTime = new Date().toString
    val report = s"Application shutdown at $currentTime"
    Email(
      "Application shutdown",
      from = s"Reporter <${adminEmail}>",
      to = Seq(s"Shad<${adminEmail}>"),
      bodyText = Some(report)
    )
  }

}

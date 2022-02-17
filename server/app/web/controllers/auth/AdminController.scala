package web.controllers.auth

import java.util

import com.mohiva.play.silhouette.api.Silhouette
import controllers.AssetsFinder
import javax.inject.Inject
import org.apache.commons.text.StringSubstitutor
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Reads, _}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.{BodyParser, ControllerComponents, InjectedController}
import play.api.{Configuration, Logging}
import utils.auth.DefaultEnv
import web.models._
import web.models.formats.AuthResponseFormats
import web.services.MemberService

import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}

class AdminController @Inject()(
                                 components: ControllerComponents,
                                 silhouette: Silhouette[DefaultEnv],
                                 memberService: MemberService,
                                 mailerClient: MailerClient,
                                 configuration: Configuration,
)(
    implicit
    assets: AssetsFinder,
    ex: ExecutionContext
) extends InjectedController
    with I18nSupport
    with AuthRequestsJsonFormatter
    with AuthResponseFormats
    with ErrorResponse
    with Logging {

  def validateJson[A: Reads]: BodyParser[A] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def listApprovedRequests = silhouette.UserAwareAction.async(validateJson[ListBetaRequest]) { implicit request =>
    val local = configuration.underlying.getString("admin.email")

    request.identity match {
      case Some(admin) if admin.email.equalsIgnoreCase(local) =>
        memberService.listApprovedRequests(request.body.pageNum, request.body.pageSize).map(xs => Ok(Json.toJson(xs)))
      case _ => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }

  }

  def listAlphaRequests = silhouette.UserAwareAction.async(validateJson[ListBetaRequest]) { implicit request =>
    val local = configuration.underlying.getString("admin.email")

    request.identity match {
      case Some(admin) if admin.email.equalsIgnoreCase(local) =>
        memberService.listAlphaUsers(request.body.pageNum, request.body.pageSize).map(xs => Ok(Json.toJson(xs)))
      case _ => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }

  }

  def approveAccessRequest = silhouette.UserAwareAction.async(validateJson[ApproveRequest]) { implicit request =>
    val local = configuration.underlying.getString("admin.email")

    val result = request.identity match {
      case Some(admin) if admin.email.equalsIgnoreCase(local) =>
        for {
          code <- memberService.approveRequest(request.body.email)
          _ <- if (code.isDefined) Future(mailerClient.send(buildActivateEmail(code.get, request.body.email)))
          else Future.failed(new IllegalArgumentException("No email found"))
          hasUpdated <- memberService.updateApproveStatus(request.body.email)
        } yield {
          Ok(Json.parse(s"""{"approved": $hasUpdated}"""))
        }
      case _ => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }
    result.recover {
      case e: Exception =>
        e.printStackTrace()
        NotFound(Json.toJson(EntityNotFound("email", request.path, -1, e.getMessage)))
    }

  }

  def sendTestMail = silhouette.UserAwareAction.async { implicit request =>
    val local = configuration.underlying.getString("admin.email")

    request.identity match {
      case Some(admin) if admin.email.equalsIgnoreCase(local) =>
        Future(mailerClient.send(buildActivateEmail("lsdflksklfklsdhflks", "shad.amezng@gmail.com"))).map(_ => Ok)

      case _ => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }
  }

  private def buildWelcomeEmail(guestEmail: String, name: String): Email = {
    val adminEmail = configuration.underlying.getString("admin.email")
    val emailBody  = Source.fromFile(this.getClass.getResource("/emails/welcome_to_alpha.html").getFile).mkString("")
    val jMap       = new util.HashMap[String, String]()
    jMap.put("docsUrl", "https://docs.gigahex.com/")
    jMap.put("name", name)
    val sub       = new StringSubstitutor(jMap)
    val emailHTML = sub.replace(emailBody)
    Email(
      s"👋 Hey, Welcome to Gigahex",
      from = s"Shad<${adminEmail}>",
      to = Seq(s"Guest<${guestEmail}>"),
      bodyHtml = Some(emailHTML)
    )
  }

  def sendActivationCode = silhouette.UserAwareAction.async(validateJson[SendActivationCode]) { implicit request =>
    val local = configuration.underlying.getString("admin.email")

    request.identity match {
      case Some(admin) if admin.email.equalsIgnoreCase(local) =>
        val id = mailerClient.send(buildActivateEmail(request.body.code, request.body.email))
        Future.successful(Ok(s"$id"))
      case _ => Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }

  }

  private def buildActivateEmail(code: String, guestEmail: String): Email = {
    val adminEmail = configuration.underlying.getString("alert.email")
    val emailPath  = getClass.getResource("/emails/activate_account.html").getFile
    logger.info(s"email path : ${emailPath}")
    val emailBody = Source.fromFile(emailPath)(Codec.UTF8).mkString("")
    val jMap      = new util.HashMap[String, String]()
    jMap.put("activationUrl", s"https://app.gigahex.com/alpha/activation/${code}")
    jMap.put("docsUrl", "https://docs.gigahex.com/")
    jMap.put("name", "Shad")
    val sub       = new StringSubstitutor(jMap)
    val emailHTML = sub.replace(emailBody)
    Email(
      s"👋 Hey, Your waitlist application has been approved!",
      from = s"Gigahex<${adminEmail}>",
      to = Seq(guestEmail),
      bodyHtml = Some(emailHTML)
    )
  }

  def hello = Action.async { implicit request =>
    Future.successful(Ok(s"${request.path}"))
  }

}

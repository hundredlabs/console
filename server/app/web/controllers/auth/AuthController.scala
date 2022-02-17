package web.controllers.auth

import java.util

import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util.{Clock, Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.api.{LoginEvent, LoginInfo, LogoutEvent, Silhouette}
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import controllers.AssetsFinder
import javax.inject.Inject
import net.ceedubs.ficus.Ficus._
import org.apache.commons.text.StringSubstitutor
import play.api.http.HeaderNames
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc._
import play.api.{Configuration, Logging}
import utils.auth.DefaultEnv
import web.models._
import web.models.formats.AuthResponseFormats
import web.providers.{DesktopTokenCredProvider, EmailCredentialsProvider}
import web.services.{AuthTokenService, AuthenticateService, MemberService, SecretStore}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}

class AuthController @Inject()(
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    credentialsProvider: EmailCredentialsProvider,
    passwordHasherRegistry: PasswordHasherRegistry,
    mailerClient: MailerClient,
    configuration: Configuration,
    authenticateService: AuthenticateService,
    desktopTokenCredProvider: DesktopTokenCredProvider,
    authTokenService: AuthTokenService,
    secretStore: SecretStore,
    clock: Clock
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

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def betaSignup: Action[BetaSignupRequest] = silhouette.UnsecuredAction.async(validateJson[BetaSignupRequest]) { implicit request =>
    val newMember = request.body
    val loginInfo = LoginInfo(CredentialsProvider.ID, newMember.email)
    memberService.retrieve(loginInfo).flatMap {
      case Some(m) =>
        Future.successful(Ok(Json.toJson(SignupResponse(false, None, "User already exists with this email"))))
      case None =>
        val authInfo = passwordHasherRegistry.current.hash(newMember.password)
        val credInfo = CredentialInfo(newMember.email, authInfo.hasher, authInfo.password, authInfo.salt)
        val member   = Member(newMember)
        val result = for {
          memberId  <- memberService.save(member, secretStore)
          _         <- authenticateService.addAuthenticateMethod(memberId, loginInfo, credInfo)
          authToken <- authTokenService.create(memberId)

        } yield {
          mailerClient.send(buildConfirmEmail(member.email, member.name, authToken.id))
          memberId
        }
        result.map { id =>
          Ok(Json.toJson(SignupResponse(true, Some(id))))
        }
    }
  }

  private def buildConfirmEmail(guestEmail: String, name: String, token: String): Email = {
    val adminEmail      = configuration.underlying.getString("alert.email")
    val appRootUrl      = configuration.underlying.getString("appRootUrl")
    val welcomeMailPath = getClass.getResource("/emails/confirm_email.html").getFile
    val emailBody       = Source.fromFile(welcomeMailPath)(Codec.UTF8).mkString("")
    val jMap            = new util.HashMap[String, String]()
    jMap.put("confirmUrl", s"${appRootUrl}/email/confirm/${token}")
    jMap.put("name", name)
    val sub       = new StringSubstitutor(jMap)
    val emailHTML = sub.replace(emailBody)
    Email(
      s"Confirm your email to activate your account",
      from = s"Gigahex Team<${adminEmail}>",
      to = Seq(guestEmail),
      bodyHtml = Some(emailHTML)
    )
  }

  private def buildWelcomeEmail(guestEmail: String, name: String): Email = {
    val adminEmail      = configuration.underlying.getString("alert.email")
    val welcomeMailPath = getClass.getResource("/emails/welcome_to_alpha.html").getFile
    val emailBody       = Source.fromFile(welcomeMailPath)(Codec.UTF8).mkString("")
    val jMap            = new util.HashMap[String, String]()
    jMap.put("docsUrl", "https://docs.gigahex.com/")
    jMap.put("name", name)
    val sub       = new StringSubstitutor(jMap)
    val emailHTML = sub.replace(emailBody)
    Email(
      s"👋 Hey, Welcome to Gigahex",
      from = s"Gigahex Team<${adminEmail}>",
      to = Seq(guestEmail),
      bodyHtml = Some(emailHTML)
    )
  }

  def getEmail(code: String) = silhouette.UnsecuredAction.async { implicit request =>
    memberService
      .getEmailByCode(code)
      .map { emailOpt =>
        emailOpt match {
          case None =>
            val msg = "Invalid activation code used. Contact support@gigahex.com"
            NotFound(Json.parse(s"""{"message": "${msg}"}"""))
          case Some(email) => Ok(Json.parse(s"""{"email": "${email}"}"""))
        }
      }
      .recover {
        case e: Exception => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
      }

  }

  def accessRequest = silhouette.UnsecuredAction.async(validateJson[AccessRequest]) { implicit request =>
    memberService
      .saveAccessRequest(request.body)
      .map { reqId =>
        val response = mailerClient.send(buildEmail(reqId, request.body.name, request.body.email, request.body.serverRegion))
        reqId
      }
      .map(id => Created(Json.parse(s"""{"reqId": ${id}}""")))
      .recover {
        case e: Exception =>
          e.getStackTrace.map(msg => logger.error(msg.toString))
          e.printStackTrace()
          InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
      }
  }

  def confirmEmail(token: String) = silhouette.UnsecuredAction.async { implicit request =>
    authTokenService.validate(token).flatMap {
      case None =>
        Future.successful(
          Ok(
            Json.obj("message" -> "Token has expired or is invalid. Contact support@gigahex.com to report this issue", "success" -> false)))
      case Some(value) =>
        memberService.find(value.memberId).flatMap {
          case None =>
            Future.successful(
              Ok(Json.obj("message" -> "This token is invalid.Contact support@gigahex.com to report this issue.", "success" -> false)))
          case Some(value) =>
            memberService
              .setActivated(value)
              .map(v =>
                if (v) Ok(Json.obj("message" -> "Your account has been activated. Proceed to login", "success" -> v))
                else Ok(Json.obj("message"   -> "Account activation failed ", "success"                        -> v)))
        }
    }
  }

  def memberApiStatus = silhouette.UnsecuredAction.async { implicit request =>
    memberService
      .listApprovedRequests(0, 3)
      .map(result => Ok(Json.parse(s"""{"requests": ${result.total}}""")))
      .recover {
        case e: Exception => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
      }

  }

  def alphaRequest = silhouette.UnsecuredAction.async(validateJson[AlpahRequest]) { implicit request =>
    memberService
      .saveAlphaRequest(request.body)
      .map { reqId =>
        val response = mailerClient.send(buildEmail(reqId, "Guest from Alpha", request.body.email, "NA"))
        reqId
      }
      .map(id => Created(Json.parse(s"""{"reqId": ${id}}""")))
      .recover {

        case e: Exception =>
          e.printStackTrace()
          InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage)))
      }

  }

  private def buildEmail(reqId: Long, guestName: String, guestEmail: String, region: String): Email = {
    val adminEmail = configuration.underlying.getString("admin.email")
    Email(
      s"Request #${reqId} from ${guestName}",
      from = s"Shad <${adminEmail}>",
      to = Seq(s"Shad<${adminEmail}>"),
      bodyText = Some(s"Received request from ${guestEmail} with region - ${region}")
    )
  }
  private[this] def getCredentials(request: RequestHeader): Option[Credentials] = {
    request.headers.get(HeaderNames.AUTHORIZATION) match {
      case Some(header) if header.startsWith("Basic ") =>
        Base64.decode(header.replace("Basic ", "")).split(":", 2) match {
          case credentials if credentials.length == 2 => Some(Credentials(credentials(0), credentials(1)))
          case _                                      => None
        }
      case _ => None
    }
  }

  def signIn = silhouette.UnsecuredAction.async(validateJson[SignInRequest]) { implicit request =>
    val credentialsOpt = getCredentials(request)
    credentialsOpt match {
      case None =>
        Future {
          Unauthorized(
            Json.toJson(
              SignInResponse(false, "", None, UNAUTHORIZED, "Credentials Not Found", Map("password" -> "Incorrect password entered"))))
        }
      case Some(credentials) =>
        val memberOpt = for {
          loginInfo <- credentialsProvider.authenticate(credentials)
          member    <- memberService.retrieve(loginInfo)

        } yield member
        memberOpt
          .flatMap {
            case Some(m) =>

                authenticateUser(m, request.body.rememberMe)

            case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
          }
          .recover {
            case e: IdentityNotFoundException =>
              Unauthorized(
                Json.toJson(SignInResponse(false, "", None, UNAUTHORIZED, e.getClass.getName, Map("email" -> "No user with this email"))))
            case e: InvalidPasswordException =>
              Unauthorized(
                Json.toJson(
                  SignInResponse(false, "", None, UNAUTHORIZED, e.getClass.getName, Map("password" -> "Incorrect password entered"))))
          }
    }

  }

  def desktopSignIn = Action.async { implicit request =>
    val loginToken = request.headers.get("DESKTOP_TOKEN")
    loginToken match {
      case None =>
        Future {
          Unauthorized(Json.toJson(SignInResponse(false, "", None, UNAUTHORIZED, "Credentials Not Found", Map("token" -> "Not Found"))))
        }
      case Some(token) =>
        val memberOpt = for {

          member <- desktopTokenCredProvider.authenticateWith(token)

        } yield member
        memberOpt
          .flatMap {
            case Some(m) =>
              if (m.activated) {
                authenticateUser(m, true)
              } else {
                Future {
                  Unauthorized(Json.toJson(
                    SignInResponse(false, "", None, UNAUTHORIZED, "Account not activated", Map("email" -> "Account not activated"))))
                }
              }
            case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
          }
          .recover {
            case e: IdentityNotFoundException =>
              Unauthorized(
                Json.toJson(SignInResponse(false, "", None, UNAUTHORIZED, e.getClass.getName, Map("email" -> "No user with this email"))))
            case e: InvalidPasswordException =>
              Unauthorized(
                Json.toJson(
                  SignInResponse(false, "", None, UNAUTHORIZED, e.getClass.getName, Map("password" -> "Incorrect password entered"))))
          }
    }

  }

  /**
    * Handles the Sign Out action.
    *
    * @return The result to display.
    */
  def signOut = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    val result = Ok(Json.toJson(SignOutResponse()))
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, result)
  }

  protected def authenticateUser(user: Member, rememberMe: Boolean)(implicit request: Request[_]): Future[AuthenticatorResult] = {
    val c      = configuration.underlying
    val result = Ok(Json.toJson(SignInResponse(true, email = user.email, userName = Some(user.name))))
    silhouette.env.authenticatorService
      .create(user.loginInfo)
      .map {
        case authenticator if rememberMe =>
          authenticator.copy(
            expirationDateTime = clock.now + c.as[FiniteDuration]("silhouette.authenticator.rememberMe.authenticatorExpiry"),
            idleTimeout = c.getAs[FiniteDuration]("silhouette.authenticator.rememberMe.authenticatorIdleTimeout"),
            cookieMaxAge = c.getAs[FiniteDuration]("silhouette.authenticator.rememberMe.cookieMaxAge")
          )
        case authenticator => authenticator
      }
      .flatMap { authenticator =>
        silhouette.env.eventBus.publish(LoginEvent(user, request))
        silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
          silhouette.env.authenticatorService.embed(v, result)
        }
      }
  }

}

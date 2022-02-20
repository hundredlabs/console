package web.controllers.auth

import java.util

import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.util.{Clock, Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.api.{LoginEvent, LoginInfo, LogoutEvent, Silhouette}
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import controllers.AssetsFinder
import javax.inject.Inject
import net.ceedubs.ficus.Ficus._
import play.api.http.HeaderNames
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.mvc._
import play.api.{Configuration, Logging}
import utils.auth.DefaultEnv
import web.models._
import web.models.formats.AuthResponseFormats
import web.providers.{DesktopTokenCredProvider, EmailCredentialsProvider}
import web.services.{AuthTokenService, AuthenticateService, MemberService, SecretStore}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class AuthController @Inject()(
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    credentialsProvider: EmailCredentialsProvider,
    configuration: Configuration,
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

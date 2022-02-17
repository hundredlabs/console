package web.controllers.read

import java.time.ZonedDateTime

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.api.Authenticator.Implicits._
import javax.inject.Inject
import web.models.{ CredentialInfo, Member, MemberType, SignInResponse}
import web.services.{AccountBound, AuthenticateService, EmailIsBeingUsed, NoEmailProvided}
import play.api.libs.json.Json
import play.api.mvc._
import net.ceedubs.ficus.Ficus._
import play.api.Configuration
import web.models.formats.AuthResponseFormats

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class SocailAuthController @Inject()(
                                      configuration: Configuration,
                                      authenticateService: AuthenticateService,
    scc: SilhouetteControllerComponents
)(implicit ex: ExecutionContext)
    extends SilhouetteController(scc)  with AuthResponseFormats {

  /**
    * Authenticates a user against a social provider.
    *
    * @param provider The ID of the provider to authenticate against.
    * @return The result to display.
    */
  def authenticateWithSocial(provider: String) = Action.async { implicit request: Request[AnyContent] =>

    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialProvider with CommonSocialProfileBuilder) =>
        p.authenticate().flatMap {
          case Left(result) => Future.successful(result)
          case Right(authInfo) =>
            for {
              profile <- p.retrieveProfile(authInfo)
              userBindResult <- authenticateService.provideUserForSocialAccount(provider, profile, authInfo)
              result <- userBindResult match {
                case NoEmailProvided => Future.successful(BadRequest(Json.obj("error" -> "No Email Found")))
                case EmailIsBeingUsed(providers) =>
                  Future.successful(Conflict(Json.obj("error" -> "EmailIsBeingUsed", "providers" -> providers)))
                case AccountBound(m) =>
                  authenticateUser(m, rememberMe = true)
              }
            } yield {
              eventBus.publish(LoginEvent(Member(profile), request))
              result
            }
        }
      case _ => Future.failed(new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
    }).recover {
      case e: ProviderException =>
        logger.error("Unexpected provider error", e)
        Redirect("/error?message=socialAuthFailed")
    }
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

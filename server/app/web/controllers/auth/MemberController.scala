package web.controllers.auth

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.crypto.Base64
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import javax.inject.Inject
import play.api.http.HeaderNames
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents, InjectedController, RequestHeader}
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler
import web.models.requests.{CreateOrUpdateOrg, OrgRequestsJsonFormat}
import web.models._
import web.models.formats.AuthResponseFormats
import web.providers.EmailCredentialsProvider
import web.services.{MemberService, OrgService}

import scala.concurrent.{ExecutionContext, Future}

class MemberController @Inject()(
                                  components: ControllerComponents,
                                  silhouette: Silhouette[DefaultEnv],
                                  credentialsProvider: EmailCredentialsProvider,
                                  passwordHasherRegistry: PasswordHasherRegistry,
                                  orgService: OrgService,
                                  memberService: MemberService,

)(
    implicit
    ex: ExecutionContext
) extends InjectedController
    with I18nSupport
    with AuthResponseFormats
    with AuthRequestsJsonFormatter
    with SecuredWebRequestHandler
    with ErrorResponse
    with OrgRequestsJsonFormat {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )


  def accountDetails = silhouette.UserAwareAction.async { implicit request =>

    val result = request.identity match {
      case Some(m) if m.id.isDefined =>
        for {
         member <- memberService.find(m.id.get)
         profile <- memberService.getMemberProfile(m.id.get)
        } yield {
          member match {
            case None => MemberInfoResponse(false, false)
            case Some(value) => MemberInfoResponse(true, profile.isDefined, email = Some(value.email), name = Some(value.name),  id = value.id, profile = profile)
          }
        }

      case _ => {
        Future.successful(MemberInfoResponse(false, false))
      }
    }
    result.map(r => Ok(Json.toJson(r)))
  }


  def changeName: Action[NameChangeRequest] = silhouette.UserAwareAction.async(validateJson[NameChangeRequest]) { implicit request =>
    request.identity match {
      case Some(m) if m.id.nonEmpty =>
        memberService.updateMemberName(request.body.name, m.id.get).map(x => Ok(Json.parse(s"""{"nameChanged": ${x}}""")))
      case _ => {
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def changePassword = silhouette.UserAwareAction.async(validateJson[PasswordChangeRequest]) { implicit request =>
    request.identity match {
      case Some(m) if m.id.nonEmpty =>
        val creds    = Credentials(request.body.email, request.body.oldPassword)
        val authInfo = passwordHasherRegistry.current.hash(request.body.newPassword)
        val hasCredsChanged = for {
          loginInfo <- credentialsProvider.authenticate(creds)
          hasChanged <- memberService
            .updateCredentials(loginInfo, CredentialInfo(request.body.email, authInfo.hasher, authInfo.password, authInfo.salt))
        } yield hasChanged.id.isDefined

        hasCredsChanged
          .map { c =>
            Ok(Json.parse(s"""{"passwordChanged": $c}"""))
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

      case _ =>
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }
  }

  def memberApiStatus = silhouette.UserAwareAction.async { implicit request =>
    val result = request.identity match {
      case Some(m) if m.id.isDefined => {
        for {
          member <- memberService.find(m.id.get)
          profile <- memberService.getMemberProfile(m.id.get)
        } yield {
          member match {
            case None => MemberInfoResponse(false, false)
            case Some(value) => MemberInfoResponse(true, profile.isDefined, email = Some(value.email))
          }
        }
      }
      case _ =>
        Future.successful(new MemberInfoResponse(false, false))
    }
    result.map(r => Ok(Json.toJson(r)))
  }

  def activateAccount = silhouette.UserAwareAction.async { implicit request =>
    val result = request.identity match {
      case Some(m) if m.id.isDefined => {
        for {
          member <- memberService.find(m.id.get)
          profile <- memberService.getMemberProfile(m.id.get)
        } yield {
          member match {
            case None => MemberInfoResponse(false, false)
            case Some(value) => MemberInfoResponse(true, profile.isDefined, email = Some(value.email))
          }
        }
      }
      case _ =>
        Future.successful(MemberInfoResponse(false, false))
    }
    result.map(r => Ok(Json.toJson(r)))
  }

  def listOrgsWithKeys = silhouette.UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(m) if m.id.nonEmpty =>
        orgService.listOrgsWithKeys(m.id.get).map(x => Ok(Json.toJson(x)))
      case _ =>
        Future.successful(Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
    }
  }

  def fetchOrgDetails = silhouette.UnsecuredAction.async { implicit request =>
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
            case Some(m) => orgService.listOrgsWithKeys(m.id.get).map(x => Ok(Json.toJson(x)))
            case None    => Future.failed(new IdentityNotFoundException("Couldn't find user"))
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

}

package web.services

import java.time.ZonedDateTime
import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, CredentialsProvider, SocialProviderRegistry}
import javax.inject.Inject
import web.models.{Member, MemberType}
import web.repo.LoginInfoRepo

import scala.concurrent.{ExecutionContext, Future}

class AuthenticateService @Inject()(credentialsProvider: CredentialsProvider,
                                    memberService: MemberService,
                                    authInfoRepository: AuthInfoRepository,
                                    loginInfoRepo: LoginInfoRepo,
                                    secretStore: SecretStore,
                                    socialProviderRegistry: SocialProviderRegistry)(implicit ec: ExecutionContext) {
  // implicit val timeout: Timeout = 5.seconds

//  def credentials(email: String, password: String): Future[AuthenticateResult] = {
//    (bruteForceDefenderActor ? IsSignInAllowed(email)).flatMap {
//      case SignInAllowed(attemptsAllowed) =>
//        val credentials = Credentials(email, password)
//        credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
//          userService.retrieve(loginInfo).map {
//            case Some(user) if !user.activated =>
//              NonActivatedUserEmail
//            case Some(user) =>
//              Success(user)
//            case None =>
//              UserNotFound
//          }
//        }.recoverWith {
//          case _: InvalidPasswordException =>
//            // TODO refactor this. Put InvalidCredentials event to Silhouette's EventBus and listen to it in BruteForceDefenderActor
//            bruteForceDefenderActor ! RegisterWrongPasswordSignIn(email)
//            Future.successful(InvalidPassword(attemptsAllowed))
//          case _: IdentityNotFoundException =>
//            Future.successful(UserNotFound)
//          case e =>
//            Future.failed(e)
//        }
//      case SignInForbidden(nextSignInAllowedAt) =>
//        Future.successful(ToManyAuthenticateRequests(nextSignInAllowedAt))
//    }
//  }

  /**
    * Creates or fetches existing user for given social profile and binds it with given auth info
    *
    * @param provider social authentication provider
    * @param profile  social profile data
    * @param authInfo authentication info
    * @tparam T type of authentication info
    * @return
    *         NoEmailProvided if social profile email is empty
    *         EmailIsBeingUsed if there is existing user with email which eq to given social profile email and user
    *           has no authentication providers for given provider
    *         AccountBind if everything is ok
    */
  def provideUserForSocialAccount[T <: AuthInfo](provider: String,
                                                 profile: CommonSocialProfile,
                                                 authInfo: T): Future[UserForSocialAccountResult] = {
    profile.email match {
      case Some(email) =>
        loginInfoRepo.getAuthenticationProviders(email).flatMap { providers =>
          if (providers.contains(provider) || providers.isEmpty) {
            val member = new Member(
              profile.fullName.getOrElse("Guest"),
              profile.email.getOrElse("unknown"),
              MemberType.Beta.toString,
              false,
              true,
              profile.loginInfo,

              ZonedDateTime.now(),
              None
            )
            for {
              memberId <- memberService.save(member, secretStore)
              _        <- addAuthenticateMethod(memberId, profile.loginInfo, authInfo)
            } yield AccountBound(member.copy(id = Some(memberId)))
          } else {
            Future.successful(EmailIsBeingUsed(providers))
          }
        }
      case None =>
        Future.successful(NoEmailProvided)
    }
  }

  /**
    * Adds authentication method to user
    *
    * @param memberId    user id
    * @param loginInfo login info
    * @param authInfo  auth info
    * @tparam T tyupe of auth info
    * @return
    */
  def addAuthenticateMethod[T <: AuthInfo](memberId: Long, loginInfo: LoginInfo, authInfo: T): Future[Unit] = {
    for {
      _ <- loginInfoRepo.saveUserLoginInfo(memberId, loginInfo)
      _ <- authInfoRepository.save(loginInfo, authInfo)
    } yield ()
  }

  /**
    * Checks whether user have authentication method for given provider id
    *
    * @param memberId     user id
    * @param providerId authentication provider id
    * @return true if user has authentication method for given provider id, otherwise false
    */
  def userHasAuthenticationMethod(memberId: Long, providerId: String): Future[Boolean] = {
    loginInfoRepo.find(memberId, providerId).map(_.nonEmpty)
  }

  /**
    * Get list of providers of user authentication methods
    *
    * @param email user email
    * @return
    */
  def getAuthenticationProviders(email: String): Future[Seq[String]] = loginInfoRepo.getAuthenticationProviders(email)
}

sealed trait UserForSocialAccountResult
case object NoEmailProvided                         extends UserForSocialAccountResult
case class EmailIsBeingUsed(providers: Seq[String]) extends UserForSocialAccountResult
case class AccountBound(member: Member)             extends UserForSocialAccountResult

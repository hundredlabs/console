package web.providers

import web.models.CredentialInfo
import javax.inject.{Inject, Named}
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.exceptions.ConfigurationException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider._
import com.mohiva.play.silhouette.impl.providers.PasswordProvider
import com.mohiva.play.silhouette.impl.providers.PasswordProvider._

import scala.concurrent.{ExecutionContext, Future}
class EmailCredentialsProvider @Inject() (protected val authInfoRepository: AuthInfoRepository,
                                            protected val passwordHasherRegistry: PasswordHasherRegistry)
                                         (implicit val executionContext: ExecutionContext)
  extends PasswordProvider {

  /**
    * Gets the provider ID.
    *
    * @return The provider ID.
    */
  override def id = ID

  /**
    * Authenticates a user with its credentials.
    *
    * @param credentials The credentials to authenticate with.
    * @return The login info if the authentication was successful, otherwise a failure.
    */
  def authenticate(credentials: Credentials): Future[LoginInfo] = {
    loginInfo(credentials).flatMap { loginInfo =>
      authenticate(loginInfo, credentials.password).map {
        case Authenticated            => loginInfo
        case InvalidPassword(error)   => throw new InvalidPasswordException(error)
        case UnsupportedHasher(error) => throw new ConfigurationException(error)
        case NotFound(error)          => throw new IdentityNotFoundException(error)
      }
    }
  }

  override def authenticate(loginInfo: LoginInfo, password: String): Future[State] = {
    authInfoRepository.find[CredentialInfo](loginInfo).flatMap {
      case Some(credInfo) => passwordHasherRegistry.find(credInfo.getPasswordInfo) match {
        case Some(hasher) if hasher.matches(credInfo.getPasswordInfo, password) =>
          if (passwordHasherRegistry.isDeprecated(hasher) || hasher.isDeprecated(credInfo.getPasswordInfo).contains(true)) {
            authInfoRepository.update(loginInfo, passwordHasherRegistry.current.hash(password)).map { _ =>
              Authenticated
            }
          } else {
            Future.successful(Authenticated)
          }
        case Some(hasher) => Future.successful(InvalidPassword(PasswordDoesNotMatch.format(id)))
        case None => Future.successful(UnsupportedHasher(HasherIsNotRegistered.format(
          id, credInfo.hasher, passwordHasherRegistry.all.map(_.id).mkString(", ")
        )))
      }
      case None => Future.successful(NotFound(PasswordInfoNotFound.format(id, loginInfo)))
    }
  }


  /**
    * Gets the login info for the given credentials.
    *
    * Override this method to manipulate the creation of the login info from the credentials.
    *
    * By default the credentials provider creates the login info with the identifier entered
    * in the form. For some cases this may not be enough. It could also be possible that a login
    * form allows a user to log in with either a username or an email address. In this case
    * this method should be overridden to provide a unique binding, like the user ID, for the
    * entered form values.
    *
    * @param credentials The credentials to authenticate with.
    * @return The login info created from the credentials.
    */
  def loginInfo(credentials: Credentials): Future[LoginInfo] = Future.successful(LoginInfo(id, credentials.identifier))
}


package web.repo

import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import web.models.Member

import scala.concurrent.Future

trait LoginInfoRepo {
  /**
    * Get list of user authentication methods providers
    *
    * @param email user email
    * @return
    */
  def getAuthenticationProviders(email: String): Future[Seq[String]]

  /**
    * Finds a user and login info pair by userID and login info providerID
    *
    * @param memberId     user id
    * @param providerId provider id
    * @return Some(User, LoginInfo) if there is a user by userId which has login method for provider by provider ID, otherwise None
    */
  def find(memberId: Long, providerId: String): Future[Option[Member]]

  /**
    * Saves a login info for user
    *
    * @param memberId The user id.
    * @param loginInfo login info
    * @return unit
    */
  def saveUserLoginInfo(memberId: Long, loginInfo: LoginInfo): Future[Unit]
}

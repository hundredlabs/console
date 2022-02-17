package web.services

import com.mohiva.play.silhouette.api.util.{PasswordHasher, PasswordInfo}
import APIHmacHasher._

class APIHmacHasher extends PasswordHasher{

  override def id: String = ID

  override def hash(message: String): PasswordInfo = ???

  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String): Boolean = ???

  override def isDeprecated(passwordInfo: PasswordInfo): Option[Boolean] = ???
}

object APIHmacHasher {
  val ID = "api-hmac"
}

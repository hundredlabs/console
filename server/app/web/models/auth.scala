package web.models

import java.time.ZonedDateTime

case class AuthToken(id: String, memberId: Long, expiry: ZonedDateTime)
case class DBLoginInfo(id: Option[Long], providerID: String, providerKey: String)
case class DBMemberLoginInfo(memberId: Long, loginInfoId: Long)
case class DBOAuth1Info(id: Option[Long], token: String, secret: String, loginInfoId: Long)
case class DBOAuth2Info(id: Option[Long],
                        accessToken: String,
                        tokenType: Option[String],
                        expiresIn: Option[Int],
                        refreshToken: Option[String],
                        loginInfoId: Long)


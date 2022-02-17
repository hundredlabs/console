package web.models

import java.time._

import scalikejdbc._
import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.{CommonSocialProfile, CredentialsProvider}
import web.utils.DateUtil
object MemberType extends Enumeration {
  type MemberType = Value
  val Alpha, Beta, Developer, Customer, Admin = Value
}

case class BetaSignedUpUser(reqId: Long, email: String, activationCode: String, activationStatus: Boolean, dtRequested: String)
case class AlphaSignedUpUser(reqId: Long, email: String, dtRequested: String)

object BetaSignedUpUser extends SQLSyntaxSupport[BetaSignedUpUser] {
  override val tableName = "access_requests"

  def apply(rs: WrappedResultSet): BetaSignedUpUser =
    new BetaSignedUpUser(
      rs.long("req_id"),
      rs.string("email"),
      rs.string("activation_code"),
      rs.boolean("activation_status"),
      DateUtil.timeElapsed(rs.zonedDateTime("dt_approved"), None)
    )
}

case class Member(name: String,
                  email: String,
                  memberType: String,
                  receiveUpdates: Boolean,
                  activated: Boolean,
                  loginInfo: LoginInfo,
                  dtJoined: ZonedDateTime,
                  id: Option[Long])
    extends Identity

object Member extends SQLSyntaxSupport[Member] {
  override val tableName = "members"

  def apply(newMember: BetaSignupRequest): Member =
    new Member(
      newMember.name,
      newMember.email,
      MemberType.Beta.toString,
      newMember.receiveUpdates,
      false,
      LoginInfo(CredentialsProvider.ID, newMember.email),
      ZonedDateTime.now(),
      None
    )

  def apply(profile: CommonSocialProfile): Member = new Member(
    name = profile.fullName.getOrElse("guest"),
    email = profile.email.getOrElse("unknown"),
    memberType = MemberType.Beta.toString,
    receiveUpdates = false,
    activated = false,
    loginInfo = profile.loginInfo,
    ZonedDateTime.now(),
    None
  )

  def apply(rs: WrappedResultSet) =
    new Member(
      rs.string("name"),
      rs.string("email"),
      rs.string("member_type"),
      rs.boolean("receive_updates"),
      rs.boolean("activated"),
      LoginInfo(CredentialsProvider.ID, rs.string("email")),
      rs.zonedDateTime("dt_joined"),
      rs.longOpt("id")
    )
}

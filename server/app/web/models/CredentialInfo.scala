package web.models

import com.mohiva.play.silhouette.api.AuthInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import scalikejdbc._

case class CredentialInfo(email: String, hasher: String, password: String, salt : Option[String], id: Option[Long] = None) extends AuthInfo {

  def getPasswordInfo : PasswordInfo = PasswordInfo(hasher, password, salt)
}
object CredentialInfo extends SQLSyntaxSupport[CredentialInfo] {
  override val tableName = "credentials"

  def apply(rs: WrappedResultSet) =
    new CredentialInfo(
      rs.string("email"),
      rs.string("hasher"),
      rs.string("password"),
      rs.stringOpt("salt"),
      rs.longOpt("id")
    )
}
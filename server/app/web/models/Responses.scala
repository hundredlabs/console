package web.models

import com.mohiva.play.silhouette.api.Identity
import web.models.rbac.{MemberProfile, Theme}
import play.api.libs.json._

case class SignupResponse(success: Boolean, id: Option[Long], message: String = "Successfully created a user")
case class UserView(name: String)
case class SignInResponse(success: Boolean,
                          email: String,
                          userName: Option[String] = None,
                          statusCode: Int = 200,
                          message: String = "Successfully authenticated",
                          errors: Map[String, String] = Map.empty)

case class SignOutResponse(success: Boolean = true, message: String = "Signed Out")
case class UserNotAuthenticated(requestedResource: String, message: String = "User not authenticated")

case class ActionForbidden(actionTarget: String, message: String = "Access to this resource is restricted")
case class MemberInfoResponse(exist: Boolean,
                              hasProfile: Boolean,
                              id: Option[Long] = None,
                              name: Option[String] = None,
                              email: Option[String] = None,
                              profile: Option[MemberProfile] = None,
                              message: String = "User not found") {

  def from(member: Option[Member]): MemberInfoResponse = member match {
    case None => this
    case Some(value) =>
      this.copy(exist = true,
                id = Some(value.id.get),
                name = Some(value.name),
                email = Some(value.email),
                message = "User exists")
  }
}
case class OrgResponse(id: Long, name: String)
case class MemberWithApi(memberId: Long, name: String, pubKey: String) extends Identity
case class OrgWithKeys(orgId: Long, orgName: String, key: String, secret: String, willExpireIn: String) extends Identity
case class WorkspaceId(id: Long, name: String, keyName: String, key: String, secretKey: String) extends Identity
case class WorkspaceAPIKey(name: String, apiKey: String, apiSecretKey: String)
case class WorkspaceViewResponse(id: Long, name: String, created: String)
case class WorkspaceResponse(id: Long, name: String)
case class OrgDetail(name: String, slugId: String, thumbnailImg: Option[String])



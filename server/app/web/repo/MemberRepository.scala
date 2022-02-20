package web.repo

import web.models.{Member, MemberValue, OrgDetail, OrgUsagePlan, OrgWithKeys, WorkspaceViewResponse}
import com.mohiva.play.silhouette.api.LoginInfo
import web.models.rbac.{MemberProfile, MemberRole}
import web.services.SecretStore

import scala.concurrent.{ExecutionContext, Future}

trait MemberRepository {

  val ec: ExecutionContext

  def listOrgsWithKeys(memberId: Long): Future[Seq[OrgWithKeys]]

  def retriveOrg(key: String): Future[Option[OrgWithKeys]]

  def getMemberInfo(id: Long): Option[MemberValue]

  def getOrgDetail(orgId: Long): Future[Option[OrgDetail]]

  def updateOrg(id: Long, detail: OrgDetail): Future[Boolean]

  def createOrg(name: String, ownerId: Long, orgSlug: String, thumbnailImg: Option[String]): Future[Either[Throwable, Long]]

  def getMemberProfile(id: Long): Future[Option[MemberProfile]]

  def findByToken(token: String): Future[Option[Member]]

  def getMemberRoles(memberId: Long): Future[Seq[MemberRole]]

  /**
    * Finds a user by its login info.
    *
    * @param loginInfo The login info of the user to find.
    * @return The found user or None if no user for the given login info could be found.
    */
  def find(loginInfo: LoginInfo): Future[Option[Member]]

  def findByEmail(email: String): Future[Option[Member]]

  /**
    * Finds a user by its user ID.
    *
    * @param memberId The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  def find(memberId: Long): Future[Option[Member]]

  /**
    * Saves a user.
    *
    * @param user The user to save.
    * @return The saved user.
    */
  def save(user: Member, secretStore: SecretStore): Future[Long]

  def updateName(name: String, memId: Long): Future[Boolean]

  def listWorkspaces(orgId: Long): Future[Seq[WorkspaceViewResponse]]

}

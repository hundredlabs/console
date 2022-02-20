package web.services

import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import web.models.{CredentialInfo, Member, MemberValue, OrgDetail, OrgWithKeys, WorkspaceAPIKey, WorkspaceViewResponse}
import com.mohiva.play.silhouette.api.services.IdentityService
import web.models.rbac.{MemberProfile, MemberRole}
import play.api.cache.SyncCacheApi
import web.models.requests.{CreateOrgWorkspace, ProvisionWorkspace, WorkspaceCreated}

import scala.concurrent.Future

trait MemberService extends IdentityService[Member] {

  /**
    * Fetch the member based on the member Id
    *
    * @param memberId
    * @return
    */
  def find(memberId: Long): Future[Option[Member]]

  def findByEmail(email: String): Future[Option[Member]]

  /**
    * Save the member and generate the id
    *
    * @param member
    * @return
    */
  def save(member: Member, secretStore: SecretStore): Future[Long]

  def updateMemberName(name: String, memId: Long): Future[Boolean]

  def updateCredentials(loginInfo: LoginInfo, credentialInfo: CredentialInfo): Future[CredentialInfo]

  def provisionWorkspace(name: String, request: ProvisionWorkspace, secretStore: SecretStore, memberId: Long): Future[Either[Throwable, WorkspaceCreated]]

  def createOrg(name: String, memberId: Long, slugId: String, thumbnail: Option[String]): Future[Either[Throwable, Long]]

  def createOrgWorkspace(ownerId: Long, orgId: Long, secretStore: SecretStore, request: CreateOrgWorkspace): Future[Either[Throwable, Long]]

  /**
    * Adds authentication method to user
    *
    * @param memberId  user id
    * @param loginInfo login info
    * @param authInfo  auth info
    * @tparam T tyupe of auth info
    * @return
    */
  def addAuthenticateMethod[T <: AuthInfo](memberId: Long, loginInfo: LoginInfo, authInfo: T): Future[Unit]

  def getMemberProfile(id: Long): Future[Option[MemberProfile]]

  def getMemberRoles(memberId: Long): Future[Seq[MemberRole]]

  def getMemberValue(id: Long, userCache: SyncCacheApi): MemberValue

  def getOrgDetail(orgId: Long): Future[Option[OrgDetail]]

  def updateOrg(orgId: Long, orgDetail: OrgDetail): Future[Option[OrgDetail]]

  def listWorkspaces(orgId: Long): Future[Seq[WorkspaceViewResponse]]

  def listWorkspaceAPIKeys(wId: Long, secretStore: SecretStore, workspaceKeyCache: SyncCacheApi): Future[Seq[WorkspaceAPIKey]]

}

trait OrgService extends IdentityService[OrgWithKeys] {

  def listOrgsWithKeys(memberId: Long): Future[Seq[OrgWithKeys]]

  def retriveOrg(apiKey: String): Future[Option[OrgWithKeys]]

}

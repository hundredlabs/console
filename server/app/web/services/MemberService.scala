package web.services

import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import web.models.{AccessRequest, AlpahRequest, AlphaRequests, BetaRequestListResponse, CredentialInfo, JobWithUsage, Member, MemberValue, OrgDetail, OrgUsagePlan, OrgWithKeys, WorkspaceAPIKey, WorkspaceViewResponse}
import com.mohiva.play.silhouette.api.services.IdentityService
import web.models.rbac.{MemberProfile, MemberRole, AccessPolicy}
import play.api.cache.SyncCacheApi
import web.models.requests.{CreateOrgWorkspace, ProvisionWorkspace, WorkspaceCreated}

import scala.concurrent.Future

trait MemberService extends IdentityService[Member] {

  /**
  * Fetch the member based on the member Id
    * @param memberId
    * @return
    */
  def find(memberId: Long): Future[Option[Member]]

  def findByEmail(email: String): Future[Option[Member]]

  /**
  * Save the member and generate the id
    * @param member
    * @return
    */
  def save(member: Member, secretStore: SecretStore): Future[Long]

  def updateMemberName(name: String, memId: Long): Future[Boolean]

  def updateCredentials(loginInfo: LoginInfo, credentialInfo: CredentialInfo): Future[CredentialInfo]

  def saveAccessRequest(request: AccessRequest): Future[Long]

  def getEmailByCode(code: String): Future[Option[String]]

  def saveAlphaRequest(request: AlpahRequest): Future[Long]

  def provisionWorkspace(name: String, request: ProvisionWorkspace, secretStore: SecretStore, memberId: Long): Future[Either[Throwable, WorkspaceCreated]]

  def createOrg(name: String, plan: OrgUsagePlan, memberId: Long, slugId: String, thumbnail: Option[String]): Future[Either[Throwable, Long]]

  def createOrgWorkspace(ownerId: Long, orgId: Long, secretStore: SecretStore, request: CreateOrgWorkspace): Future[Either[Throwable, Long]]

  def notifyMember(memberId: Long): Future[Boolean]

  /**
    * Adds authentication method to user
    *
    * @param memberId    user id
    * @param loginInfo login info
    * @param authInfo  auth info
    * @tparam T tyupe of auth info
    * @return
    */
  def addAuthenticateMethod[T <: AuthInfo](memberId: Long, loginInfo: LoginInfo, authInfo: T): Future[Unit]


  /**
  * Fetch the list of beta requests
    * @param pageNum the current page number
    * @param pageSize total count of requests to be fetched
    * @return
    */
  def listApprovedRequests(pageNum: Int, pageSize: Int) : Future[BetaRequestListResponse]

  def listAlphaUsers(pageNum: Int, pageSize: Int) : Future[AlphaRequests]

  def approveRequest(email: String): Future[Option[String]]

  def updateApproveStatus(email: String): Future[Boolean]

  def setActivated(member: Member): Future[Boolean]

  def getMemberProfile(id: Long): Future[Option[MemberProfile]]

  def getDesktopToken(memberId: Long): Future[Option[String]]

  def getMemberRoles(memberId: Long): Future[Seq[MemberRole]]

  def getMemberValue(id: Long, userCache: SyncCacheApi): MemberValue

  def getOrgDetail(orgId: Long): Future[Option[OrgDetail]]

  def getJobUsageFor(orgId: Long, date: String): Future[Seq[JobWithUsage]]

  def updateOrg(orgId: Long, orgDetail: OrgDetail): Future[Option[OrgDetail]]

  def listWorkspaces(orgId: Long): Future[Seq[WorkspaceViewResponse]]

  def listWorkspaceAPIKeys(wId: Long, secretStore: SecretStore, workspaceKeyCache: SyncCacheApi): Future[Seq[WorkspaceAPIKey]]

}

trait OrgService extends IdentityService[OrgWithKeys] {

  def listOrgsWithKeys(memberId: Long): Future[Seq[OrgWithKeys]]

  def retriveOrg(apiKey: String): Future[Option[OrgWithKeys]]

}

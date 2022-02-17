package web.repo

import java.time.{LocalDate, ZonedDateTime}

import web.models.{AccessRequest, AlpahRequest, AlphaRequests, BetaRequestListResponse, JobWithUsage, Member, MemberValue, OrgDetail, OrgUsagePlan, OrgWithKeys, WorkspaceViewResponse}
import com.mohiva.play.silhouette.api.LoginInfo
import web.models.rbac.{MemberProfile, MemberRole}
import web.services.SecretStore

import scala.concurrent.{ExecutionContext, Future}

trait MemberRepository {

  val ec: ExecutionContext
  def updateApproveStatus(email: String): Future[Boolean]

  def approveRequest(email: String): Future[Option[String]]

  def listAlphaRequests(pageNum: Int, pageSize: Int): Future[AlphaRequests]

  def getEmailByCode(code: String): Future[Option[String]]

  def listOrgsWithKeys(memberId: Long): Future[Seq[OrgWithKeys]]

  def retriveOrg(key: String): Future[Option[OrgWithKeys]]

  def getMemberInfo(id: Long): Option[MemberValue]

  def newAccessRequest(request: AccessRequest): Future[Long]

  def getJobUsageByDate(orgId: Long, date: LocalDate, endDate: LocalDate): Future[Seq[JobWithUsage]]

  def getOrgDetail(orgId: Long): Future[Option[OrgDetail]]

  def updateOrg(id: Long, detail: OrgDetail): Future[Boolean]

  def newAlphaRequest(request: AlpahRequest): Future[Long]

  def notifyMember(memberId: Long): Future[Boolean]

  def createOrg(name: String, ownerId: Long, plan: OrgUsagePlan, orgSlug: String, thumbnailImg: Option[String]): Future[Either[Throwable, Long]]

  def getMemberProfile(id: Long): Future[Option[MemberProfile]]

  def findByToken(token: String): Future[Option[Member]]

  def getDesktopToken(memberId: Long): Future[Option[String]]

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

  def setActivated(member: Member): Future[Boolean]

  def listApprovedRequests(pageNum: Int, pageSize: Int): Future[BetaRequestListResponse]

  def listWorkspaces(orgId: Long): Future[Seq[WorkspaceViewResponse]]

}

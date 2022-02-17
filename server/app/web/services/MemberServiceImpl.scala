package web.services

import web.models.{AccessRequest, AlpahRequest, AlphaRequests, BetaRequestListResponse, CredentialInfo, JobWithUsage, Member, MemberValue, OrgDetail, OrgUsagePlan, OrgWithKeys, WorkspaceAPIKey, WorkspaceId, WorkspaceViewResponse}
import web.repo.{LoginInfoRepo, MemberRepository, WorkspaceRepo}
import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import javax.inject.Inject
import web.models.rbac.{MemberProfile, MemberRole}
import play.api.cache.SyncCacheApi
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, YearMonth}

import web.models.requests.{CreateOrgWorkspace, ProvisionWorkspace, WorkspaceCreated}

import scala.concurrent.{ExecutionContext, Future}

class MemberServiceImpl @Inject()(memberRepository: MemberRepository,
                                  workspaceRepo: WorkspaceRepo,
                                  loginInfoRepo: LoginInfoRepo,
                                  authInfoRepository: AuthInfoRepository)
    extends MemberService {
  implicit val ec: ExecutionContext = memberRepository.ec

  /**
    * Fetch the member based on the member Id
    *
    * @param memberId
    * @return
    */
  override def find(memberId: Long): Future[Option[Member]] = memberRepository.find(memberId)

  override def findByEmail(email: String): Future[Option[Member]] = memberRepository.findByEmail(email)

  /**
    * Save the member and generate the id
    *
    * @param member
    * @return
    */
  override def save(member: Member, secretStore: SecretStore): Future[Long] = {
    Future.sequence(Seq(retrieve(member.loginInfo), memberRepository.findByEmail(member.email))).flatMap { members =>
      members.flatten.headOption match {
        case None =>
          memberRepository.save(member, secretStore)
        case Some(value) =>
          memberRepository
            .updateName(value.name, value.id.getOrElse(0))
            .map(_ => value.id.getOrElse(0L))


      }
    }
  }

  override def updateMemberName(name: String, memId: Long): Future[Boolean] = memberRepository.updateName(name, memId)

  override def updateCredentials(loginInfo: LoginInfo, credentialInfo: CredentialInfo): Future[CredentialInfo] =
    authInfoRepository.update(loginInfo, credentialInfo)

  override def retrieve(loginInfo: LoginInfo): Future[Option[Member]] = memberRepository.find(loginInfo)

  override def getEmailByCode(code: String): Future[Option[String]] = memberRepository.getEmailByCode(code)

  override def saveAccessRequest(request: AccessRequest): Future[Long] = memberRepository.newAccessRequest(request)

  override def saveAlphaRequest(request: AlpahRequest): Future[Long] = memberRepository.newAlphaRequest(request)

  override def provisionWorkspace(name: String, request: ProvisionWorkspace,
                                  secretStore: SecretStore,
                                  memberId: Long): Future[Either[Throwable, WorkspaceCreated]] = {
    val (orgName, orgSlug) = if (request.isPersonalAccount || request.orgSlugId.isEmpty) {
      val personalOrg = name.split(" ").headOption match {
        case None => "Default"
        case Some(value) => s"""${value}"""
      }
      val slugId = new DefaultKeyPairGenerator().getRandomString(10)
      (personalOrg, slugId)
    } else {
      (request.orgName.get, request.orgSlugId.get)
    }

    for {
      org <- createOrg(orgName, OrgUsagePlan.freePlan(), memberId, orgSlug, None)
      ws <- org.map(orgId => createOrgWorkspace(memberId, orgId, secretStore, CreateOrgWorkspace(request.name, None, None))) match {
        case Left(e) => Future.successful(Left(e))
        case Right(v) =>
          v.map {
            case Left(ex) => Left(ex)
            case Right(value) => Right(WorkspaceCreated(value , orgSlug))
          }
      }
    } yield ws
  }

  override def createOrg(name: String, plan: OrgUsagePlan, memberId: Long, slugId: String, thumbnail: Option[String]): Future[Either[Throwable, Long]] =
    memberRepository.createOrg(name, memberId, plan, slugId, thumbnail)

  override def createOrgWorkspace(ownerId: Long,
                                  orgId: Long,
                                  secretStore: SecretStore,
                                  request: CreateOrgWorkspace): Future[Either[Throwable, Long]] =
    workspaceRepo.createWorkspace(ownerId, orgId, secretStore, request)

  override def notifyMember(memberId: Long): Future[Boolean] = memberRepository.notifyMember(memberId)

  override def addAuthenticateMethod[T <: AuthInfo](memberId: Long, loginInfo: LoginInfo, authInfo: T): Future[Unit] =
    for {
      _ <- loginInfoRepo.saveUserLoginInfo(memberId, loginInfo)
      _ <- authInfoRepository.add(loginInfo, authInfo)
    } yield ()

  override def listApprovedRequests(pageNum: Int, pageSize: Int): Future[BetaRequestListResponse] =
    memberRepository.listApprovedRequests(pageNum, pageSize)

  override def listAlphaUsers(pageNum: Int, pageSize: Int): Future[AlphaRequests] = memberRepository.listAlphaRequests(pageNum, pageSize)

  override def approveRequest(email: String): Future[Option[String]] = memberRepository.approveRequest(email)

  override def updateApproveStatus(email: String): Future[Boolean] = memberRepository.updateApproveStatus(email)

  override def setActivated(member: Member): Future[Boolean] = memberRepository.setActivated(member)

  override def getMemberProfile(id: Long): Future[Option[MemberProfile]] = memberRepository.getMemberProfile(id)

  override def getDesktopToken(memberId: Long): Future[Option[String]] = memberRepository.getDesktopToken(memberId)

  override def getMemberRoles(memberId: Long): Future[Seq[MemberRole]] = memberRepository.getMemberRoles(memberId)

  override def getMemberValue(id: Long, userCache: SyncCacheApi): MemberValue = userCache.getOrElseUpdate(id.toString) {
    memberRepository.getMemberInfo(id) match {
      case None        => MemberValue(id, "", Seq())
      case Some(value) => value
    }
  }

  override def getOrgDetail(orgId: Long): Future[Option[OrgDetail]] = memberRepository.getOrgDetail(orgId)

  override def getJobUsageFor(orgId: Long, date: String): Future[Seq[JobWithUsage]] = {
    val startDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd-MMM-yyyy"))
    val endDate = YearMonth
      .from(startDate)
      .atEndOfMonth()

    memberRepository.getJobUsageByDate(orgId, startDate, endDate)
  }

  override def updateOrg(orgId: Long, orgDetail: OrgDetail): Future[Option[OrgDetail]] =
    for {
      _      <- memberRepository.updateOrg(orgId, orgDetail)
      detail <- memberRepository.getOrgDetail(orgId)
    } yield detail

  override def listWorkspaces(orgId: Long): Future[Seq[WorkspaceViewResponse]] = memberRepository.listWorkspaces(orgId)

  override def listWorkspaceAPIKeys(workspaceId: Long,
                                    secretStore: SecretStore,
                                    workspaceKeyCache: SyncCacheApi): Future[Seq[WorkspaceAPIKey]] = {
    workspaceRepo.listWorkspaceAPIKeys(workspaceId, secretStore, secretStore.getWorkspaceKeyPair(workspaceId, workspaceKeyCache))
  }
}

class OrgServiceImpl @Inject()(memberRepository: MemberRepository) extends OrgService {

  override def listOrgsWithKeys(memberId: Long): Future[Seq[OrgWithKeys]] = memberRepository.listOrgsWithKeys(memberId)

  override def retriveOrg(apiKey: String): Future[Option[OrgWithKeys]] = memberRepository.retriveOrg(apiKey)

  override def retrieve(loginInfo: LoginInfo): Future[Option[OrgWithKeys]] = memberRepository.retriveOrg(loginInfo.providerKey)
}

package web.services

import web.models.{CredentialInfo, Member, MemberValue, OrgDetail, OrgUsagePlan, OrgWithKeys, WorkspaceAPIKey, WorkspaceViewResponse}
import web.repo.{LoginInfoRepo, MemberRepository, WorkspaceRepo}
import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import javax.inject.Inject
import web.models.rbac.{MemberProfile, MemberRole}
import play.api.cache.SyncCacheApi

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
      org <- createOrg(orgName, memberId, orgSlug, None)
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

  override def createOrg(name: String, memberId: Long, slugId: String, thumbnail: Option[String]): Future[Either[Throwable, Long]] =
    memberRepository.createOrg(name, memberId, slugId, thumbnail)

  override def createOrgWorkspace(ownerId: Long,
                                  orgId: Long,
                                  secretStore: SecretStore,
                                  request: CreateOrgWorkspace): Future[Either[Throwable, Long]] =
    workspaceRepo.createWorkspace(ownerId, orgId, secretStore, request)

  override def addAuthenticateMethod[T <: AuthInfo](memberId: Long, loginInfo: LoginInfo, authInfo: T): Future[Unit] =
    for {
      _ <- loginInfoRepo.saveUserLoginInfo(memberId, loginInfo)
      _ <- authInfoRepository.add(loginInfo, authInfo)
    } yield ()


  override def getMemberProfile(id: Long): Future[Option[MemberProfile]] = memberRepository.getMemberProfile(id)


  override def getMemberRoles(memberId: Long): Future[Seq[MemberRole]] = memberRepository.getMemberRoles(memberId)

  override def getMemberValue(id: Long, userCache: SyncCacheApi): MemberValue = userCache.getOrElseUpdate(id.toString) {
    memberRepository.getMemberInfo(id) match {
      case None        => MemberValue(id, "", Seq())
      case Some(value) => value
    }
  }

  override def getOrgDetail(orgId: Long): Future[Option[OrgDetail]] = memberRepository.getOrgDetail(orgId)

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

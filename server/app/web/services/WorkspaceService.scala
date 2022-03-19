package web.services

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.IdentityService
import javax.inject.Inject
import web.models.WorkspaceId
import web.repo.{LoginInfoRepo, MemberRepository, WorkspaceRepo}
import play.api.cache.SyncCacheApi
import play.cache.NamedCache
import web.models.requests.{ConnectionView, WorkspaceConnection, WorkspaceView}

import scala.concurrent.Future

trait WorkspaceService extends IdentityService[WorkspaceId] {

  def listWorkspaces(orgId: Long, orgSlugId: String): Future[Either[Throwable, Seq[WorkspaceView]]] = ???

  def retrieveWorkspace(apiKey: String, secretStore: SecretStore): Future[Option[WorkspaceId]]

  def addConnection(workspaceId: Long, connection: WorkspaceConnection): Future[Either[Throwable, Long]]

  def updateConnection(workspaceId: Long, connectionId: Long, connection: WorkspaceConnection): Future[Either[Throwable, Boolean]]

  def listConnections(workspaceId: Long): Future[Either[Throwable, Seq[ConnectionView]]]

  def deleteConnection(workspaceId: Long, connectionId: Long): Future[Either[Throwable, Boolean]]

}

class WorkspaceServiceImpl @Inject()(memberRepository: MemberRepository,
                                     workspaceRepo: WorkspaceRepo,
                                     loginInfoRepo: LoginInfoRepo,
                                     @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
                                     secretStore: SecretStore,
                                     authInfoRepository: AuthInfoRepository)
    extends WorkspaceService {

  override def retrieveWorkspace(apiKey: String, secretStore: SecretStore): Future[Option[WorkspaceId]] =
    workspaceRepo.retrieveWorkspace(apiKey, secretStore, workspaceKeyCache)

  override def retrieve(loginInfo: LoginInfo): Future[Option[WorkspaceId]] = retrieveWorkspace(loginInfo.providerKey, secretStore)

  override def addConnection(workspaceId: Long, connection: WorkspaceConnection): Future[Either[Throwable, Long]] =
    workspaceRepo.addConnection(workspaceId, connection)

  override def updateConnection(workspaceId: Long, connectionId: Long, connection: WorkspaceConnection): Future[Either[Throwable, Boolean]] =
    workspaceRepo.updateConnection(workspaceId, connectionId, connection)

  override def listConnections(workspaceId: Long): Future[Either[Throwable, Seq[ConnectionView]]] =
    workspaceRepo.listConnections(workspaceId)

  override def deleteConnection(workspaceId: Long, connectionId: Long): Future[Either[Throwable, Boolean]] =
    workspaceRepo.deleteConnection(workspaceId, connectionId)

}

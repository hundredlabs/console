package web.repo

import com.goterl.lazycode.lazysodium.utils.KeyPair
import web.models.{WorkspaceAPIKey, WorkspaceId}
import web.models.rbac.MemberProfile
import web.services.SecretStore
import play.api.cache.SyncCacheApi
import web.models.requests.{CreateOrgWorkspace, WorkspaceView}

import scala.concurrent.{ExecutionContext, Future}

trait WorkspaceRepo {
  val ec: ExecutionContext

  /**
    * Creates the workspace for the given org and makes the caller, the owner of the org
    * @param ownerId
    * @param orgId
    * @param secretStore
    * @param request
    * @return workspace ID
    */
  def createWorkspace(
                       ownerId: Long,
                       orgId: Long,
                       secretStore: SecretStore,
                       request: CreateOrgWorkspace): Future[Either[Throwable, Long]]

  /**
    * For the given org, fetch all the workspaces defined. This method can only be called by a user who has
    * Org management permission
    * @param orgId
    * @return list of workspace view
    */
  def listWorkspaces(orgId: Long): Future[Seq[WorkspaceView]]

  def listWorkspaceAPIKeys(workspaceId: Long, secretStore: SecretStore, workspaceKeys: Option[KeyPair]): Future[Seq[WorkspaceAPIKey]]

  /**
    * Retrieves the workspace info based on the api key
    * @param apiKey
    * @return
    */
  def retrieveWorkspace(apiKey: String, secretStore: SecretStore, workspaceKeysCache: SyncCacheApi): Future[Option[WorkspaceId]]

  /**
    * Once a user switches to a different org, or a different workspace in an org, this updates the current user profile
    * @param memberId
    * @param workspaceId
    * @param orgId
    * @return
    */
  def updateCurrentWorkspace(memberId: Long, workspaceId: Long, orgId: Long): Future[Option[MemberProfile]]


}

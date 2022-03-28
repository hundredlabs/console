package web.repo.pg

import com.goterl.lazycode.lazysodium.utils.KeyPair
import javax.inject.Inject
import web.models.{WorkspaceAPIKey, WorkspaceId}
import web.models.rbac.{AccessRoles, MemberProfile, SubjectType, Theme}
import web.models.requests.{ConnectionProvider, ConnectionView, CreateOrgWorkspace, WorkspaceConnection, WorkspaceView}
import web.repo.WorkspaceRepo
import web.services.{DefaultKeyPairGenerator, SecretStore}
import web.utils.DateUtil
import play.api.cache.SyncCacheApi
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try

class PgWorkspaceRepoImpl @Inject()(blockingEC: ExecutionContext) extends WorkspaceRepo {
  implicit val ec = blockingEC

  override def createWorkspace(ownerId: Long,
                               orgId: Long,
                               secretStore: SecretStore,
                               request: CreateOrgWorkspace): Future[Either[Throwable, Long]] =
    Future {
      blocking {
        var workspaceId = 0L
        var roleId      = 0L
        DB localTx { implicit session =>
          val response = sql"""SELECT name FROM workspaces WHERE org_id = ${orgId} AND name = ${request.name.trim}"""
            .map(_.string("name"))
            .single()
            .apply()

          if (response.isDefined) {
            throw new IllegalArgumentException("Workspace with this name already exists. Try using a different identifier.")
          }

          workspaceId = sql"""INSERT INTO workspaces(name, org_id, thumbnail_img, description, created_by, dt_created)
             VALUES(${request.name},${orgId}, ${request.thumbnail}, ${request.description},${ownerId}, ${DateUtil.now})"""
            .updateAndReturnGeneratedKey()
            .apply()

          //Generate the crypto keypair and save
          val wsKeyPair     = secretStore.generateKeyPair
          sql"""INSERT INTO workspace_crypto_keypairs(hex_pub_key, hex_private_key, workspace_id)
             VALUES(${wsKeyPair.getPublicKey.getAsHexString}, ${wsKeyPair.getSecretKey.getAsHexString}, ${workspaceId})"""
            .update()
            .apply()

         val apiKeyPair = new DefaultKeyPairGenerator().genKey(10, workspaceId)
          val encryptedApiSecretKey = secretStore.encrypt(apiKeyPair.secret, wsKeyPair)

          var created = false

          while(!created){
           created = Try {
              sql"""INSERT INTO workspace_api_keys(api_key, encrypted_api_secret_key, name, workspace_id)
               VALUES(${apiKeyPair.key}, $encryptedApiSecretKey, 'default', $workspaceId)"""
                .update().apply()
            }.isSuccess
          }



          //Create all the roles defined by system
          val adminPolicies = AccessRoles.ROLE_WORKSPACE_MANAGER.map(_.name).mkString(AccessRoles.policySeparator)
          roleId = sql"""SELECT id FROM roles WHERE manager_id = ${orgId} AND name = ${AccessRoles.WORKSPACE_MANAGER}"""
            .map(r => r.long("id"))
            .single()
            .apply() match {
            case None =>
              sql"""update member_profile SET current_workspace_id = ${workspaceId} WHERE member_id = ${ownerId}"""
                .update()
                .apply()
              sql"""INSERT INTO roles(name, access_policies, manager_id)
              VALUES(${AccessRoles.WORKSPACE_MANAGER}, $adminPolicies,
              ${orgId})"""
                .updateAndReturnGeneratedKey()
                .apply()
            case Some(v) => v
          }

          sql"""INSERT INTO member_roles(member_id, role_id, subject_id, subject_type)
             VALUES(${ownerId}, ${roleId}, ${workspaceId}, ${SubjectType.WORKSPACE.toString})"""
            .update()
            .apply()

        }
        Right(workspaceId)

      }
    }.recover {
      case e: IllegalArgumentException => Left(e)
      case ex: Exception =>
        ex.printStackTrace()
        Left(new RuntimeException("Internal processing error. Contact support@gigahex.com"))
    }

  override def listWorkspaces(orgId: Long): Future[Seq[WorkspaceView]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT id, name, description, dt_created, thumbnail_img FROM workspaces WHERE org_id = ${orgId}"""
          .map(
            r =>
              WorkspaceView(r.string("name"),
                            r.stringOpt("description"),
                            r.stringOpt("thumbnail_img"),
                            r.long("id"),
                            DateUtil.timeElapsed(r.dateTime("dt_created"), None)))
          .list()
          .apply()

      }
    }
  }

  override def listWorkspaceAPIKeys(workspaceId: Long, secretStore: SecretStore, workspaceKeys: Option[KeyPair]): Future[Seq[WorkspaceAPIKey]] = Future {
    blocking {
      workspaceKeys match {
        case None => Seq()
        case Some(kp) =>
          DB localTx { implicit session =>
            sql"""SELECT name, api_key, encrypted_api_secret_key FROM workspace_api_keys WHERE workspace_id = ${workspaceId}"""
              .map(
                r =>
                  WorkspaceAPIKey(r.string("name"),
                    r.string("api_key"),
                    r.string("encrypted_api_secret_key")
                    ))
              .list()
              .apply()
              .map{ wk =>

                val encryptedAPIKey = secretStore.encrypt(wk.apiKey, kp)

                val decryptedSecretKey = secretStore.decrypt(wk.apiSecretKey, kp)
                wk.copy(apiSecretKey = decryptedSecretKey)
              }

          }
      }

    }
  }

  override def retrieveWorkspace(apiKey: String, secretStore: SecretStore, workspaceKeysCache: SyncCacheApi): Future[Option[WorkspaceId]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT workspaces.name, workspaces.id as w_id, wapi.name as key_name, wapi.encrypted_api_secret_key FROM workspace_api_keys as wapi
             INNER JOIN workspaces ON wapi.workspace_id = workspaces.id WHERE wapi.api_key = $apiKey"""
          .map(r => WorkspaceId(r.long("w_id"),
            r.string("name"),
            r.string("key_name"),
            apiKey,
            r.string("encrypted_api_secret_key")))
          .single().apply()
          .flatMap {wk =>
            secretStore.getWorkspaceKeyPair(wk.id, workspaceKeysCache).map { kp =>
              val decryptedSecretKey = secretStore.decrypt(wk.secretKey, kp)
              wk.copy(secretKey = decryptedSecretKey)
            }
          }
      }
    }
  }.recover{
    case e : Exception =>
    e.printStackTrace()
    None
  }

 override  def updateCurrentWorkspace(memberId: Long, workspaceId: Long, orgId: Long): Future[Option[MemberProfile]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""update member_profile set current_workspace = ${workspaceId}  WHERE org_id = ${orgId} AND member_id = ${memberId}"""
          .update().apply() > 0

        sql"""SELECT current_org_id, current_workspace_id, web_theme, workspaces.name as ws_name,
               orgs.name as org_name, orgs.slug_id as org_slug_id, desktop_theme FROM member_profile
             INNER JOIN orgs ON member_profile.current_org_id = orgs.id
             INNER JOIN workspaces ON member_profile.current_workspace_id = workspaces.id
             WHERE member_id = ${memberId}"""
          .map(r =>
            MemberProfile(
              r.long("current_org_id"),
              r.string("org_name"),
              r.string("org_slug_id"),
              r.long("current_workspace_id"),
              r.string("ws_name"),
              Theme.withName(r.string("web_theme")),
              Theme.withName(r.string("desktop_theme"))
            ))
          .single()
          .apply()
      }
    }
  }

  override def addConnection(workspaceId: Long, connection: WorkspaceConnection): Future[Either[Throwable, Long]] = Future {
    blocking {
      Try {
        DB localTx { implicit session =>
          sql"""INSERT INTO connections(name, connection_provider, properties, connection_schema_ver, workspace_id, dt_created)
             VALUES(${connection.name}, ${connection.provider}, ${connection.encProperties}, ${connection.schemaVersion},
             ${workspaceId}, ${DateUtil.now})"""
            .updateAndReturnGeneratedKey()
            .apply()
        }
      }.toEither
    }
  }

  override def listConnectionProviders(): Future[Either[Throwable, List[ConnectionProvider]]] = Future {
    blocking {
      Try {
        DB localTx { implicit session =>
          sql"""SELECT name, description, category FROM connection_providers"""
            .map{ row =>
              ConnectionProvider(name = row.string("name"),
                description = row.string("description"),
                category = row.string("category"))
            }
            .list()
            .apply()
        }
      }.toEither
    }
  }



  override def updateConnection(workspaceId: Long, connectionId: Long, connection: WorkspaceConnection): Future[Either[Throwable, Boolean]] = Future {
    blocking {
      Try {
        DB localTx { implicit session =>
          sql"""UPDATE connections SET name = ${connection.name}, properties = ${connection.encProperties},
                connection_schema_ver = ${connection.schemaVersion} WHERE workspace_id = ${workspaceId} AND id = ${connectionId}
             """
            .update()
            .apply() > 0
        }
      }.toEither
    }
  }

  override def listConnections(workspaceId: Long): Future[Either[Throwable, Seq[ConnectionView]]] = Future {
    blocking {
      Try {
        DB localTx { implicit session =>
          sql"""SELECT id, c.name, connection_provider, connection_schema_ver, cp.category, dt_created FROM connections as c
                INNER JOIN connection_providers as cp ON c.connection_provider = cp.name
                WHERE
                workspace_id = ${workspaceId}
             """
            .map{ row =>
              ConnectionView(id = row.long("id"),
                name = row.string("name"),
                schemaVersion = row.string("connection_schema_ver"),
                provider = row.string("connection_provider"),
                providerCategory = row.string("category"),
                dateCreated = row.dateTime("dt_created").toEpochSecond)
            }
            .list()
            .apply()
        }
      }.toEither
    }
  }

  override def deleteConnection(workspaceId: Long, connectionId: Long): Future[Either[Throwable, Boolean]] = Future {
    blocking {
      Try {
        DB localTx { implicit session =>
          sql"""DELETE FROM connections WHERE workspace_id = ${workspaceId} AND id = ${connectionId}
             """
            .update()
            .apply() > 0
        }
      }.toEither
    }
  }

  override def getConnection(workspaceId: Long, connectionId: Long): Future[Either[Throwable, Option[WorkspaceConnection]]] = Future {
    blocking {
      Try {
        DB localTx { implicit session =>
          sql"""SELECT name, connection_provider, properties, connection_schema_ver
                 FROM connections WHERE workspace_id = ${workspaceId} AND id = ${connectionId}
             """
            .map{ row =>
              WorkspaceConnection(name = row.string("name"),
                encProperties = row.string("properties"),
                provider = row.string("connection_provider"),
                schemaVersion = row.int("connection_schema_ver")
              )
            }.single().apply()
        }
      }.toEither
    }
  }
}

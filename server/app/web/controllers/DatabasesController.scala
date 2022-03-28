package web.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.gigahex.postgres.{MySQLDatabaseService, PostgresDBService}
import com.gigahex.services.{MySQLConnection, PgConnection, RequestQueryExecution, ServiceConnection}
import com.mohiva.play.silhouette.api.{HandlerResult, Silhouette}
import controllers.AssetsFinder
import javax.inject.Inject
import web.models.{ErrorResponse, IllegalParam, InternalServerErrorResponse}
import web.models.formats.{AuthResponseFormats, ConnectionFormats, DBFormats}
import web.services.{ClusterService, DatabaseServiceManager, FileServiceManager, MemberService, SecretStore, WorkspaceService}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{ControllerComponents, InjectedController, Results}
import play.cache.NamedCache
import utils.auth.{DefaultEnv, RandomGenerator}
import web.controllers.handlers.SecuredWebRequestHandler
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Failure
import scala.concurrent.ExecutionContext

class DatabasesController @Inject()(
                                     components: ControllerComponents,
                                     silhouette: Silhouette[DefaultEnv],
                                     memberService: MemberService,
                                     workspaceService: WorkspaceService,
                                     @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
                                     secretStore: SecretStore
                                   )(
                                     implicit
                                     ex: ExecutionContext,
                                     assets: AssetsFinder,
                                     system: ActorSystem,
                                     mat: Materializer
                                   ) extends InjectedController
  with I18nSupport
  with AuthResponseFormats
  with ErrorResponse
  with SecuredWebRequestHandler
  with ConnectionFormats
with DBFormats{

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )
  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  private def usingConnection(workspaceId: Long, connectionId: Long, path: String)(connHandler: DatabaseServiceManager[_] => Result): Future[Result] = {
    workspaceService
      .getConnection(workspaceId, connectionId)
      .map {
        case Left(e) => InternalServerError(Json.toJson(InternalServerErrorResponse(path, e.getMessage)))
        case Right(Some(v)) => secretStore
          .decryptText(v.encProperties, workspaceId, workspaceKeyCache)
          .map(p => Json.parse(p).as[ServiceConnection])
          .map {
            case x: PgConnection => new DatabaseServiceManager[PgConnection](x, PostgresDBService)
            case x: MySQLConnection => new DatabaseServiceManager[MySQLConnection](x, MySQLDatabaseService)
          }.map(dbm => connHandler(dbm))
        .getOrElse(BadRequest(Json.toJson(IllegalParam(path, 0, "Unable to decrypt the connection details due to missing encryption keys."))))
        case _ => BadRequest(Json.toJson(IllegalParam(path, 0, "Unable to get the connection details")))

      }
  }

  def summary(connectionId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        usingConnection(profile.workspaceId, connectionId, request.path) { dbm =>
          dbm.getSummary()
            .fold(
              err => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage))),
              res => Ok(Json.toJson(res))
            )
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def catalogs(connectionId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        usingConnection(profile.workspaceId, connectionId, request.path) { dbm =>
          dbm.getCatalogs()
            .fold(
              err => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage))),
              res => Ok(Json.toJson(res))
            )
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def schemas(connectionId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        usingConnection(profile.workspaceId, connectionId, request.path) { dbm =>
          dbm.getSchemas()
            .fold(
              err => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage))),
              res => Ok(Json.toJson(res))
            )
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def listTables(connectionId: Long, schema: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        usingConnection(profile.workspaceId, connectionId, request.path) { dbm =>
          dbm.listTables(schema)
            .fold(
              err => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage))),
              res => Ok(Json.toJson(res))
            )
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def executeQuery(connectionId: Long) = silhouette.UserAwareAction.async(validateJson[RequestQueryExecution]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        usingConnection(profile.workspaceId, connectionId, request.path) { dbm =>
          dbm.executeQuery(request.body.q)
            .fold(
              err => InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, err.getMessage))),
              res => {
                res.fold(
                  resultCode => Ok(Json.toJson(Map("success" -> resultCode))),
                  queryResult => {
                    val str = mapper.writeValueAsString(queryResult)
                    Ok(Json.parse(str))
                  }
                )

              }
            )
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

}

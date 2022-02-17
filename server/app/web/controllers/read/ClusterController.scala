package web.controllers.read

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.gigahex.commons.models.ClusterStatus.ClusterStatus
import com.mohiva.play.silhouette.api.Silhouette
import controllers.AssetsFinder
import javax.inject.{Inject, Named}
import web.actors.clusters.ClusterManager
import web.actors.clusters.ClusterManager.StartLocalSpark
import web.models
import web.models.{ErrorResponse, InternalServerErrorResponse}
import web.models.cluster.{CommonJsonFormatter, SparkClusterJsonFormatter, SparkConfigurationRequest}
import web.services.{ClusterService, MemberService}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, InjectedController, Request, WebSocket}
import play.cache.NamedCache
import utils.auth.DefaultEnv

import concurrent.duration._
import akka.pattern._
import web.controllers.handlers.SecuredWebRequestHandler
import web.repo.clusters.ServicesNames

import scala.concurrent.{ExecutionContext, Future}

class ClusterController @Inject()(@Named("spark-cluster-manager") clusterManager: ActorRef,
                                  @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
                                  @NamedCache("session-cache") userCache: SyncCacheApi,
                                  silhouette: Silhouette[DefaultEnv],
                                  memberService: MemberService,
                                  clusterService: ClusterService,
                                  ws: WSClient
                                 )(
                                   implicit
                                   ex: ExecutionContext,
                                   assets: AssetsFinder,
                                   system: ActorSystem,
                                   mat: Materializer
                                 ) extends InjectedController
  with I18nSupport
  with SecuredWebRequestHandler
  with ErrorResponse
 with CommonJsonFormatter
  with SparkClusterJsonFormatter {

  implicit val timeout: Timeout = Timeout(10.seconds)

  private def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  /**
    * Saves the local spark configuration
    * @return
    */
  def saveLocalSparkClusterConfig: Action[SparkConfigurationRequest] =
    silhouette.UserAwareAction.async(validateJson[SparkConfigurationRequest]) { implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
          clusterService
            .saveLocalSparkConfiguration(request.body, profile.workspaceId, workspaceKeyCache)
            .map(result => {
              if(result > 0){
                Created(Json.toJson(Map("clusterId" -> result)))
              } else {
                BadRequest(Json.toJson(Map("error" -> "Spark service already installed on this host. Delete it before proceeding.")))
              }
            })
            .recoverWith {
              case e: Exception =>
                Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
            }
        } else {
          Future.successful(Forbidden)
        }
      }
    }

  def fetchSparkCluster(clusterId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getSparkCluster(clusterId, profile.workspaceId)
          .map {
            case None    => NotFound
            case Some(v) => Ok(Json.toJson(v))
          }
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def startCluster(service: String, clusterId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {

        val response = clusterService.getClusterPackageInfo(clusterId, service)

        response
          .flatMap {
            case None => Future.successful(NotFound)
            case Some(pkg) =>
              clusterManager
                .ask(ClusterManager.StartLocalCluster(pkg))
                .mapTo[ClusterStatus]
                .map(status => Ok(Json.toJson(Map("status" -> status.toString))))
          }
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def removeCluster(clusterId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterManager
          .ask(ClusterManager.DeleteLocalCluster(clusterId))
          .mapTo[Either[Throwable, Boolean]]
            .flatMap { response =>
              response match {
                case Left(value) =>   Future(InternalServerError(Json.toJson(Map("error" -> value.getMessage))))
                case Right(_) => clusterService.removeCluster(profile.orgId, profile.workspaceId, clusterId)
                  .map(k => Ok(Json.toJson(Map("clusterRemoved" -> k))))
              }
            }

      } else {
        Future.successful(Forbidden)
      }
    }
  }


  /**
    *
    * @param clusterId spark cluster Id
    * @return
    */
  def stopCluster(clusterId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        implicit val timeout: Timeout = Timeout(2.seconds)

        clusterManager
          .ask(ClusterManager.StopLocalCluster(clusterId))
          .mapTo[ClusterStatus]
          .map(status => Ok(Json.toJson(Map("status" -> status.toString))))
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamClusterMetrics(clusterId: Long) =
    WebSocket.acceptOrResult[String, String] { implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkClusterWebsocketRequest(silhouette, memberService, userCache, request) { (orgId, workspaceId) =>
        Source
          .tick(2.seconds, 2.seconds, "tick")
          .mapAsync(1)(_ => clusterService.getSparkCluster(clusterId ,workspaceId))
          .map {
            case None => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }

      }
    }

}

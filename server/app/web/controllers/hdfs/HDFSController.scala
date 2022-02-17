package web.controllers.hdfs

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.gigahex.commons.models.RunStatus
import com.mohiva.play.silhouette.api.Silhouette
import controllers.AssetsFinder
import javax.inject.{Inject, Named}
import web.actors.clusters.spark.SparkProcesses
import web.controllers.kafka.KafkaClientHandler
import web.models
import web.models.{ErrorResponse, InternalServerErrorResponse}
import web.models.cluster.{HDFSConfigurationRequest, HDFSJsonFormats, HDFSProcesses, KafkaClusterJsonFormatter, KafkaConfigurationRequest}
import web.services.{ClusterService, MemberService}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.libs.ws.{EmptyBody, WSClient}
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, InjectedController, Request, WebSocket}
import play.cache.NamedCache
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler

import concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class HDFSController @Inject()(@Named("spark-cluster-manager") clusterManager: ActorRef,
                               @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
                               @NamedCache("session-cache") userCache: SyncCacheApi,
                               silhouette: Silhouette[DefaultEnv],
                               memberService: MemberService,
                               clusterService: ClusterService,
                               ws: WSClient)(
                                implicit
                                ex: ExecutionContext,
                                assets: AssetsFinder,
                                system: ActorSystem,
                                mat: Materializer
                              ) extends InjectedController
  with I18nSupport
  with SecuredWebRequestHandler
  with ErrorResponse
  with HDFSJsonFormats {

  private def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def saveLocalHDFSClusterConfig: Action[HDFSConfigurationRequest] =
    silhouette.UserAwareAction.async(validateJson[HDFSConfigurationRequest]) { implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
          clusterService
            .saveLocalHDFSConfiguration(request.body, profile.workspaceId, workspaceKeyCache)
            .map(result => {
              if(result > 0){
                Created(Json.toJson(Map("clusterId" -> result)))
              } else {
                BadRequest(Json.toJson(Map("error" -> "There is already an existing Kafka service installed on this host. Delete it before proceeding.")))
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

  def fetchHDFSCluster(clusterId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getHDFSCluster(clusterId, profile.workspaceId)
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

  def proxyWebHDFS(clusterId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterProcess(clusterId, profile.workspaceId, HDFSProcesses.DATA_NODE).flatMap {
          case None => Future(NotFound(s"WebHDFS server is not running"))
          case Some(pd) => if(pd.status == RunStatus.Running){

            val url = s"http://${pd.host}:50070/webhdfs/v1/${path}?${request.rawQueryString}"
            println(s"URL : ${url}")
            ws.url(url).get().map{ response =>
              Ok(response.body).as("application/json")
            }
          } else {
            Future(Ok(s" ${HDFSProcesses.DATA_NODE} is not running"))
          }
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def proxyModifyWebHDFS(clusterId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterProcess(clusterId, profile.workspaceId, HDFSProcesses.DATA_NODE).flatMap {
          case None => Future(NotFound(s"WebHDFS server is not running"))
          case Some(pd) => if(pd.status == RunStatus.Running){
            val qstringWithUser = request.rawQueryString.replaceAll("user_name",System.getProperty("user.name"))
            val url = s"http://${pd.host}:50070/webhdfs/v1/${path}?${qstringWithUser}"
            println(s"URL : ${url}")
            ws.url(url).put(EmptyBody).map{ response =>
              Ok(response.body).as("application/json")
            }
          } else {
            Future(Ok(s" ${HDFSProcesses.DATA_NODE} is not running"))
          }
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def proxyDelFileWebHDFS(clusterId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterProcess(clusterId, profile.workspaceId, HDFSProcesses.DATA_NODE).flatMap {
          case None => Future(NotFound(s"WebHDFS server is not running"))
          case Some(pd) => if(pd.status == RunStatus.Running){
            val qstringWithUser = request.rawQueryString.replaceAll("user_name",System.getProperty("user.name"))
            val url = s"http://${pd.host}:50070/webhdfs/v1/${path}?${qstringWithUser}"
            println(s"URL : ${url}")
            ws.url(url).delete().map{ response =>
              Ok(response.body).as("application/json")
            }
          } else {
            Future(Ok(s" ${HDFSProcesses.DATA_NODE} is not running"))
          }
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamHDFSClusterState(clusterId: Long) =
    WebSocket.acceptOrResult[String, String] { implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkClusterWebsocketRequest(silhouette, memberService, userCache, request) { (orgId, workspaceId) =>
        Source
          .tick(2.seconds, 2.seconds, "tick")
          .mapAsync(1)(_ => clusterService.getHDFSCluster(clusterId, workspaceId))
          .map {
            case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }
      }
    }

}

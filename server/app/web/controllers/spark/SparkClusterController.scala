package web.controllers.spark

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named}
import web.actors.clusters.ClusterManager.StartLocalSpark
import web.models.ErrorResponse
import web.services.{ClusterService, MemberService}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc._
import com.gigahex.commons.models.RunStatus
import controllers.AssetsFinder
import web.actors.clusters.spark.SparkProcesses
import web.models.cluster.{SparkClusterJsonFormatter, SparkMasterSummary}
import play.api.libs.ws.WSClient
import play.cache.NamedCache
import utils.HtmlParser
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler

import concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class SparkClusterController @Inject()(@Named("spark-cluster-manager") clusterManager: ActorRef,
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
    with SparkClusterJsonFormatter {

  private def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )



  def proxyHistoryUI(clusterId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterProcess(clusterId, profile.workspaceId, SparkProcesses.SHS).flatMap {
          case None => Future(NotFound(s"History server is not running"))
          case Some(pd) => if(pd.status == RunStatus.Running){

            getResult(request.path, path, pd.host, pd.port, request.rawQueryString)
          } else {
            Future(Ok(s" ${SparkProcesses.SHS} is not running"))
          }
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def proxyHistoryAPI(clusterId: Long, path: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterProcess(clusterId, profile.workspaceId, SparkProcesses.SHS).flatMap {
          case None => Future(NotFound(s"History server is not running"))
          case Some(pd) => if(pd.status == RunStatus.Running){
            ws.url(s"http://${pd.host}:${pd.port}/${path}").get().map{ response =>
              Ok(response.body).as("application/json")
            }
          } else {
            Future(Ok(s" ${SparkProcesses.SHS} is not running"))
          }
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def proxyMasterUI(clusterId: Long, version: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService.getClusterProcess(clusterId, profile.workspaceId, SparkProcesses.MASTER).flatMap {
          case None => Future(NotFound(s"Master Web server is not running"))
          case Some(pd) => if(pd.status == RunStatus.Running){
            parseMasterUIResponse(s"http://${pd.host}:${pd.port}", version).map(result => Ok(Json.toJson(Seq(result))))
          } else {
            Future(Ok(s" ${SparkProcesses.MASTER} is not running"))
          }
        }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  private def parseMasterUIResponse(url: String, version: String): Future[SparkMasterSummary] = {
    ws.url(url).get().map { response =>
      HtmlParser.parseMasterHTML(response.body, version)
    }
  }

  private def getResult(requestPath: String, historyPath: String, host: String, port: Int, queryString: String): Future[Result] = historyPath match {

    case x if x.endsWith("css") =>
      val fileName = x.substring(x.indexOf("static"))
      ws.url(s"http://$host:$port/${fileName}").get().map{ response =>
        Ok(response.body).as("text/css")
      }

    case x if x.endsWith("js") =>
      val fileName = x.substring(x.indexOf("static"))
      ws.url(s"http://$host:$port/${fileName}").get().map{ response =>
        Ok(response.body).as("application/javascript")
      }

    case x if x.endsWith("html") =>
      val fileName = x.substring(x.indexOf("static"))
      ws.url(s"http://$host:$port/${fileName}").get().map{ response =>
        Ok(response.bodyAsBytes).as("text/html")
      }

    case x if x.endsWith("png") =>
      val fileName = x.substring(x.indexOf("static"))
      ws.url(s"http://$host:$port/${fileName}").get().map{ response =>
        Ok(response.bodyAsBytes).as("image/png")
      }

    case x if x.endsWith("js.map") =>
      val fileName = x.substring(x.indexOf("static"))
      ws.url(s"http://$host:$port/${fileName}").get().map{ response =>
        Ok(response.bodyAsBytes).as("application/javascript")
      }

    case x if x.endsWith("css.map") =>
      val fileName = x.substring(x.indexOf("static"))
      ws.url(s"http://$host:$port/${fileName}").get().map{ response =>
        Ok(response.bodyAsBytes).as("text/css")
      }

    case x if x.startsWith("api") =>
      val q = if(queryString == "") "" else s"?${queryString}"
      ws.url(s"http://${host}:${port}/${historyPath}${q}").get().map{ response =>
        Ok(response.body).as("application/json")
      }

    case _ =>
      ws.url(s"http://$host:$port/$historyPath?${queryString}").get().map{ response =>
        val contentBody = response.body
          .replaceAll("/static",s"${requestPath.substring(0, requestPath.indexOf("history"))}static")
            .replaceAll("/history", s"${requestPath.substring(0, requestPath.indexOf("history"))}history")
        Ok(views.html.master(play.twirl.api.Html(contentBody)))
      }


  }

}

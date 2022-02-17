package web.controllers.read

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.gigahex.commons.models.ClusterStatus
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named}
import web.models.{ClusterJsonFormat, ErrorResponse, IllegalParam, JobFormats, JobRequestResponseFormat}
import web.services.{ClusterService, JobService, MemberService, SparkEventService}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, InjectedController}
import play.cache.NamedCache
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler
import web.models.formats.AuthResponseFormats

import scala.concurrent.ExecutionContext

class AgentsWebController @Inject()(
                                      @Named("spark-events-manager") subscriptionManager: ActorRef,
                                      @NamedCache("job-cache") jobCache: SyncCacheApi,
                                      @NamedCache("session-cache") userCache: SyncCacheApi,
                                      components: ControllerComponents,
                                      silhouette: Silhouette[DefaultEnv],
                                      memberService: MemberService,
                                      configuration: Configuration,
                                      clusterService: ClusterService,
                                      sparkEventService: SparkEventService
                                    )(
                                      implicit
                                      ex: ExecutionContext,
                                      system: ActorSystem,
                                      mat: Materializer
                                    ) extends InjectedController
  with I18nSupport
  with AuthResponseFormats
  with ErrorResponse
  with ClusterJsonFormat
  with SecuredWebRequestHandler {

  /**
    * Get the list of clusters the user has added
    * @return
    */
  def listAgents = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrg(request, memberService, userCache) { mv =>
      clusterService.listAllClusters(mv.orgIds.head, 1L).map { cls =>
        Ok(Json.toJson(cls))
      }
    }
  }

  def listAgentsWithStatus(status: String) = silhouette.UserAwareAction.async { implicit request =>
    handleRequestWithOrg(request, memberService, userCache) { mv =>
      clusterService.listClustersByWorkspace(mv.orgIds.head, ClusterStatus.withNameOpt(status)).map { cls =>
        Ok(Json.toJson(cls))
      }
    }
  }

}

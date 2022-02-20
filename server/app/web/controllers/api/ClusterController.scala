package web.controllers.api

import akka.actor.ActorRef
import com.mohiva.play.silhouette.api.Silhouette
import controllers.AssetsFinder
import javax.inject.{Inject, Named, Singleton}
import web.models.{AuthRequestsJsonFormatter, ClusterJsonFormat}
import web.services.ClusterService
import play.api.Logging
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{ControllerComponents, InjectedController}
import play.cache.NamedCache
import utils.auth.APIJwtEnv
import web.controllers.handlers.SecuredAPIReqHander

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClusterController @Inject()(
                                  @Named("spark-events-manager") subscriptionManager: ActorRef,
                                  @NamedCache("job-cache") jobCache: SyncCacheApi,
                                  @NamedCache("session-cache") userCache: SyncCacheApi,
                                  components: ControllerComponents,
                                  silhouette: Silhouette[APIJwtEnv],
                                  clusterService: ClusterService
                                )(
                                  implicit
                                  assets: AssetsFinder,
                                  ex: ExecutionContext
                                ) extends InjectedController
  with I18nSupport
  with AuthRequestsJsonFormatter
with ClusterJsonFormat
  with Logging
  with SecuredAPIReqHander {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )



}

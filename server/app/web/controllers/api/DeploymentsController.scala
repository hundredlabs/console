package web.controllers.api

import akka.actor.ActorRef
import akka.stream.Materializer
import com.mohiva.play.silhouette.api.Silhouette
import controllers.AssetsFinder
import javax.inject.{Inject, Named}
import web.models.{AuthRequestsJsonFormatter, DeploymentJsonFormat, JobFormats}
import web.services.{ClusterService, DeploymentService, JobService}
import play.api.{Configuration, Logging}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.mvc.{ControllerComponents, InjectedController}
import play.cache.NamedCache
import utils.auth.APIJwtEnv
import play.api.libs.json.{JsError, Json, Reads}
import web.controllers.handlers.SecuredAPIReqHander

import scala.concurrent.{ExecutionContext, Future}

class DeploymentsController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[APIJwtEnv],
    jobService: JobService,
    clusterService: ClusterService,
    configuration: Configuration,
    deploymentService: DeploymentService
)(
    implicit
    assets: AssetsFinder,
    mat: Materializer,
    ex: ExecutionContext
) extends InjectedController
    with I18nSupport
    with AuthRequestsJsonFormatter
    with JobFormats
    with Logging
    with SecuredAPIReqHander {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )





}

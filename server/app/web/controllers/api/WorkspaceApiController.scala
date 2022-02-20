package web.controllers.api

import akka.actor.ActorRef
import akka.stream.Materializer
import com.gigahex.commons.constants.Headers
import com.gigahex.commons.models.{ ClusterIdResponse, ClusterState, DeploymentActionUpdate, DeploymentUpdate,  NewCluster, RunStatus}
import com.mohiva.play.silhouette.api.services.AuthenticatorResult
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.api.util.Clock
import controllers.AssetsFinder
import javax.inject.{Inject, Named}
import web.models.{ActionForbidden, AuthRequestsJsonFormatter, ClusterJsonFormat, DeploymentJsonFormat, OrgResponse, OrgWithKeys, SignInResponse, UserNotAuthenticated, WorkspaceId, WorkspaceResponse}
import web.models.common.JobErrorJson
import web.providers.{APICredentialsProvider, WorkspaceAPICredentialsProvider}
import web.services.ClusterService
import play.api.{Configuration, Logging}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents, InjectedController, Request}
import play.cache.NamedCache
import utils.auth.{APIJwtEnv, WorkspaceAPIJwtEnv}
import web.controllers.handlers.SecuredAPIReqHander
import web.models.formats.AuthResponseFormats

import scala.concurrent.{ExecutionContext, Future}

class WorkspaceApiController @Inject()(
    @Named("spark-events-manager") subscriptionManager: ActorRef,
    @NamedCache("job-cache") jobCache: SyncCacheApi,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    components: ControllerComponents,
    silhouette: Silhouette[WorkspaceAPIJwtEnv],
    credentialsProvider: WorkspaceAPICredentialsProvider,
    clusterService: ClusterService,
)(
    implicit
    assets: AssetsFinder,
    mat: Materializer,
    ex: ExecutionContext
) extends InjectedController
    with I18nSupport
    with AuthRequestsJsonFormatter
    with AuthResponseFormats
    with ClusterJsonFormat
    with JobErrorJson
    with Logging
    with SecuredAPIReqHander {

  implicit val runStatusFmt            = Json.formatEnum(RunStatus)
  implicit val deploymentUpdateFmt     = Json.format[DeploymentUpdate]
  implicit val deploymentLogsUpdateFmt = Json.format[DeploymentActionUpdate]

  def validateJson[A: Reads]: BodyParser[A] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )
  def apiLogin: Action[AnyContent] = silhouette.UnsecuredAction.async { implicit request =>
    val clientKey = request.headers.get(Headers.AUTH_ACCESS_KEY)
    val message   = request.headers.get(Headers.AUTH_REQ_ACTION).flatMap(x => clientKey.map(k => k + "," + x))
    val signature = request.headers.get(Headers.AUTH_CLIENT_SIGN)
    (clientKey, message, signature) match {
      case (Some(key), Some(msg), Some(sign)) =>
        credentialsProvider
          .authenticate(sign, msg, key)
          .flatMap(o => authenticateWorkspace(o))
          .recover {
            case e: Exception => Unauthorized(Json.toJson(SignInResponse(false, "", message = e.getMessage)))
          }
      case _ => Future.successful(Unauthorized(Json.toJson(SignInResponse(false, "", message = "Missing auth headers"))))
    }

  }

  private def authenticateWorkspace(w: WorkspaceId)(implicit request: Request[_]): Future[AuthenticatorResult] = {
    val result = Ok(Json.toJson(WorkspaceResponse(w.id, w.name)))
    silhouette.env.authenticatorService
      .create(LoginInfo(APICredentialsProvider.ID, w.key))
      .map(authenticator => authenticator)
      .flatMap { authenticator =>
        //silhouette.env.eventBus.publish(LoginEvent(user, request))
        silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
          silhouette.env.authenticatorService.embed(v, result)
        }
      }
  }




  def registerCluster: Action[NewCluster] = silhouette.UserAwareAction.async(validateJson[NewCluster]) { implicit request =>
    request.identity match {
      case None => Future.successful(Forbidden)
      case Some(WorkspaceId(id, _, _, _, _)) =>
        for {
          orgId <- clusterService.getOrgId(id)
          result <- orgId match {
            case None => Future.successful(BadRequest(Json.toJson(Map("error" -> "No org found"))))
            case Some(oId) =>
              clusterService.addCluster(oId, id, request.body).map { response =>
                response.fold(
                  ex => BadRequest(Json.toJson(Map("error" -> ex.getMessage))),
                  clusterId => Created(Json.toJson(ClusterIdResponse(clusterId)))
                )
              }
          }
        } yield result

    }
  }


}

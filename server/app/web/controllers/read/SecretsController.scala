package web.controllers.read

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.gigahex.commons.models.IntegrationType.IntegrationType
import com.gigahex.commons.models.{AWSAccountCredential, AWSUserKeys, IntegrationType, SaveSecretPool}
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.{Inject, Named}
import web.models.formats.{AuthResponseFormats, SecretsJsonFormat}
import web.models.rbac.AccessPolicy
import web.models.{ ClusterJsonFormat, ErrorResponse}
import web.services.{MemberService, SecretStore}
import play.api.cache.SyncCacheApi
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.mvc.{ControllerComponents, InjectedController}
import play.cache.NamedCache
import utils.ResourceNotFound
import utils.auth.DefaultEnv
import web.controllers.handlers.SecuredWebRequestHandler

import scala.concurrent.{ExecutionContext, Future}

class SecretsController @Inject()(
    components: ControllerComponents,
    silhouette: Silhouette[DefaultEnv],
    memberService: MemberService,
    @NamedCache("session-cache") userCache: SyncCacheApi,
    secretStore: SecretStore
)(
    implicit
    ex: ExecutionContext,
    system: ActorSystem,
    mat: Materializer
) extends InjectedController
    with I18nSupport
    with AuthResponseFormats
    with ErrorResponse
    with SecretsJsonFormat
    with SecuredWebRequestHandler {

  def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def getPubKey = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(Ok(Json.toJson(Map("key" -> secretStore.getPublicKey))))
  }


  def generateIntegrationKey(integration: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (roles.exists(p => p.subjectId == profile.orgId && p.policies.exists(_ == AccessPolicy.ORG_MANAGE))) {
        secretStore.generateIntegrationKeyPair(IntegrationType.withName(integration), profile.orgId).map(k => Ok(Json.toJson(k)))
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def validateAndFetchEMRClusters = silhouette.UserAwareAction.async(validateJson[AWSUserKeys]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (roles.exists(p => p.subjectId == profile.orgId && p.policies.exists(_ == AccessPolicy.ORG_MANAGE))) {
        Future(Ok)
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def saveSecret = silhouette.UserAwareAction.async(validateJson[AWSAccountCredential]) { implicit request =>
    val apiKey    = secretStore.decryptText(request.body.apiKey)
    val apiSecret = secretStore.decryptText(request.body.apiKeySecret)
    Future.successful(Ok)
  }

}

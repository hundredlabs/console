package web.controllers.handlers

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import utils.auth.APIJwtEnv
import web.models.{JobValue, OrgWithKeys, UserNotAuthenticated}
import web.models.formats.AuthResponseFormats

import scala.concurrent.Future

trait SecuredAPIReqHander extends AuthResponseFormats {

  def handlePostRequest[E <: Env, B](request: UserAwareRequest[E, B])(handler: (E#I, B) => Future[Result]): Future[Result] = {
    request.identity match {
      case None     => Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      case Some(id) => handler(id, request.body)
    }
  }

  def handleRequest[E <: Env, B](request: UserAwareRequest[E, B])(handler: E#I => Future[Result]): Future[Result] = {
    request.identity match {
      case None     => Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      case Some(id) => handler(id)
    }
  }



}

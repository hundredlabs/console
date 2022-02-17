package utils.auth

import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import web.models.{ErrorResponse, ForbiddenError}
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.libs.json._

import scala.concurrent.Future

/**
 * Custom unsecured error handler.
 */
class CustomUnsecuredErrorHandler extends UnsecuredErrorHandler with ErrorResponse {

  /**
   * Called when a user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   *
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthorized(implicit request: RequestHeader) = {

    Future.successful(Forbidden(Json.toJson(ForbiddenError("The requested resource is forbidden for currently logged in user", "/projects"))))
  }
}

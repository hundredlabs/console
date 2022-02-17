package web.models

import play.api.libs.json._
case class EntityNotFound(name: String, path: String, memberId: Long, message: String = "Unable to find the entity")
case class IllegalParam(path: String, memberId: Long, message: String)
case class InternalServerErrorResponse(path: String, message: String = "Internal server error")
case class ForbiddenError(message: String, correctPath: String)

trait ErrorResponse {
  implicit val entityResponseFmt         = Json.format[EntityNotFound]
  implicit val illegalParamFmt = Json.format[IllegalParam]
  implicit val serverErrorFmt = Json.format[InternalServerErrorResponse]
  implicit val forbiddenErrorFmt = Json.format[ForbiddenError]
}

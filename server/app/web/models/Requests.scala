package web.models

import play.api.libs.json._


case class NameChangeRequest(name: String)
case class PasswordChangeRequest(email: String, oldPassword: String, newPassword: String)
case class SignInRequest(rememberMe: Boolean)
case class ListBetaRequest(pageSize: Int, pageNum: Int)
case class ApproveRequest(email: String)
case class SendActivationCode(email: String, code: String)

trait AuthRequestsJsonFormatter {

  implicit val passwordChangeRequestFmt = Json.format[PasswordChangeRequest]
  implicit val nameChangeRequestFmt     = Json.format[NameChangeRequest]
  implicit val signInFmt                = Json.format[SignInRequest]
  implicit val lstBetaFmt               = Json.format[ListBetaRequest]
  implicit val sacFmt                   = Json.format[SendActivationCode]
  implicit val approveRequestFmt        = Json.format[ApproveRequest]
}


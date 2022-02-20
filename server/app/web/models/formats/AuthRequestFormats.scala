package web.models.formats

import play.api.libs.json.Json
import web.models.{
  ActionForbidden,
  BetaSignedUpUser,
  MemberInfoResponse,
  OrgDetail,
  OrgResponse,
  OrgWithKeys,
  SignInResponse,
  SignOutResponse,
  SignupResponse,
  UserNotAuthenticated,
  UserView,
  WorkspaceAPIKey,
  WorkspaceResponse,
  WorkspaceViewResponse
}
import web.models.rbac.{MemberProfile, Theme}

trait AuthResponseFormats {
  implicit val userView                 = Json.format[UserView]
  implicit val signupResponse           = Json.format[SignupResponse]
  implicit val userNotAuthenticated     = Json.format[UserNotAuthenticated]
  implicit val actionForbiddenFmt       = Json.format[ActionForbidden]
  implicit val signInResponseFmt        = Json.format[SignInResponse]
  implicit val signOutResponseFmt       = Json.format[SignOutResponse]
  implicit val themTypeFmt              = Json.formatEnum(Theme)
  implicit val memerProfileFmt          = Json.format[MemberProfile]
  implicit val memberAccResponseFmt     = Json.format[MemberInfoResponse]
  implicit val betaSignupsFmt           = Json.format[BetaSignedUpUser]
  implicit val orgsWithKeysFmt          = Json.format[OrgWithKeys]
  implicit val orgResponseFmt           = Json.format[OrgResponse]
  implicit val workspaceResponseFmt     = Json.format[WorkspaceResponse]
  implicit val workspaceAPIKeyFmt       = Json.format[WorkspaceAPIKey]
  implicit val workspaceViewResponseFmt = Json.format[WorkspaceViewResponse]
  implicit val orgDetailFmt             = Json.format[OrgDetail]
}

package web.models.requests

case class CreateOrUpdateOrg(name: String, slugId: String, thumbnail: Option[String])
case class CreateOrgWorkspace(name: String, thumbnail: Option[String], description: Option[String])
case class WorkspaceView(name: String, description: Option[String], thumbnail: Option[String], id: Long, created: String)
case class ConnectionProvider(name: String, description: String, category: String)
case class WorkspaceConnection(name: String, provider: String, encProperties: String, schemaVersion: Int)
case class ConnectionView(id: Long, name: String, schemaVersion: String, provider: String, providerCategory: String, dateCreated: Long)
case class ProvisionWorkspace(name: String, orgName: Option[String], orgSlugId: Option[String], isPersonalAccount: Boolean)
case class WorkspaceCreated(workspaceId: Long, orgSlugId: String)
import play.api.libs.json._

trait OrgRequestsJsonFormat {
  implicit val createOrgFmt = Json.format[CreateOrUpdateOrg]
  implicit val createOrgWorkspaceFmt = Json.format[CreateOrgWorkspace]
  implicit val workspaceViewFmt = Json.format[WorkspaceView]
  implicit val provisionWorkspaceFmt = Json.format[ProvisionWorkspace]
  implicit val workspaceCreatedFmt = Json.format[WorkspaceCreated]
  implicit val workspaceConnectionFmt = Json.format[WorkspaceConnection]
  implicit val connectionViewFmt = Json.format[ConnectionView]
  implicit val connectionProviderFmt = Json.format[ConnectionProvider]
}
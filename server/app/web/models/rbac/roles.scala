package web.models.rbac


case class WorkspaceKey(hexPubKey: String, pKeyVersion: String)
case class IntegrationKey(keyId: Long, pubkey: String)

object AccessRoles {
  import AccessPolicy._
  val policySeparator   = ":"
  val ORG_ADMIN         = "Organisation Admin"
  val WORKSPACE_MANAGER = "Workspace Manager"
  val WORKSPACE_MEMBER  = "Workspace Member"

  val ROLE_ORG_ADMIN         = Seq(ORG_MANAGE, ORG_BILLING)
  val ROLE_ORG_BILLING_USER  = Seq(ORG_BILLING)
  val ROLE_WORKSPACE_MANAGER = Seq(WS_MANAGE, WS_MANAGE_CLUSTERS, WS_MANAGE_INTEGRATIONS)
  val ROLE_WORKSPACE_MEMBER  = Seq(WS_READ, WS_MANAGE_CLUSTERS)
  val ROLE_WORKSPACE_VIEWER  = Seq(WS_READ)
}

case class MemberProfile(orgId: Long,
                         orgName: String,
                         orgSlugId: String,
                         workspaceId: Long,
                         workspaceName: String,
                         webTheme: Theme.Value,
                         desktopTheme: Theme.Value,
                         orgThumbnail: Option[String] = None)

object Theme extends Enumeration {
  type SubjectType = Value

  val DARK  = Value("dark")
  val LIGHT = Value("light")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(LIGHT)
}

object AccessPolicy extends Enumeration {

  protected case class Val(name: String, description: String) extends super.Val

  val ORG_MANAGE       = Val("org.manage", "Allow creation, deletion and modification of organisation and the workspace")
  val ORG_MODIFY       = Val("org.modify", "Allow modification of the organisation")
  val ORG_READ         = Val("org.delete", "Allow read only permission for organisation properties")
  val ORG_MANAGE_USERS = Val("org.manageUsers", "Add and remove users to the existing organisation")
  val ORG_BILLING      = Val("org.billing", "Update and view billing information")

  val WS_MANAGE = Val("workspace.manage", "Allow creation, deletion and modification of workspace properties")
  val WS_MODIFY = Val("workspace.modify", "Allow modification of the workspace properties")
  val WS_READ   = Val("workspace.read", "Allow viewing the workspace properties")
  val WS_MANAGE_INTEGRATIONS =
    Val("workspace.manageIntegrations", "Add, remove and update the integrations and connections for the workspace")
  val WS_MANAGE_CLUSTERS = Val("workspace.manageClusters", "Add, remove and update clusters in a workspace")
  val WS_DEPLOY_JOB      = Val("workspace.deployJob", "Allow to deploy data workloads to different clusters available in the workspace")

  import scala.language.implicitConversions
  implicit def valueToPlanetVal(x: Value): Val = x.asInstanceOf[Val]

  def parseRoles(roles: String): Seq[AccessPolicy.Value] =
    roles
      .split(":")
      .map { s =>
        values.find(p => p.name.equalsIgnoreCase(s))
      }
      .filter(_.isDefined)
      .map(_.get)
}

object SubjectType extends Enumeration {
  type SubjectType = Value

  val ORG       = Value("org")
  val WORKSPACE = Value("workspace")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(WORKSPACE)
}

case class MemberRole(subjectId: Long, subjectType: SubjectType.Value, policies: Seq[AccessPolicy.Value])

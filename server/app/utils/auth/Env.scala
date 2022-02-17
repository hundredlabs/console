package utils.auth


import web.models.{Member, OrgWithKeys, WorkspaceId}
import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.{CookieAuthenticator, JWTAuthenticator}

/**
 * The default env.
 */
trait DefaultEnv extends Env {
  type I = Member
  type A = CookieAuthenticator
}

trait WorkspaceAPIJwtEnv extends Env {
  type I = WorkspaceId
  type A = JWTAuthenticator
}

trait APIJwtEnv extends Env {
  type I = OrgWithKeys
  type A = JWTAuthenticator
}




package web.repo

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import web.models.CredentialInfo
import web.services.APISecret
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.reflect.ClassTag

class APISecretsRepository(implicit executionContext: ExecutionContext, override val classTag: ClassTag[APISecret]) extends DelegableAuthInfoDAO[APISecret]{

  implicit val session = AutoSession
  override def find(loginInfo: LoginInfo): Future[Option[APISecret]] = Future {
    blocking {
      val result = sql"select secret_key from orgs where api_key = ${loginInfo.providerKey}"
        .map(rs => APISecret(loginInfo.providerKey,rs.string("secret_key")))
        .single()
        .apply()
      result
    }
  }

  override def add(loginInfo: LoginInfo, authInfo: APISecret): Future[APISecret] = ???

  override def update(loginInfo: LoginInfo, authInfo: APISecret): Future[APISecret] = ???

  override def save(loginInfo: LoginInfo, authInfo: APISecret): Future[APISecret] = ???

  override def remove(loginInfo: LoginInfo): Future[Unit] =  Future {
    blocking {
      DB localTx { implicit session =>
        sql"delete from orgs where api_key = ${loginInfo.providerKey}"
      }
    }
  }
}

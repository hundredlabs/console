package web.repo

import web.models.CredentialInfo
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future,blocking}
import scala.reflect.ClassTag

class CredentialsRepository(implicit executionContext: ExecutionContext, override val classTag: ClassTag[CredentialInfo]) extends DelegableAuthInfoDAO[CredentialInfo] {

  implicit val session = AutoSession

  override def find(loginInfo: LoginInfo): Future[Option[CredentialInfo]] = Future {
    blocking {
    val result =  sql"select * from credentials where email = ${loginInfo.providerKey}"
        .map(rs => CredentialInfo(rs))
        .single()
        .apply()
      result
    }

  }

  override def add(loginInfo: LoginInfo, authInfo: CredentialInfo): Future[CredentialInfo] = Future {
    try {
      sql"""select id from login_info where provider_id = ${loginInfo.providerID} and provider_key = ${loginInfo.providerKey}"""
        .map(_.long("id")).single().apply() match {
        case None => authInfo
        case Some(v) =>
          val id =
            sql"""insert into credentials(email, hasher, password, salt, login_info_id)
                 values(${loginInfo.providerKey}, ${authInfo.hasher}, ${authInfo.password}, ${authInfo.salt
              .getOrElse("")}, ${v})"""
              .updateAndReturnGeneratedKey
              .apply()
          CredentialInfo(authInfo.email, authInfo.hasher, authInfo.password, authInfo.salt, Some(id))
      }


    } catch {
      case e: Exception =>

        CredentialInfo(authInfo.email, authInfo.hasher, authInfo.password, authInfo.salt, None)
    }
  }

  override def update(loginInfo: LoginInfo, authInfo: CredentialInfo): Future[CredentialInfo] = Future {
    try {
      val savedCreds = sql"select * from credentials where email = ${loginInfo.providerKey}"
        .map(rs => CredentialInfo(rs))
        .single()
        .apply()
      savedCreds match {
        case Some(cred) =>
          sql"update credentials set password = ${authInfo.password} where id = ${cred.id
            .getOrElse(-1L)}".update
            .apply()
          authInfo.copy(id = cred.id)
        case None => authInfo
      }

    } catch {
      case e: Exception =>

        CredentialInfo(authInfo.email, authInfo.hasher, authInfo.password, authInfo.salt, None)
    }
  }

  override def save(loginInfo: LoginInfo, authInfo: CredentialInfo): Future[CredentialInfo] = {
    for {
      savedCreds <- Future(
        sql"select * from credentials where email = ${loginInfo.providerKey}"
          .map(rs => CredentialInfo(rs))
          .single()
          .apply())
      updatedOrInsertedCreds <- if (savedCreds.nonEmpty) update(loginInfo, authInfo) else add(loginInfo, authInfo)
    } yield updatedOrInsertedCreds
  }

  override def remove(loginInfo: LoginInfo): Future[Unit] = Future {
    try sql"delete from credentials where email = ${loginInfo.providerKey}".update().apply()
    catch {
      case e : Exception => throw e
    }
  }

}

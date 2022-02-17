package web.repo.pg

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import web.models.{ DBLoginInfo, DBOAuth2Info}
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.reflect.ClassTag

class OAuth2InfoDAO(implicit executionContext: ExecutionContext, override val classTag: ClassTag[OAuth2Info])
    extends DelegableAuthInfoDAO[OAuth2Info] {

  private implicit val session = AutoSession

  private def getDBLoginInfo(loginInfo: LoginInfo): Option[DBLoginInfo] =
    sql"""select provider_id, provider_key, id FROM login_info where provider_id = ${loginInfo.providerID} and
             provider_key = ${loginInfo.providerKey}"""
      .map(r => DBLoginInfo(Some(r.long("id")), r.string("provider_id"), r.string("provider_key")))
      .single()
      .apply()

  override def find(loginInfo: LoginInfo): Future[Option[OAuth2Info]] = Future {
    blocking {
      val optLoginInfo = getDBLoginInfo(loginInfo)
      optLoginInfo.flatMap { dbLoginInfo =>
        sql"""SELECT * FROM oauth2_info WHERE login_info_id = ${dbLoginInfo.id.get}"""
          .map(r => OAuth2Info(r.string("access_token"), r.stringOpt("token_type"), r.intOpt("expires_in"), r.stringOpt("refresh_token")))
          .single()
          .apply()

      }
    }
  }

  override def add(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = Future {
    blocking {
      val optLoginInfo = getDBLoginInfo(loginInfo)
      optLoginInfo.map { dbLoginInfo =>
        sql"""INSERT INTO oauth2_info(access_token, token_type, expires_in, refresh_token, login_info_id)
             VALUES(${authInfo.accessToken}, ${authInfo.tokenType}, ${authInfo.expiresIn}, ${authInfo.refreshToken},
             ${dbLoginInfo.id.get})"""
          .update()
          .apply()

      }
      authInfo
    }
  }

  override def update(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = Future {
    blocking {
      val optLoginInfo = getDBLoginInfo(loginInfo)
      optLoginInfo.map { dbLoginInfo =>
        sql"""SELECT id, access_token, token_type, expires_in, refresh_token, login_info_id FROM
             oauth2_info WHERE login_info_id = ${dbLoginInfo.id.get}"""
          .map(
            r =>
              DBOAuth2Info(r.longOpt("id"),
                           r.string("access_token"),
                           r.stringOpt("token_type"),
                           r.intOpt("expires_in"),
                           r.stringOpt("refresh_token"),
                           r.long("login_info_id")))
          .single()
          .apply()
          .map { dbOauth2 =>
            sql"""UPDATE oauth2_info SET access_token = ${dbOauth2.accessToken},
                 token_type = ${dbOauth2.tokenType},
                 expires_in = ${dbOauth2.expiresIn},
                 refresh_token = ${dbOauth2.refreshToken}
                 WHERE id = ${dbOauth2.id}"""
              .update()
              .apply()
          }

      }
      authInfo
    }
  }

  override def save(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] = Future {
    blocking {
      val optLoginInfo = getDBLoginInfo(loginInfo)
      optLoginInfo.map{ dbLoginInfo =>
        val queryResult =
          sql"""SELECT oauth2.id as oauth2_id,  logins.id as login_id FROM login_info as logins LEFT OUTER JOIN
           oauth2_info as oauth2 ON logins.id = oauth2.login_info_id WHERE logins.id = ${dbLoginInfo.id.get}"""
            .map(r => (r.longOpt("login_id"), r.longOpt("oauth2_id")))
            .single()
            .apply()

        queryResult.map {
          case (Some(_), None)    => add(loginInfo, authInfo)
          case (Some(_), Some(_)) => update(loginInfo, authInfo)
        }
      }

    }
    authInfo
  }

  override def remove(loginInfo: LoginInfo): Future[Unit] = Future {
    blocking {
      val optLoginInfo = getDBLoginInfo(loginInfo)
      optLoginInfo.map { dbLoginInfo =>
        sql"""DELETE FROM oauth2_info WHERE login_info_id = ${dbLoginInfo.id.get}"""
          .update()
          .apply()
      }

    }
  }
}

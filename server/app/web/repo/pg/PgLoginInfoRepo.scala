package web.repo.pg


import java.security.SecureRandom

import com.mohiva.play.silhouette.api.LoginInfo
import web.models.{DBLoginInfo, Member}
import web.repo.LoginInfoRepo

import scala.concurrent.{ExecutionContext, Future, blocking}
import scalikejdbc._
import utils.auth.RandomGenerator

class PgLoginInfoRepo(implicit ec: ExecutionContext) extends LoginInfoRepo {



  /**
    * Get list of user authentication methods providers
    *
    * @param email user email
    * @return
    */
  override def getAuthenticationProviders(email: String): Future[Seq[String]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select login_info.provider_id from members INNER JOIN user_login_info ON members.id = user_login_info.member_id
              INNER JOIN login_info ON  login_info.id = user_login_info.login_info_id where members.email = ${email}"""
          .map(_.string("provider_id"))
          .list()
          .apply()
      }
    }
  }

  /**
    * Finds a user and login info pair by userID and login info providerID
    *
    * @param memberId     user id
    * @param providerId provider id
    * @return Some(User, LoginInfo) if there is a user by userId which has login method for provider by provider ID, otherwise None
    */
  override def find(memberId: Long, providerId: String): Future[Option[Member]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select name, email, member_type, provider_id, provider_key, receive_updates, dt_joined from members INNER JOIN user_login_info
               ON members.id = user_login_info.member_id
              INNER JOIN login_info ON where login_info.id = user_logins.login_info_id WHERE members.id = ${memberId}
              AND login_info.provider_id = ${providerId}"""
          .map(r =>
            new Member(
              r.string("name"),
              r.string("email"),
              r.string("member_type"),
              r.boolean("receive_udpates"),
              r.boolean("activated"),
              LoginInfo(r.string("provider_id"), r.string("provider_key")),

              r.dateTime("dt_joined"),
              Some(memberId)
          ))
          .single()
          .apply()
      }
    }
  }

  /**
    * Saves a login info for user
    *
    * @param memberId    The user id.
    * @param loginInfo login info
    * @return unit
    */
  override def saveUserLoginInfo(memberId: Long, loginInfo: LoginInfo): Future[Unit] = Future {
    blocking {
      DB localTx  { implicit session =>
      val optLoginInfo = sql"""select provider_id, provider_key, id FROM login_info where provider_id = ${loginInfo.providerID} and
             provider_key = ${loginInfo.providerKey}"""
          .map(r => DBLoginInfo(Some(r.long("id")), r.string("provider_id"), r.string("provider_key")))
          .single().apply()

          val dbLoginInfo = optLoginInfo match {
          case None =>
            val id = sql"""INSERT INTO login_info(provider_id, provider_key) VALUES(${loginInfo.providerID}, ${loginInfo.providerKey})"""
              .updateAndReturnGeneratedKey()
              .apply()
            DBLoginInfo(Some(id), loginInfo.providerID, loginInfo.providerKey)
          case Some(value) => value
        }


        val exists = sql"""SELECT member_id FROM user_login_info where member_id = ${memberId} AND login_info_id = ${dbLoginInfo.id.get}"""
          .map(_.string("member_id"))
          .single().apply().isDefined


        if(!exists){
          sql"""INSERT into user_login_info(member_id, login_info_id, login_token)
                VALUES(${memberId}, ${dbLoginInfo.id.get}, ${RandomGenerator.generate()})"""
            .update().apply()
        } else {
          sql"""UPDATE user_login_info SET login_token = ${RandomGenerator.generate()}
                WHERE member_id = ${memberId} AND login_info_id = ${dbLoginInfo.id.get}"""
            .update().apply()
        }

      }
    }
  }
}

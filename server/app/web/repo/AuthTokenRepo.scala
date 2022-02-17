package web.repo

import java.time.ZonedDateTime
import java.util.UUID

import javax.inject.Inject
import web.models.AuthToken
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future, blocking}

class AuthTokenRepo @Inject()(implicit ec: ExecutionContext) {

  /**
    * Finds a token by its ID.
    *
    * @param id The unique token ID.
    * @return The found token or None if no token for the given ID could be found.
    */
  def find(id: String): Future[Option[AuthToken]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select member_id, expiry from auth_token where id = ${id}"""
          .map(r => AuthToken(id, r.long("member_id"), r.dateTime("expiry")))
          .single()
          .apply()
      }
    }
  }

  /**
    * Finds expired tokens.
    *
    */
  def findExpired(): Future[Seq[AuthToken]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select member_id, expiry, id from auth_token where expiry > ${ZonedDateTime.now()}"""
          .map(r => AuthToken(r.string("id"), r.long("member_id"), r.dateTime("expiry")))
          .list()
          .apply()
      }
    }
  }


  /**
    * Saves a token.
    *
    * @param token The token to save.
    * @return The saved token.
    */
  def save(token: AuthToken): Future[Unit] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""insert into auth_token(id, member_id, expiry) VALUES(${token.id}, ${token.memberId}, ${token.expiry})"""
          .update()
          .apply()
      }
    }
  }

  /**
    * Removes the token for the given ID.
    *
    * @param id The ID for which the token should be removed.
    * @return A future to wait for the process to be completed.
    */
  def remove(id: String) = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""delete from auth_token where id = ${id})"""
          .update()
          .apply()
      }
    }
  }

}

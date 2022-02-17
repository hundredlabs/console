package web.services

import java.time.ZonedDateTime
import java.util.UUID
import scala.language.postfixOps
import javax.inject.Inject
import web.models.AuthToken
import web.repo.AuthTokenRepo

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
/**
  * Handles actions to auth tokens.
  */
trait AuthTokenService {

  /**
    * Creates a new auth token and saves it in the backing store.
    *
    * @param memberId The user ID for which the token should be created.
    * @param expiry The duration a token expires.
    * @return The saved auth token.
    */
  def create(memberId: Long, expiry: FiniteDuration = 5 minutes): Future[AuthToken]

  /**
    * Validates a token ID.
    *
    * @param id The token ID to validate.
    * @return The token if it's valid, None otherwise.
    */
  def validate(id: String): Future[Option[AuthToken]]

  /**
    * Cleans expired tokens.
    *
    * @return The list of deleted tokens.
    */
  def clean: Future[Seq[AuthToken]]
}

/**
  * Handles actions to auth tokens.
  *
  * @param authTokenDAO The auth token DAO implementation.
  * @param ex           The execution context.
  */
class AuthTokenServiceImpl @Inject()(authTokenDAO: AuthTokenRepo)(implicit ex: ExecutionContext) extends AuthTokenService {

  /**
    * Creates a new auth token and saves it in the backing store.
    *
    * @param memberId The user ID for which the token should be created.
    * @param expiry The duration a token expires.
    * @return The saved auth token.
    */
  def create(memberId: Long, expiry: FiniteDuration = 12 hours) = {
    val token = AuthToken(UUID.randomUUID().toString+ "-" + System.currentTimeMillis(), memberId, ZonedDateTime.now().plusSeconds(expiry.toSeconds))
    authTokenDAO.save(token).map(_ => token)
  }

  /**
    * Validates a token ID.
    *
    * @param id The token ID to validate.
    * @return The token if it's valid, None otherwise.
    */
  def validate(id: String) = authTokenDAO.find(id)

  /**
    * Cleans expired tokens.
    *
    * @return The list of deleted tokens.
    */
  def clean = authTokenDAO.findExpired().flatMap { tokens =>
    Future.sequence(tokens.map { token =>
      authTokenDAO.remove(token.id).map(_ => token)
    })
  }
}


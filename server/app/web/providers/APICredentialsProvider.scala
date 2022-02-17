package web.providers

import java.nio.charset.Charset

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasherRegistry}
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import com.mohiva.play.silhouette.impl.providers.PasswordProvider
import com.mohiva.play.silhouette.impl.providers.PasswordProvider.{PasswordDoesNotMatch, PasswordInfoNotFound}
import javax.inject.{Inject, Named}
import web.services.{APISecret, MemberService, OrgService}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import web.models.OrgWithKeys
import play.api.libs.Codecs

import scala.concurrent.{ExecutionContext, Future}

class APICredentialsProvider @Inject()(protected val authInfoRepository: AuthInfoRepository,
                                       protected val orgService: OrgService,
                                        protected val passwordHasherRegistry: PasswordHasherRegistry)(implicit val executionContext: ExecutionContext)
  extends PasswordProvider{



  override def id: String = APICredentialsProvider.ID

  private val HMAC_SHA_256 = "HmacSHA256"

  /**
    * Authenticates an org with its API KEY and SECRET KEY.
    *
    * @param clientSignature The client signature hashed with private key
    * @param message The orginal message string
    * @return The login info if the authentication was successful, otherwise a failure.
    */
  def authenticate(clientSignature: String, message: String, clientAPIKey: String): Future[OrgWithKeys] = {
    orgService.retriveOrg(clientAPIKey).map {
       case Some(org) if matches(clientSignature, message, org.secret) => org
       case Some(_) => throw new InvalidPasswordException("Invalid client signature")
       case None => throw new IdentityNotFoundException("API Key details not found")

     }

  }
  /**
  *
    * @param loginInfo providerID is the orginal message and provider key is the api key
    * @param signature the Signature calculated by the client
    * @return
    */
  override def authenticate(loginInfo: LoginInfo, signature: String): Future[State] = {
    authInfoRepository.find[APISecret](loginInfo).flatMap {

      case Some(result) if matches(signature, loginInfo.providerID, result.secret) => Future.successful(Authenticated)
      case Some(_) => Future.successful(InvalidPassword(PasswordDoesNotMatch.format(id)))
      case None => Future.successful(NotFound(PasswordInfoNotFound.format(id, loginInfo)))
    }
  }

  private[providers] def matches(signature: String, message: String, secretKey: String): Boolean = {
    val mac = Mac.getInstance(HMAC_SHA_256)
    mac.init(new SecretKeySpec(secretKey.getBytes(Charset.forName("UTF-8")), HMAC_SHA_256))
    val signatureBytes = mac.doFinal(message.getBytes(Charset.forName("UTF-8")))
    Codecs.toHexString(signatureBytes).equalsIgnoreCase(signature)
  }


}

object APICredentialsProvider {
  val ID = "api-key"
}

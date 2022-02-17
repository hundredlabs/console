package web.providers

import java.nio.charset.Charset

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import com.mohiva.play.silhouette.impl.exceptions.{IdentityNotFoundException, InvalidPasswordException}
import com.mohiva.play.silhouette.impl.providers.PasswordProvider
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import web.models.{OrgWithKeys, WorkspaceId}
import web.services.{MemberService, OrgService, SecretStore, WorkspaceService}
import play.api.libs.Codecs

import scala.concurrent.{ExecutionContext, Future}

class WorkspaceAPICredentialsProvider @Inject()(protected val authInfoRepository: AuthInfoRepository,
                                             protected val workspaceService: WorkspaceService,
                                                secretStore: SecretStore,
                                             protected val passwordHasherRegistry: PasswordHasherRegistry)
                                             (implicit val executionContext: ExecutionContext) extends PasswordProvider{
  override def id: String = APICredentialsProvider.ID

  private val HMAC_SHA_256 = "HmacSHA256"

  /**
    * Authenticates an org with its API KEY and SECRET KEY.
    *
    * @param clientSignature The client signature hashed with private key
    * @param message The orginal message string
    * @return The login info if the authentication was successful, otherwise a failure.
    */
  def authenticate(clientSignature: String, message: String,  apiKey: String): Future[WorkspaceId] = {

    workspaceService.retrieveWorkspace(apiKey, secretStore).map {
      case Some(ws) if matches(clientSignature, message, ws.secretKey) => ws
      case Some(_) => throw new InvalidPasswordException("Invalid client signature")
      case None => throw new IdentityNotFoundException("API Key details not found")
    }

  }

  private[providers] def matches(signature: String, message: String, secretKey: String): Boolean = {
    val mac = Mac.getInstance(HMAC_SHA_256)
    mac.init(new SecretKeySpec(secretKey.getBytes(Charset.forName("UTF-8")), HMAC_SHA_256))
    val signatureBytes = mac.doFinal(message.getBytes(Charset.forName("UTF-8")))
    Codecs.toHexString(signatureBytes).equalsIgnoreCase(signature)
  }

}

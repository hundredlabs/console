package web.services

import java.security.SecureRandom

import com.mohiva.play.silhouette.api.AuthInfo
import play.api.libs.Codecs

trait APISecretsGenerator {

  def genKey(keyLen: Int, suffix: Long): APISecret

}

case class APISecret(key: String, secret: String) extends AuthInfo
object IDGeneratorUtil {
  lazy val random = new SecureRandom()
}
class DefaultKeyPairGenerator extends APISecretsGenerator {

  private[web] def getRandomString(len: Int): String = {
    val randomValue = new Array[Byte](len)
    IDGeneratorUtil.random.nextBytes(randomValue)
    Codecs.toHexString(randomValue)
  }

  override def genKey(keyLen: Int, suffix: Long): APISecret = {
    APISecret(
      key = getRandomString(keyLen) + suffix,
      secret = getRandomString(keyLen * 2) + suffix
    )
  }

}
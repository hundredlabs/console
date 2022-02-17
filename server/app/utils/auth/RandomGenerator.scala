package utils.auth

import java.security.SecureRandom

object RandomGenerator {

  val DEFAULT_CODEC = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    .toCharArray()
  val random = new SecureRandom()

  def generate() : String = {
    val verifierBytes = new Array[Byte](20)
    random.nextBytes(verifierBytes)
    val chars = new Array[Char](verifierBytes.length)
    var i = 0
    while ( {
      i < verifierBytes.length
    }) {
      chars(i) = DEFAULT_CODEC((verifierBytes(i) & 0xFF) % DEFAULT_CODEC.length)

      {
        i += 1; i - 1
      }
    }
    new String(chars)
  }


}

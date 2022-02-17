package com.gigahex.commons.models

sealed trait SecretPool {
  val name: String
  val poolType: SecretPoolType.Value

  def secrets: Map[String, String]

}
import SecretNames._
case class AWSAccountCredential(apiKey: String,
                            apiKeySecret: String,
                            name: String,
                            poolType: SecretPoolType.Value = SecretPoolType.AWS_USER_KEYS)
    extends SecretPool {
  override def secrets: Map[String, String] = Map(SecretNames.AWS_ACCESS_KEY_ID -> apiKey,
    SecretNames.AWS_SECRET_ACCESS_KEY -> apiKeySecret)
}
sealed trait AccountCredential
case class AWSUserKeys(keyId: Long, cipherApiKey: String, cipherApiSecretKey: String) extends AccountCredential
case class DatabricksToken(token: String) extends AccountCredential
case class GCPCredentialJson(content: String) extends AccountCredential


case class DBCredentials(username: String,
                         password: String,
                         host: String,
                         database: String,
                         name: String,
                         poolType: SecretPoolType.Value = SecretPoolType.DB_CONN)
    extends SecretPool {
  override def secrets: Map[String, String] = Map(DB_USERNAME -> username,
    DB_HOST -> host,
    DB_PASSWORD -> password,
    DB_DEFAULT -> database)
}

object SecretNames {
  val AWS_ACCESS_KEY_ID     = "AWS_ACCESS_KEY_ID"
  val AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY"
  val DATABRICKS_TOKEN = "DATABRICKS_TOKEN"
  val DB_USERNAME = "DB_USERNAME"
  val DB_HOST = "DB_HOST"
  val DB_PASSWORD = "DB_PASSWORD"
  val DB_DEFAULT = "DB_DEFAULT"


}

case class UserSecret(id: Long, pubKey: String)

object IntegrationType extends Enumeration {
  type IntegrationType = Value

  val EMR: Value          = Value("EMR")
  val DATABRICKS: Value                = Value("DATABRICKS")
  val GCP: Value                = Value("GCP")
  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(EMR)
}

object SecretPoolType extends Enumeration {
  type SecretPoolType = Value

  val AWS_USER_KEYS: Value          = Value("AWS_USER_KEYS")
  val DB_CONN: Value                = Value("DB_CONN")
  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(AWS_USER_KEYS)
}

case class SaveSecretPool(keyId: Long, pool: SecretPool)

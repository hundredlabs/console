package web.services

import com.gigahex.commons.models.{AWSUserKeys, AccountCredential, DatabricksToken, GCPCredentialJson, SecretNames, SecretPool}
import com.goterl.lazycode.lazysodium.utils.{Key, KeyPair}
import com.goterl.lazycode.lazysodium.{LazySodiumJava, SodiumJava}
import javax.inject.{Inject, Singleton}
import web.models.rbac.{IntegrationKey, WorkspaceKey}

import com.gigahex.commons.models.IntegrationType.IntegrationType
import play.api.cache.SyncCacheApi
import scalikejdbc._
import scalikejdbc.config.DBs

import scala.concurrent.{ExecutionContext, Future, blocking}

@Singleton
class SecretStore @Inject()(
                             sodium: SodiumJava,
                           )(implicit ec: ExecutionContext) {
  private val lazySodium                                   = new LazySodiumJava(sodium)
  private val keyPair                                      = lazySodium.cryptoBoxKeypair()
  private var globalKeyPair: KeyPair = _


  private[this] def getIntegrationSecretId(keyId: Long, name: String) = s"INTEGRATION_${keyId}_${name}"

  def init(): SecretStore = {
    DBs.setupAll()
    DB localTx { implicit session =>
      globalKeyPair = sql"""SELECT hex_public_key, hex_private_key FROM global_secret""".map(r => {
        new KeyPair(Key.fromHexString(r.string("hex_public_key")), Key.fromHexString(r.string("hex_private_key")))
      }).single().apply() match {
        case None =>
          sql"""INSERT INTO global_secret(hex_public_key, hex_private_key)
            VALUES(${keyPair.getPublicKey.getAsHexString}, ${keyPair.getSecretKey.getAsHexString})"""
            .executeUpdate().apply()
          keyPair
        case Some(value) => value
      }
      this
    }
  }

  def decryptText(cipherText: String): String = lazySodium.cryptoBoxSealOpenEasy(cipherText, keyPair)

  def getPublicKey: String = keyPair.getPublicKey.getAsHexString()

  def generateKeyPair: KeyPair = lazySodium.cryptoBoxKeypair()

  def encrypt(text: String, usingKeyPair: KeyPair): String = lazySodium.cryptoBoxSealEasy(text, usingKeyPair.getPublicKey)

  def decrypt(cipherText: String, usingKeyPair: KeyPair): String = lazySodium.cryptoBoxSealOpenEasy(cipherText, usingKeyPair)

  def encrypt(text: String): String = {
    encrypt(text, globalKeyPair)

  }

  def decrypt(cipherText: String): String = {
    decrypt(cipherText, globalKeyPair)
  }

//  def encryptPKey(key: Array[Byte]): String = {
//    val keySpec    = new X509EncodedKeySpec(globalPubKey)
//    val rsaKey     = KeyFactory.getInstance("RSA").generatePublic(keySpec)
//    val cipher     = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
//    val oaepParams = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
//    cipher.init(Cipher.ENCRYPT_MODE, rsaKey, oaepParams)
//    val cipherText = cipher.doFinal(key)
//    Hex.encodeHexString(cipherText)
//  }

//  def decryptPrivateKey(cipherText: String): Array[Byte] = {
//    kmsClient.asymmetricDecrypt(keyVersionName, ByteString.copyFrom(Hex.decodeHex(cipherText))).getPlaintext.toByteArray
//  }

//  private def saveWorkspacePrivateKey(keyPair: KeyPair, workspaceSlugId: String): WorkspaceKey = DB localTx { implicit session =>
//    //Save the private key to GCP Secret store
//    val secretId = getPkSecretId(workspaceSlugId)
//    val secretBuilder = Secret.newBuilder
//      .setName(secretId)
//      .setReplication(replication)
//      .build
//
//    secretManagerServiceClient.createSecret(projectId, secretId, secretBuilder)
//    val secretPayload = SecretPayload.newBuilder().setData(ByteString.copyFrom(keyPair.getSecretKey.getAsBytes)).build()
//    val addedVersion  = secretManagerServiceClient.addSecretVersion(SecretName.of(projectId.getProject, secretId), secretPayload)
//    addedVersion.getName
//    WorkspaceKey(keyPair.getPublicKey.getAsHexString, addedVersion.getName)
//  }

//  def saveMemberPrivateKey(memberId: Long, apiKey: String, apiSecret: String): String = {
//
//    val secretBuilder = Secret.newBuilder
//      .setName(apiKey)
//      .setReplication(replication)
//      .build
//
//    secretManagerServiceClient.createSecret(projectId, apiKey, secretBuilder)
//
//    val secretPayload = SecretPayload.newBuilder().setData(ByteString.copyFrom(Hex.decodeHex(apiSecret))).build()
//    val addedVersion  = secretManagerServiceClient.addSecretVersion(SecretName.of(projectId.getProject, apiKey), secretPayload)
//    addedVersion.getName
//  }

//  private def getSecretFromGCP(secretId: String): String = {
//    val name    = SecretName.of(projectId.getProject, secretId)
//    val version = secretManagerServiceClient.listSecretVersions(name).iterateAll().asScala.head
//    secretManagerServiceClient.accessSecretVersion(version.getName).getPayload.getData.toStringUtf8
//  }

  def getIntegrationSecrets(orgId: Long, integrationType: IntegrationType): Future[Option[AccountCredential]] = {
    Future {
      blocking {
        DB readOnly { implicit session =>
          val keyId = sql"""SELECT id from org_integrations WHERE org_id = $orgId AND integration_type = ${integrationType.toString}"""
            .map(_.long("id"))
            .list()
            .apply()
            .headOption

          keyId.map { id =>
            integrationType match {
              case com.gigahex.commons.models.IntegrationType.EMR =>
                AWSUserKeys(
                  id,
                  "",
                  ""
                )
              case com.gigahex.commons.models.IntegrationType.DATABRICKS =>
                DatabricksToken("")
              case com.gigahex.commons.models.IntegrationType.GCP =>
                GCPCredentialJson("")
            }
          }
        }
      }
    }
  }

  def getWorkspacePublicKey(workspaceId: Long, cache: SyncCacheApi): Future[Option[String]] =
    Future {
      blocking{
        getWorkspaceKeyPair(workspaceId, cache).map(_.getPublicKey.getAsHexString)
      }
    }

  def getWorkspaceKeyPair(workspaceId: Long, workspaceKeyPairs: SyncCacheApi): Option[KeyPair] =
    workspaceKeyPairs.getOrElseUpdate(workspaceId.toString) {
      DB readOnly { implicit session =>
        sql"""SELECT hex_pub_key, hex_private_key FROM workspace_crypto_keypairs WHERE workspace_id = $workspaceId"""
          .map(r => (r.string("hex_pub_key"), r.string("hex_private_key")))
          .single()
          .apply()
          .map { strKeyPair =>
            val (pubKey, privateKey) = strKeyPair
            new KeyPair(Key.fromHexString(pubKey), Key.fromHexString(privateKey))

          }
      }
    }

  def decryptText(cipherText: String, workspaceId: Long, keyPairCache: SyncCacheApi) : Option[String] = {
    getWorkspaceKeyPair(workspaceId, keyPairCache).map { kp =>
      decrypt(cipherText, kp)
    }
  }

  def getAWSKey(keyId: Long, cipherAWSKey: String, cipherAWSSecretKey: String): Future[Option[(String, String)]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT hex_pub_key, encrypted_pkey FROM org_integrations WHERE id = $keyId"""
          .map(r => (r.string("hex_pub_key"), r.string("encrypted_pkey")))
          .single()
          .apply()
          .map { strKeyPair =>
            val (pubKey, privateKey) = strKeyPair


            val keyPair         = new KeyPair(Key.fromHexString(pubKey), Key.fromHexString(privateKey))
            val awsAPIKey       = lazySodium.cryptoBoxSealOpenEasy(cipherAWSKey, keyPair)
            val awsAPISecretKey = lazySodium.cryptoBoxSealOpenEasy(cipherAWSSecretKey, keyPair)

            (awsAPIKey, awsAPISecretKey)
          }
      }
    }
  }

//  private def saveGCPSecrets(secretId: String, secret: String): String = {
//
//    val secretBuilder = Secret.newBuilder
//      .setName(secretId)
//      .setReplication(replication)
//      .build
//
//    secretManagerServiceClient.createSecret(projectId, secretId, secretBuilder)
//    val secretPayload = SecretPayload.newBuilder().setData(ByteString.copyFrom(secret.getBytes(StandardCharsets.UTF_8))).build
//    val addedVersion  = secretManagerServiceClient.addSecretVersion(SecretName.of(projectId.getProject, secretId), secretPayload)
//    addedVersion.getName
//  }

//  def generateWorkspaceKeyPair(workspaceSlugId: String, orgId: Long): WorkspaceKey =
//    DB readOnly { implicit session =>
//      sql"""select key_version, hex_pub_key FROM workspaces WHERE slug_id = ${workspaceSlugId} AND org_id = ${orgId}"""
//        .map(r => WorkspaceKey(r.string("hex_pub_key"), r.string("key_version")))
//        .single()
//        .apply() match {
//        case None        => saveWorkspacePrivateKey(lazySodium.cryptoBoxKeypair(), workspaceSlugId)
//        case Some(value) => value
//      }
//    }

  def generateIntegrationKeyPair(integration: IntegrationType, orgId: Long): Future[IntegrationKey] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val keyPair = lazySodium.cryptoBoxKeypair()

          val keyId = sql"""INSERT INTO org_integrations(hex_pub_key, encrypted_pkey, integration_type, org_id)
           VALUES(${keyPair.getPublicKey.getAsHexString}, ${keyPair.getSecretKey.getAsHexString}, ${integration.toString}, $orgId)"""
            .updateAndReturnGeneratedKey()
            .apply()

          IntegrationKey(keyId, keyPair.getPublicKey.getAsHexString)
        }
      }
    }

  def remoteIntegrationKeyPair(keyId: Long): Future[Boolean] =
    Future {
      blocking {
        DB localTx { implicit session =>
          sql"""DELETE FROM org_integrations WHERE id = ${keyId}"""
            .update()
            .apply() > 0
        }
      }
    }

  def saveAccountSecrets(pool: SecretPool, keyId: Long): Future[Unit] = Future {
    blocking {

      pool.secrets.map {
        case (name, secret) => Future{}
      }

    }

  }

}

//create a keypair and save to user table and the private key to GCP secrets
//for every account or secret cred - a new entry in the creds table

package web.models.formats

import com.gigahex.commons.models.{AWSAccountCredential, AWSUserKeys, ClusterProvider, DBCredentials, SaveSecretPool, SecretPool, SecretPoolType, UserSecret}
import web.models.cloud.CloudCluster
import web.models.rbac.IntegrationKey
import play.api.libs.json._

trait SecretsJsonFormat {
  implicit val secretPoolTypeFmt = Json.formatEnum(SecretPoolType)
  implicit val clusterProviderFmt = Json.formatEnum(ClusterProvider)
  implicit val jsAWSAccount = Json.format[AWSAccountCredential]
  implicit val dbCredsFmt = Json.format[DBCredentials]
  implicit val jsSecretPool = Json.format[SecretPool]
  implicit val jsUserKey = Json.format[UserSecret]
  implicit val jsSaveSecretPool = Json.format[SaveSecretPool]
  implicit val integrationKeyFmt = Json.format[IntegrationKey]
  implicit val AWSUserKeysFmt = Json.format[AWSUserKeys]
  implicit val cloudClusterFmt = Json.format[CloudCluster]
}

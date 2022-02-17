package web.services

import com.amazonaws.regions.Regions
import com.gigahex.commons.models.IntegrationType.IntegrationType
import javax.inject.Inject
import web.cloud.aws.EMRClusterProvider


import scala.concurrent.{ExecutionContext, Future}

class CloudService @Inject()(emr: EMRClusterProvider, secretStore: SecretStore)(implicit ec: ExecutionContext){

  private val emrRegions = Seq(Regions.AF_SOUTH_1, Regions.AP_EAST_1, Regions.AP_NORTHEAST_1, Regions.AP_NORTHEAST_2,
    Regions.AP_SOUTH_1, Regions.AP_SOUTHEAST_1, Regions.AP_SOUTHEAST_2, Regions.CA_CENTRAL_1, Regions.CN_NORTH_1, Regions.CN_NORTHWEST_1,
    Regions.DEFAULT_REGION, Regions.EU_CENTRAL_1, Regions.EU_NORTH_1, Regions.EU_SOUTH_1, Regions.EU_WEST_1, Regions.EU_WEST_2, Regions.EU_WEST_3,
    Regions.ME_SOUTH_1, Regions.SA_EAST_1, Regions.US_EAST_1, Regions.US_EAST_2, Regions.US_WEST_1, Regions.US_WEST_2).map(_.getName)

  private val gcpRegions = Seq("asia-east1", "asia-east2", "asia-northeast1", "asia-northeast2", "asia-northeast3", "asia-south1",
  "asia-southeast1, asia-southeast2, australia-southeast1, europe-north1, europe-west1, europe-west2, europe-west3, europe-west4, europe-west6")

  private def regions(integrationType: IntegrationType): Seq[String] = {
    integrationType match {
      case com.gigahex.commons.models.IntegrationType.EMR =>  emrRegions
      case com.gigahex.commons.models.IntegrationType.DATABRICKS => emrRegions
      case com.gigahex.commons.models.IntegrationType.GCP => gcpRegions
    }
  }

  def listRegions(orgId: Long, integrationType: IntegrationType): Future[Option[Seq[String]]] = {
    for {
     secrets <- secretStore.getIntegrationSecrets(orgId, integrationType)
     regions <- secrets match {
       case None => Future.successful(None)
       case Some(_) => Future.successful(Some(regions(integrationType)))
     }
    } yield regions
  }




}

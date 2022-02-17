package web.cloud.aws

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.regions.Regions
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder
import com.amazonaws.services.elasticmapreduce.model.{AddJobFlowStepsRequest, HadoopJarStepConfig, ListClustersRequest, ListStepsRequest, StepConfig}
import web.models.cloud.EMRView
import java.util

import com.gigahex.commons.models.SparkJobConfig

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try

class EMRClusterProvider {

  val activeClusterStates = Seq("STARTING", "BOOTSTRAPPING", "WAITING", "RUNNING", "TERMINATED")

  def listActiveEMRClusters(apiKey: String, apiSecretKey: String, region: String)(implicit ec: ExecutionContext): Future[Either[Throwable, Seq[EMRView]]] = {
    val awsCredential = new BasicAWSCredentials(apiKey, apiSecretKey)
    val emr = AmazonElasticMapReduceClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(awsCredential))
      .withRegion(Regions.fromName(region))
      .build()

    Future {
      blocking {
        Try{
          emr
            .listClusters(new ListClustersRequest().withClusterStates(activeClusterStates: _*))
            .getClusters
            .asScala
            .toSeq
            .map(c => EMRView(c.getName, c.getId, c.getStatus.getState, c.getClusterArn, c.getNormalizedInstanceHours))
        }.toEither
      }
    }
  }

  def runSparkJob(clusterId: String, region: String, config: SparkJobConfig, apiKey: String, apiSecretKey: String)(
      implicit ec: ExecutionContext): Future[Either[Throwable, String]] = {
    val awsCredential = new BasicAWSCredentials(apiKey, apiSecretKey)
    val emr = AmazonElasticMapReduceClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(awsCredential))
      .withRegion(Regions.valueOf(region))
      .build()

    val sparkJobReq = new AddJobFlowStepsRequest()
    sparkJobReq.withJobFlowId(clusterId)
    val stepConfigs = new util.ArrayList[StepConfig]

    val sparkStepConf = new HadoopJarStepConfig().withJar("command-runner.jar").withArgs(config.getCommandString: _*)

    val sparkStep = new StepConfig().withName(s"Run ${config.jobName}")
      .withActionOnFailure("CONTINUE")
      .withHadoopJarStep(sparkStepConf)
    stepConfigs.add(sparkStep)
    sparkJobReq.withSteps(stepConfigs)
    val submittedStep = emr.addJobFlowSteps(sparkJobReq).getStepIds.asScala.head
    emr
      .listSteps(
        new ListStepsRequest()
        .withClusterId(clusterId)
          .withStepIds(submittedStep)
      )
      .getSteps().asScala.head.getStatus



    Future {
      blocking {
        Try(emr.addJobFlowSteps(sparkJobReq).getStepIds.asScala.head).toEither
      }
    }
  }

}

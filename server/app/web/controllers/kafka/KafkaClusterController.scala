package web.controllers.kafka

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.mohiva.play.silhouette.api.Silhouette
import controllers.AssetsFinder
import javax.inject.{Inject, Named}
import web.models.{ErrorResponse, InternalServerErrorResponse}
import web.models.cluster.{
  ConsumerGroupInfo,
  ConsumerMember,
  KafkaClusterJsonFormatter,
  KafkaConfigurationRequest,
  KafkaNode,
  KafkaProcesses,
  TopicConfig
}
import web.services.{ClusterService, MemberService}
import play.api.cache.SyncCacheApi

import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsError, Json, Reads}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, InjectedController, Request, WebSocket}
import play.cache.NamedCache
import utils.auth.DefaultEnv

import concurrent.duration._
import akka.stream.scaladsl.Source
import com.gigahex.commons.models.RunStatus
import web.models
import org.apache.kafka.clients.admin.{NewTopic, OffsetSpec}
import org.apache.kafka.common.TopicPartition
import web.controllers.handlers.SecuredWebRequestHandler

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class KafkaClusterController @Inject()(@Named("spark-cluster-manager") clusterManager: ActorRef,
                                       @NamedCache("workspace-keypairs") workspaceKeyCache: SyncCacheApi,
                                       @NamedCache("session-cache") userCache: SyncCacheApi,
                                       silhouette: Silhouette[DefaultEnv],
                                       memberService: MemberService,
                                       clusterService: ClusterService,
                                       ws: WSClient)(
    implicit
    ex: ExecutionContext,
    assets: AssetsFinder,
    system: ActorSystem,
    mat: Materializer
) extends InjectedController
    with I18nSupport
    with SecuredWebRequestHandler
    with ErrorResponse
    with KafkaClusterJsonFormatter
    with KafkaClientHandler {

  private def validateJson[A: Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def saveLocalKafkaClusterConfig: Action[KafkaConfigurationRequest] =
    silhouette.UserAwareAction.async(validateJson[KafkaConfigurationRequest]) { implicit request =>
      handleMemberRequest(request, memberService) { (roles, profile) =>
        if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
          clusterService
            .saveLocalKafkaConfiguration(request.body, profile.workspaceId, workspaceKeyCache)
            .map(result => {
              if (result > 0) {
                Created(Json.toJson(Map("clusterId" -> result)))
              } else {
                BadRequest(Json.toJson(
                  Map("error" -> "There is already an existing Kafka service installed on this host. Delete it before proceeding.")))
              }
            })
            .recoverWith {
              case e: Exception =>
                Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
            }
        } else {
          Future.successful(Forbidden)
        }
      }
    }

  def fetchKafkaCluster(clusterId: Long): Action[AnyContent] = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .map {
            case None    => NotFound
            case Some(v) => Ok(Json.toJson(v))
          }
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def streamKafkaClusterState(clusterId: Long) =
    WebSocket.acceptOrResult[String, String] { implicit request =>
      implicit val req = Request(request, AnyContentAsEmpty)
      handleSparkClusterWebsocketRequest(silhouette, memberService, userCache, request) { (orgId, workspaceId) =>
        Source
          .tick(2.seconds, 2.seconds, "tick")
          .mapAsync(1)(_ => clusterService.getKafkaCluster(clusterId, workspaceId))
          .map {
            case None        => Json.toJson(models.IllegalParam(request.path, 0, "Not found")).toString()
            case Some(value) => Json.toJson(value).toString()
          }

      }
    }

  /**
    * List all the brokers in the given kafka cluster
    * @param clusterId
    * @return
    */
  def listBrokers(clusterId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .flatMap(info =>
            info match {
              case None => Future(NotFound)
              case Some(v) =>
                v.processes.find(_.name.equals(KafkaProcesses.KAFKA_SERVER)) match {

                  case Some(p) if p.status == RunStatus.Running =>
                    withAdmin(p.host, p.port) { admin =>
                      val result = admin
                        .describeCluster()
                        .nodes()
                        .toCompletableFuture
                        .asScala
                        .map { nodes =>
                          Ok(Json.toJson(nodes.asScala.map(n => KafkaNode(n.idString(), n.host(), n.port(), n.rack()))))
                        }
                      result.onComplete(_ => admin.close())
                      result

                    }
                  case _ => Future(NotFound)
                }
          })
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * List the consumer group and the respective members
    * @param clusterId
    * @return
    */
  def listConsumerGroups(clusterId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        withKafkaCluster(clusterService, clusterId, profile.workspaceId) { admin =>
          val futureConsumerGroups = getConsumerGroups(admin)
          futureConsumerGroups.map(cg => Ok(Json.toJson(cg)))
        }.recoverWith {
            case e: Exception =>
              e.printStackTrace()
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def listTopicPartitions(clusterId: Long, topic: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .flatMap(info =>
            info match {
              case None => Future(NotFound)
              case Some(v) =>
                v.processes.find(_.name.equals(KafkaProcesses.KAFKA_SERVER)) match {

                  case Some(p) if p.status == RunStatus.Running =>
                    withAdmin(p.host, p.port) { admin =>
                      val result = getTopicPartitions(admin, topic).map(tps => Ok(Json.toJson(tps)))
                      result.onComplete(_ => admin.close())
                      result
                    }
                  case _ => Future(NotFound)
                }
          })
          .recoverWith {
            case e: Exception =>
              e.printStackTrace()
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  def listTopicMessages(clusterId: Long, topic: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .flatMap(info =>
            info match {
              case None => Future(NotFound)
              case Some(v) =>
                v.processes.find(_.name.equals(KafkaProcesses.KAFKA_SERVER)) match {

                  case Some(p) if p.status == RunStatus.Running =>
                    withAdmin(p.host, p.port) { admin =>
                      val result = getTopicMessages(admin, topic).map(messages => Ok(Json.toJson(messages)))
                      result.onComplete(_ => admin.close())
                      result
                    }
                  case _ => Future(NotFound)
                }
          })
          .recoverWith {
            case e: Exception =>
              e.printStackTrace()
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * List all the topics in the given kafka cluster
    * @param clusterId
    * @return
    */
  def listTopics(clusterId: Long) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .flatMap(info =>
            info match {
              case None => Future(NotFound)
              case Some(v) =>
                v.processes.find(_.name.equals(KafkaProcesses.KAFKA_SERVER)) match {

                  case Some(p) if p.status == RunStatus.Running =>
                    withAdmin(p.host, p.port) { admin =>
                      val result = getTopicSummary(admin, p.host, p.port).map(tds => Ok(Json.toJson(tds)))
                      result.onComplete(_ => admin.close())
                      result
                    }
                  case _ => Future(NotFound)
                }
          })
          .recoverWith {
            case e: Exception =>
              e.printStackTrace()
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * List all the topics in the given kafka cluster
    * @param clusterId
    * @return
    */
  def listTopicConfigurations(clusterId: Long, topic: String) = silhouette.UserAwareAction.async { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .flatMap(info =>
            info match {
              case None => Future(NotFound)
              case Some(v) =>
                v.processes.find(_.name.equals(KafkaProcesses.KAFKA_SERVER)) match {

                  case Some(p) if p.status == RunStatus.Running =>
                    withAdmin(p.host, p.port) { admin =>
                      val result = getTopicConfig(admin, topic).map(tpc => Ok(Json.toJson(tpc)))
                      result.onComplete(_ => admin.close())
                      result
                    }
                  case _ => Future(NotFound)
                }
          })
          .recoverWith {
            case e: Exception =>
              e.printStackTrace()
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

  /**
    * List all the topics in the given kafka cluster
    * @param clusterId
    * @return
    */
  def createTopic(clusterId: Long) = silhouette.UserAwareAction.async(validateJson[TopicConfig]) { implicit request =>
    handleMemberRequest(request, memberService) { (roles, profile) =>
      if (hasWorkspaceManagePermission(profile, roles, profile.orgId, profile.workspaceId)) {
        clusterService
          .getKafkaCluster(clusterId, profile.workspaceId)
          .flatMap(info =>
            info match {
              case None => Future(NotFound)
              case Some(v) =>
                v.processes.find(_.name.equals(KafkaProcesses.KAFKA_SERVER)) match {

                  case Some(p) if p.status == RunStatus.Running =>
                    withAdmin(p.host, p.port) { admin =>
                      val topic = new NewTopic(request.body.name, request.body.partitions, request.body.replicationFactor)
                      val result = admin
                        .createTopics(Seq(topic).asJava)
                        .topicId(request.body.name)
                        .toCompletableFuture
                        .asScala
                        .map(uuid => Created(Json.toJson(Map("uuid" -> uuid.toString))))
                      result.onComplete(_ => admin.close())
                      result
                    }
                  case _ => Future(NotFound)
                }
          })
          .recoverWith {
            case e: Exception =>
              Future.successful(InternalServerError(Json.toJson(InternalServerErrorResponse(request.path, e.getMessage))))
          }
      } else {
        Future.successful(Forbidden)
      }
    }
  }

}

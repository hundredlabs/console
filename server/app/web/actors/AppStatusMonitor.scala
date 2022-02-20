package web.actors

import akka.actor.{ActorRef, ActorSystem}
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordHasherRegistry
import javax.inject.{Inject, Named}
import web.actors.clusters.ClusterStatusChecker
import web.models.{BetaSignupRequest, CredentialInfo, Member}
import web.services.{MemberService, SecretStore}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class AppStatusMonitor @Inject()(actorSystem: ActorSystem,
                                 @Named("app-meta") appMetadataFetcher: ActorRef,
                                @Named("cluster-status-checker") clusterChecker: ActorRef)(implicit executionContext: ExecutionContext) {


  actorSystem.scheduler.scheduleAtFixedRate(initialDelay = 3.second,
                                 interval = 5.seconds,
                                 receiver = clusterChecker,
                                 message = ClusterStatusChecker.FetchClusterStatus)

}



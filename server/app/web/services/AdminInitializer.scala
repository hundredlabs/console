package web.services

import java.io.IOException

import akka.actor.{ActorRef, ActorSystem}
import javax.inject.{Inject, Named}
import web.actors.SystemInitializer


import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


  class AppInitializer @Inject()(actorSystem: ActorSystem,
                                   @Named("system-init") systemInit: ActorRef)(implicit executionContext: ExecutionContext) {

    actorSystem.scheduler.scheduleOnce(delay = 1.second,
      receiver = systemInit,
      message = SystemInitializer.InitSystem)


}

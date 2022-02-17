package web.utils

import akka.Done
import akka.actor.CoordinatedShutdown
import com.typesafe.scalalogging.LazyLogging
import controllers.AssetsFinder
import javax.inject.{Inject, Singleton}
import web.services.SecretStore
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.mailer.MailerClient

import scala.concurrent.{ExecutionContext, Future, blocking}

@Singleton
class ApplicationHooks @Inject()(cs: CoordinatedShutdown, mailerClient: MailerClient,
                                 applicationLifecycle: ApplicationLifecycle,
                                 secretStore: SecretStore,
                                 configuration: Configuration)(
                                  implicit
                                  ex: ExecutionContext
                                ) extends LazyLogging{



  cs.addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "notify-shutdown") { () =>
    //code to notify
    Future {
      blocking{
          logger.warn("Application shutting down.")
      }
      Done
    }
  }

  applicationLifecycle.addStopHook{ () =>
    Future {
      blocking {
        logger.info("Closing the GCP Secret clients")
        logger.error("Application shutdown in progress. Notification being sent")
      }
    }
  }

}

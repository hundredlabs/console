package web.actors

import java.time.{ZoneId, ZonedDateTime}

import akka.actor.{Actor, ActorLogging}
import com.gigahex.commons.models.RunStatus
import javax.inject.Inject
import web.services.{JobService, SparkEventService}
import play.api.Configuration

import scala.util.{Failure, Success}

class AppMetaFetcher @Inject()(eventService: SparkEventService, jobService: JobService, config: Configuration) extends Actor with ActorLogging {
  implicit val dispatcher = context.dispatcher

  override def preStart(): Unit = {
    log.info("Started app metadata fetcher")
  }

  override def receive: Receive = {

    case AppMetaFetcher.ExpireApps => eventService.listSparkAppsByStatus(RunStatus.Running).onComplete {
      case Failure(exception) =>
        exception.printStackTrace()
        log.error(s"Unable to fetch the apps - ${exception.getMessage}")
      case Success(value) => value.foreach{ current =>
       val now = ZonedDateTime.now(ZoneId.of(current.timezone)).toEpochSecond
        if((now - current.lastUpdated) > config.get[Int]("app.timeLimit")){
          println(s"Updating the status of the job - ${current.runId}")
          jobService.updateJobStatus(RunStatus.TimeLimitExceeded.toString, current.runId, current.timezone)
        }
      }
    }

  }

}

object AppMetaFetcher {

  case object ExpireApps
}

package web.actors
//
//import akka.actor.{ActorRef, FSM, LoggingFSM, Props}
//import com.gigahex.commons.events.{
//  ApplicationEnded,
//  ApplicationStarted,
//  JobEnded,
//  JobStarted,
//  SparkEvents,
//  StageCompleted,
//  StageSubmitted,
//  TaskCompleted,
//  TaskStarted
//}
//import web.models.spark.{
//  AppAttemptMetric,
//  AppCompleted,
//  AppIsRunning,
//  AppNotStarted,
//  AppWaitingForEvents,
//  ApplicationMetric,
//  JobMetric,
//  SparkMachineData,
//  SparkMachineState,
//  StageMetric,
//  Uninitialized
//}
//
//import scala.concurrent.duration._
//import web.services.SparkEventService
//import com.softwaremill.quicklens._
//
//class SparkMetricsMachine(jobId: Long, jobRunId: Long,  subscriptionManager: ActorRef, eventService: SparkEventService)
//    extends LoggingFSM[SparkMachineState, SparkMachineData]
//    with SparkEventHandler {
//
//  startWith(AppNotStarted, Uninitialized)
//
//  when(AppNotStarted) {
//    case Event(ApplicationStarted(appId, appAttemptId, appName, time, sparkUser), Uninitialized) =>
//      val appMetric = ApplicationMetric(appId, appName, time, "running", None, Map(appAttemptId -> AppAttemptMetric(time, None, Map())))
//      sender() ! true
//      goto(AppIsRunning).using(appMetric)
//  }
//
//  onTransition {
//    case AppNotStarted -> AppIsRunning =>
//      stateData match {
//        case Uninitialized                                         => log.info("Application started")
//        case ApplicationMetric(appId, appName, startTime, _, _, _) => log.info(s"Application - ${appName} started with appId - ${appId}")
//      }
//
//    case AppIsRunning -> AppCompleted =>
//      stateData match {
//        case Uninitialized                              => log.info("Application completed")
//        case ApplicationMetric(_, _, _, _, _, attempts) => log.info(s"Application completed with status - ${attempts}")
//        case _                                          => log.warning("Somehow the app completed...")
//
//      }
//
//    case AppWaitingForEvents -> AppCompleted =>
//      stateData match {
//        case Uninitialized                              => log.info("Application completed")
//        case ApplicationMetric(_, _, _, _, _, attempts) => log.info(s"Application completed with status - ${attempts}")
//        case _                                          => log.warning("Somehow the app completed...")
//
//      }
//  }
//
//  when(AppIsRunning) {
//    case Event(BatchEvents(evs), am: ApplicationMetric) =>
//      //log.info(s"received sorted events: ${evs}")
//      val updatedAm = evs.foldLeft(am){
//        case (aggAm, event) => handleSparkEvent(event, aggAm)
//      }
//      if(evs.last.isInstanceOf[ApplicationEnded]){
//        log.info(s"Final updated am : ${updatedAm}")
//        goto(AppCompleted).using(updatedAm)
//      } else {
//        stay().using(updatedAm)
//      }
//
//    case Event(event: SparkEvents, am: ApplicationMetric) =>
//      event match {
//
//        case e: ApplicationEnded =>
//          log.info("received appEnded")
//          goto(AppWaitingForEvents).using(handleAppEnded(e, am))
//
//        case e: JobStarted => stay().using(handleJobStarted(e, am))
//        case e: JobEnded =>
//          log.info("received job completed")
//          stay().using(handleJobEnded(e, am))
//        case e: StageSubmitted => stay().using(handleStageSubmitted(e, am))
//        case e: StageCompleted =>
//          log.info(s"received stage completed. stageId - ${e.stageId}, parentJobId - ${e.parentJobId}")
//          stay().using(handleStageCompleted(e, am))
//        case e: TaskStarted   => stay().using(handleTaskStarted(e,am))
//        case e: TaskCompleted => stay().using(handleTaskCompleted(e, am))
//      }
//
//  }
//
//  when(AppWaitingForEvents, stateTimeout = 2.seconds) {
//    case Event(event: SparkEvents, am: ApplicationMetric) =>
//      event match {
//
//        case e: JobEnded =>
//          log.info("received job completed - while waiting")
//          stay().using(handleJobEnded(e, am))
//        case e: StageCompleted =>
//          log.info("received stage completed - while waiting")
//          stay().using(handleStageCompleted(e, am))
//      }
//
//    case Event(StateTimeout, am: ApplicationMetric) =>
//      log.info("Timeout received")
//      goto(AppCompleted).using(am)
//
//  }
//  when(AppCompleted) {
//    case Event(e: Any, _) =>
//      log.warning(s"Any event will be ignored - ${e}")
//      stay()
//  }
//
//  initialize()
//
//  private def handleSparkEvent(event: SparkEvents, am: ApplicationMetric): ApplicationMetric = {
//    event match {
//      case e: ApplicationEnded => handleAppEnded(e, am)
//
//      case e: JobStarted => handleJobStarted(e, am)
//      case e: JobEnded => handleJobEnded(e, am)
//      case e: StageSubmitted => handleStageSubmitted(e, am)
//      case e: StageCompleted => handleStageCompleted(e, am)
//      case e: TaskStarted   => handleTaskStarted(e,am)
//      case e: TaskCompleted => handleTaskCompleted(e, am)
//    }
//  }
//}
//
//object SparkMetricsMachine {
//  def props(jobId: Long, jobRunId: Long,  subscriptionManager: ActorRef, eventService: SparkEventService): Props =
//    Props(new SparkMetricsMachine(jobId, jobRunId, subscriptionManager, eventService))
//}
//
//trait SparkEventHandler {
//
//  def handleAppEnded(event: ApplicationEnded, state: ApplicationMetric): ApplicationMetric = {
//    state.copy(endTime = Some(event.time), status = event.finalStatus)
//  }
//
//  def handleJobStarted(event: JobStarted, state: ApplicationMetric): ApplicationMetric = {
//    val jobMetric = JobMetric(event.time, None, "running", event.stageInfos.size, 0, 0, event.stageInfos.map { s =>
//      s.id -> StageMetric(s.id, "--", "--", "waiting", s.numTasks, 0, 0)
//    }.toMap)
//    val newAppMetric     = state.attempts.get(event.appAttemptId).map{ am =>
//      am.modify(_.jobs).using(js => js + (event.jobId -> jobMetric))
//    }
//
//    state.modify(_.attempts.at(event.appAttemptId)).setToIfDefined(newAppMetric)
//  }
//
//  def handleStageSubmitted(event: StageSubmitted, state: ApplicationMetric): ApplicationMetric = {
//    val newAM = state.attempts.get(event.appAttemptId).map { am =>
//      am.modify(_.jobs.at(event.parentJobId).inProgress)
//        .using(_ + 1)
//        .modify(_.jobs.at(event.parentJobId).stages.at(event.stageId).currentStatus)
//        .setTo(event.status)
//    }
//    state.modify(_.attempts.at(event.appAttemptId)).setToIfDefined(newAM)
//  }
//
//  def handleStageCompleted(event: StageCompleted, state: ApplicationMetric): ApplicationMetric = {
//    val newAM = state.attempts.get(event.appAttemptId).map { am =>
//      am.modify(_.jobs.at(event.parentJobId)).using(jm => jm.copy(inProgress = jm.inProgress - 1, completedStages = jm.completedStages + 1))
//        .modify(_.jobs.at(event.parentJobId).stages.at(event.stageId).currentStatus)
//        .setTo(event.status)
//    }
//    state.modify(_.attempts.at(event.appAttemptId)).setToIfDefined(newAM)
//  }
//
//  def handleJobEnded(event: JobEnded, state: ApplicationMetric): ApplicationMetric = {
//    val jobStatus = if (event.succeeded) "succeeded" else "failed"
//    val newAM = state.attempts.get(event.appAttemptId).map { am =>
//      am.modify(_.jobs.at(event.jobId).endTime).setTo(Some(event.time)).modify(_.jobs.at(event.jobId).currentStatus).setTo(jobStatus)
//    }
//    val ns = state.modify(_.attempts.at(event.appAttemptId)).setToIfDefined(newAM)
//    //println(s"job completed - pos update state AM - ${ns} ")
//    ns
//  }
//
//  def handleTaskStarted(event: TaskStarted, state: ApplicationMetric) = {
//    val newAm = state.attempts.get(event.appAttemptId).map { am =>
//    //println(s"task started - jobs - ${am.jobs} in stage -> ${event.stageId}")
//     val findJobId = am.jobs.find {
//       case (i, metric) => metric.stages.keySet.contains(event.stageId)
//     }.map(_._1).getOrElse(-1)
//      am.modify(_.jobs.at(findJobId).stages.at(event.stageId).inProgress).using(_ + 1)
//    }
//    state.modify(_.attempts.at(event.appAttemptId)).setToIfDefined(newAm)
//  }
//
//  def handleTaskCompleted(event: TaskCompleted , state: ApplicationMetric) = {
//    val newAm = state.attempts.get(event.appAttemptId).map { am =>
//      val findJobId = am.jobs.find {
//        case (i, metric) => metric.stages.keySet.contains(event.stageId)
//      }.map(_._1).getOrElse(-1)
//      am.modify(_.jobs.at(findJobId).stages.at(event.stageId).inProgress).using(_ - 1)
//        .modify(_.jobs.at(findJobId).stages.at(event.stageId).completedTasks).using(_ + 1)
//    }
//    state.modify(_.attempts.at(event.appAttemptId)).setToIfDefined(newAm)
//  }
//
//}

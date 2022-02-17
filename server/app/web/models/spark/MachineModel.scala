package web.models.spark

import com.gigahex.commons.events.SparkEvents

sealed trait SparkMachineState

case object AppNotStarted       extends SparkMachineState
case object AppIsRunning        extends SparkMachineState
case object AppWaitingForEvents extends SparkMachineState
case object AppCompleted        extends SparkMachineState

sealed trait SparkMachineData
case object Uninitialized extends SparkMachineData

case class ApplicationMetric(appId: String,
                             appName: String,
                             startTime: Long,
                             status: String,
                             endTime: Option[Long],
                             attempts: Map[String, AppAttemptMetric])
    extends SparkMachineData
case class AppAttemptMetric(time: Long, endTime: Option[Long], jobs: Map[Int, JobMetric])
case class JobMetric(startedTime: Long,
                     endTime: Option[Long],
                     currentStatus: String,
                     numStages: Int,
                     inProgress: Int,
                     completedStages: Int,
                     stages: Map[Int, StageMetric])
case class StageMetric(stageId: Int, startedAt: String, runtime: String, currentStatus: String, numTasks: Int, inProgress: Int, completedTasks: Int)

case class SparkLog(offset: Long, timestamp: Long, classname: String, thread: String, message: String, level: String, readableTime: Option[String] = None)
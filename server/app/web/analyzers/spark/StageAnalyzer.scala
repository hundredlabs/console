package web.analyzers.spark

import com.gigahex.commons.models.SeverityLevel
import web.models.spark.{MetricObservationResult, SparkStageCompleteStats}

import web.utils.DateUtil

object StageAnalyzer {

  private val gcToComputeRationLimit = 0.4
  private val slowToFastTaskRation = 0.2
  private val shuffleReadToComputeRation = 0.3
  private val tasksCountThreshold = 100
  private val smallTaskRuntime = 500
  private val stageToTaskTime = 0.5

  private def getStageId(stat: SparkStageCompleteStats): String = {
    if(stat.stageAttemptId != 0) s"${stat.stageId}(attempt = ${stat.stageAttemptId})" else stat.stageId.toString
  }

  def getErrorInStage(stats: Seq[SparkStageCompleteStats]): Seq[MetricObservationResult] = {
    stats.filter(p => p.failedTasks > 0).map{s =>
      MetricObservationResult(s"Stage ${s.stageId} failed due to failure of ${s.failedTasks} tasks",
        s"""```java
          |${s.failedReason.getOrElse("Check the Spark driver or executor logs for more details.")}
          |```""".stripMargin, 100)

    }
  }

  /**
    * Observe the GC time spent
    * @param stats
    * @return
    */
  def longGCTime(stats: Seq[SparkStageCompleteStats]): MetricObservationResult = {
    val highGCStages = stats.foldLeft(0) {
      case (count, stat) =>
        val gcToComputeRatio = stat.summary.aggMetrics.gcTime.total / stat.summary.timeline.executorComputeTime.toDouble
        if (gcToComputeRatio >= gcToComputeRationLimit) {
          count + 1
        } else count
    }

    if (highGCStages > 0) {
      val title = "Significant GC Time"
      val messages = stats
        .filter(stat => stat.summary.aggMetrics.gcTime.total / stat.summary.timeline.executorComputeTime.toDouble >= gcToComputeRationLimit)
        .map { stat =>
          s"| ${stat.parentJobId}| ${getStageId(stat)} | ${stat.name} | ${DateUtil.formatIntervalMillis(stat.summary.aggMetrics.gcTime.mean.toLong)} | ${DateUtil
            .formatIntervalMillisToDecimal(stat.summary.aggMetrics.gcTime.total)} | ${DateUtil.formatIntervalMillisToDecimal(stat.summary.timeline.executorComputeTime)}"
        }
      val description = """| JobID     | Stage ID   | Name  | GC Mean time | GC Total time | Executor Compute Time |
                           || ---------|:-----:| ---------------:|-------------:|--------------:|----------------------:|
                           |""".stripMargin + messages.mkString("\n")
      MetricObservationResult(title, description, highGCStages)
    } else {
      MetricObservationResult("Low GC time", "There was no significant time spent on JVM Garbage collection, as compared to the executor compute time", highGCStages)
    }

  }

  def findSlowTasks(stats: Seq[SparkStageCompleteStats]): MetricObservationResult = {
    val slowStages = stats.foldLeft(0){
      case (count, stat) =>
      if(stat.numTasksCompleted >= 10 && stat.summary.aggMetrics.taskDuration.mean/stat.summary.aggMetrics.taskDuration.max <= slowToFastTaskRation){
        count + 1
      } else {
        count
      }
    }

    if(slowStages > 0){
      val title = "Straggler tasks found"
     val messages =  stats.filter(stat => stat.numTasksCompleted >= 10 && stat.summary.aggMetrics.taskDuration.mean/stat.summary.aggMetrics.taskDuration.max <= slowToFastTaskRation)
        .map{ stat =>
          s"| ${stat.parentJobId}| ${getStageId(stat)} | ${stat.name} | ${DateUtil.formatIntervalMillisToDecimal(stat.summary.aggMetrics.taskDuration.mean.toLong)} | ${DateUtil
            .formatIntervalMillisToDecimal(stat.summary.aggMetrics.taskDuration.max)} | ${DateUtil.formatIntervalMillisToDecimal(stat.runtime)}"
        }
      val description = """| JobID     | Stage ID   | Name   | Mean task time | Slowest task runtime | Stage runtime |
                           || ---------|:-----:| ---------------:|-------------:|--------------:|----------------------:|
                           |""".stripMargin + messages.mkString("\n")
      MetricObservationResult(title, description, slowStages)
    } else {
      MetricObservationResult("No straggler tasks found", "There was no measurable data skew discovered. Data Skew can slow down the performance of the entire application.", slowStages)
    }
  }

  def longShuffleReadTime(stats: Seq[SparkStageCompleteStats]): MetricObservationResult = {
    val longShuffleStages = stats.foldLeft(0){
      case (count, stat) =>

        if(stat.summary.timeline.shuffleReadTime/stat.summary.timeline.executorComputeTime.toDouble > shuffleReadToComputeRation){
          count + 1
        } else {
          count
        }
    }

    if(longShuffleStages > 0){
      val title = "Long Shuffle read time"
      val messages =  stats.filter(stat => stat.summary.timeline.shuffleReadTime/stat.summary.timeline.executorComputeTime.toDouble > shuffleReadToComputeRation)
        .map{ stat =>
          s"| ${stat.parentJobId}| ${getStageId(stat)} | ${stat.name} | ${DateUtil.formatIntervalMillisToDecimal(stat.summary.timeline.shuffleReadTime)} | ${DateUtil
            .formatIntervalMillisToDecimal(stat.summary.timeline.executorComputeTime)} | ${DateUtil.formatIntervalMillisToDecimal(stat.runtime)}"
        }
      val description = """| JobID     | Stage ID   | Name  | Shuffle Read time | Executor compute time | Stage runtime |
                           || ---------|:-----:| ---------------:|-------------:|--------------:|----------------------:|
                           |""".stripMargin + messages.mkString("\n")
      MetricObservationResult(title, description, longShuffleStages)
    } else {
      MetricObservationResult("No long shuffle read time", "Long spark shuffle read time could occur due to GC pauses in remote executor", longShuffleStages)
    }
  }

  def tooManySmallTasks(stats: Seq[SparkStageCompleteStats]): MetricObservationResult = {
    val condition : SparkStageCompleteStats => Boolean = {
      (s: SparkStageCompleteStats) => s.numTasksCompleted > tasksCountThreshold && s.summary.aggMetrics.taskDuration.mean < smallTaskRuntime
    }
    val stagesWithSmallTasks = stats.foldLeft(0){
      case (count, stat) =>
        if(condition(stat)){
          count + 1
        } else {
          count
        }
    }

    if(stagesWithSmallTasks > 0){
      val title = "Too many small partitions"
      val messages =  stats.filter(condition)
        .map { stat =>
          s"| ${stat.parentJobId}| ${getStageId(stat)} | ${stat.name} | ${DateUtil.formatIntervalMillisToDecimal(stat.summary.aggMetrics.taskDuration.mean.toLong)} | ${DateUtil
            .formatIntervalMillisToDecimal(stat.summary.timeline.taskDeserializationTime)} | ${DateUtil
            .formatIntervalMillisToDecimal(stat.summary.timeline.executorComputeTime)} | ${stat.numTasksCompleted}"
        }
      val description = """| JobID     | Stage ID  |Name  | Mean Task duration | Task Deserialization time | Executor compute time | Total tasks |
                           || ---------|:-----:| ---------------:|-------------:|--------------:|--------------:|----------------------:|
                           |""".stripMargin + messages.mkString("\n")
      MetricObservationResult(title, description, stagesWithSmallTasks)
    } else {
      MetricObservationResult("Too many small tasks not found",
        """Too many small tasks decreases the overall performance of application,
          | due to time spent in task deserialization and scheduling.""".stripMargin, stagesWithSmallTasks)
    }
  }



}

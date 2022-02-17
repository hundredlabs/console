package web.actors.clusters.spark

object SparkProcesses {
  val MASTER = "Master"
  val WORKER = "Worker"
  val SHS    = "History_Server"

  def getMasterCmd(binPath: String, uiPort: Int): String = {
    s"""$binPath/spark-class org.apache.spark.deploy.master.Master --host 0.0.0.0
       |--port 7077
       |--webui-port $uiPort""".stripMargin.replaceAll("\n", " ")

  }

  def getWorkerCmd(binPath: String, uiPort: Int): String = s"""${binPath}/spark-class org.apache.spark.deploy.worker.Worker
                                                             |--webui-port $uiPort
                                                             |spark://0.0.0.0:7077""".stripMargin.replaceAll("\n", " ")

  def getHistoryCmd(binPath: String, configPath: String): String =
    s"""$binPath/spark-class org.apache.spark.deploy.history.HistoryServer --properties-file $configPath"""

}

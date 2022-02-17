package utils
import web.models.cluster.{SparkMasterSummary, WorkerView}

import scala.jdk.CollectionConverters._
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object HtmlParser {

  def parseMasterHTML(html: String, sparkVersion: String = "3.0.3"): SparkMasterSummary = {
    val doc         = Jsoup.parse(html)
    var sparkUrl    = ""
    var cores       = ""
    var memory      = ""
    var liveWorkers = ""
    var status      = ""
    val parentSelector = sparkVersion match {
      case "3.0.3" => "ul.unstyled > li"
      case "3.2.0" => "ul.list-unstyled > li"
      case _       => "ul.unstyled > li"
    }
    doc
      .select(parentSelector)
      .forEach(liElem => {
        val titleNode = liElem.childNode(0).outerHtml()
        if (titleNode.contains("URL")) {
          sparkUrl = liElem.childNode(1).outerHtml().trim

        } else if (titleNode.contains("Cores")) {
          cores = liElem.childNode(1).outerHtml().trim
        } else if (titleNode.contains("Memory")) {
          memory = liElem.childNode(1).outerHtml().trim

        } else if (titleNode.contains("Workers")) {
          liveWorkers = liElem.childNode(1).outerHtml().trim
        } else if (titleNode.contains("Status")) {
          status = liElem.childNode(1).outerHtml().trim
        }
      })

    val sms = SparkMasterSummary(
      url = sparkUrl,
      aliveWorkers = liveWorkers.toInt,
      coresUsed = cores.split(",")(1).replace("Used", "").trim.toInt,
      coresAvailable = cores.split(",")(0).replace("Total", "").trim.toInt,
      memoryUsed = memory.split(",")(1).replace("Used", "").trim,
      memoryAvailable = memory.split(",")(0).replace("Total", "").trim,
      status = status,
      workers = Seq()
    )

    val workers = new collection.mutable.ArrayBuffer[WorkerView]

    doc.select("div.aggregated-workers > table > tbody > tr").forEach { row =>
      var index        = 0
      var wid          = ""
      var addr         = ""
      var workerStatus = ""
      var workerCores  = ""
      var workerMemory = ""
      row
        .childNodes()
        .asScala
        .filter(child => child.isInstanceOf[Element] && child.childNodeSize() > 0)
        .foreach(n => {
          if (n.childNodeSize() > 1 && index == 0) {
            wid = n.childNode(1).childNode(0).outerHtml().trim

          } else if (index == 1) {
            addr = n.childNode(0).outerHtml().trim
          } else if (index == 2) {
            workerStatus = n.childNode(0).outerHtml().trim
          } else if (index == 3) {
            workerCores = n.childNode(0).outerHtml().trim
          } else if (index == 4) {
            workerMemory = n.childNode(0).outerHtml().trim
          }
          index = index + 1
        })
      val coresMax  = workerCores.substring(0, workerCores.indexOf('(')).trim.toInt
      val coresUsed = workerCores.substring(workerCores.indexOf('(') + 1, workerCores.indexOf("Used")).trim.toInt
      val memMax    = workerMemory.substring(0, workerMemory.indexOf('(')).trim
      val memUsed   = workerMemory.substring(workerMemory.indexOf('(') + 1, workerMemory.indexOf("Used")).trim
      workers.addOne(
        WorkerView(workerId = wid,
                   address = addr,
                   status = workerStatus,
                   coresUsed = coresUsed,
                   coresAvailable = coresMax,
                   memUsed,
                   memMax))
    }
    sms.copy(workers = workers.toSeq)
  }

}

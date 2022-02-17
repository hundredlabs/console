package web.utils

import java.time.ZoneOffset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import web.models.spark.SparkLog

object LogParser {

  private[web] def parse(line: String): Option[SparkLog] = {
    val splits = line.split("\\|\\|")
    if(splits.length == 6){
      val logOffset = splits(0).toLong
      val time = splits(1)
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
      val offset = time.split(" ")(2)
      val parsableTime = splits(1).substring(0, splits(1).lastIndexOf(' '))
      val dateTime = LocalDateTime.parse(parsableTime, formatter)
      val timestamp = dateTime.atOffset(ZoneOffset.of(offset)).toInstant.toEpochMilli
      val level = splits(2)
      val thread = splits(3)
      val classname = splits(4)
      val message = splits(5)
      Some(SparkLog(logOffset,timestamp, classname, thread, message, level))
    } else None
  }

}

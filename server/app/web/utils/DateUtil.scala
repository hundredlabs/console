package web.utils

import java.lang.management.OperatingSystemMXBean
import java.text.StringCharacterIterator
import java.time.{Instant, ZoneId, ZonedDateTime}

import com.gigahex.commons.models.RunStatus.{Value, values}
import web.utils.TimeUnit.TimeUnit

object TimeUnit extends Enumeration {
  type TimeUnit = Value

  val SEC = Value("secs")
  val MIN  = Value("min")
  val HOUR  = Value("hr")


  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(SEC)
}

object DateUtil {

  def now = ZonedDateTime.now()

  def getTime(epochMillis: Long): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

  def formatInterval(interval: Long): String = interval match {
    case x if x < 60                       => s"${x} secs"
    case x if x >= 60 && x < 3600          => s"${x / 60} mins"
    case x if x >= 3600 && x < 86400       => s"${x / 3600} hrs"
    case x if x >= 86400 && x < 2592000    => s"${x / 86400} days"
    case x if x >= 2592000 && x < 31536000 => s"${x / 2592000} mon"
    case x if x > 31536000                 => s"${x / 31536000} yrs"
  }



  def formatIntervalMillis(interval: Long): String = interval match {
    case x if x < 1000                             => s"${x} ms"
    case x if x >= 1000 && x < 60000               => s"${BigDecimal(x / 1000d).setScale(0, BigDecimal.RoundingMode.HALF_UP)} s"
    case x if x >= 60000 && x < 3600000            => s"${BigDecimal(x / 60000d).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} m"
    case x if x >= 3600000 && x < 86400000         => s"${BigDecimal(x / 3600000d).setScale(1, BigDecimal.RoundingMode.HALF_DOWN)} h"
    case x if x >= 86400000 && x < 2592000000L     => s"${BigDecimal(x / 86400000d).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} d"
    case x if x >= 2592000000L && x < 31536000000L => s"${BigDecimal(x / 2592000000L).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} mo"
    case x if x >= 31536000000L                    => s"${BigDecimal(x / 31536000000L).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} y"
  }

  def formatIntervalMillisToDecimal(interval: Long): String = interval match {
    case x if x < 1000                             => s"${x} ms"
    case x if x >= 1000 && x < 60000               => s"${BigDecimal(x / 1000d).setScale(1, BigDecimal.RoundingMode.HALF_UP)} s"
    case x if x >= 60000 && x < 3600000            => s"${BigDecimal(x / 60000d).setScale(1, BigDecimal.RoundingMode.HALF_DOWN)} m"
    case x if x >= 3600000 && x < 86400000         => s"${BigDecimal(x / 3600000d).setScale(1, BigDecimal.RoundingMode.HALF_DOWN)} h"
    case x if x >= 86400000 && x < 2592000000L     => s"${BigDecimal(x / 86400000d).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} d"
    case x if x >= 2592000000L && x < 31536000000L => s"${BigDecimal(x / 2592000000L).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} mo"
    case x if x >= 31536000000L                    => s"${BigDecimal(x / 31536000000L).setScale(0, BigDecimal.RoundingMode.HALF_DOWN)} y"
  }


  def formatIntervalMillisInDays(interval: Long): String = interval match {
    case x if x < 1000                             => s"${x} ms"
    case x if x >= 1000 && x < 60000               => s"${BigDecimal(x / 1000d).setScale(1, BigDecimal.RoundingMode.HALF_UP)} s"
    case x if x >= 60000 && x < 3600000            => s"${BigDecimal(x / 60000d).setScale(1, BigDecimal.RoundingMode.HALF_UP)} m"
    case x if x >= 3600000 && x < 86400000         => s"${BigDecimal(x / 3600000d).setScale(1, BigDecimal.RoundingMode.HALF_UP)} h"
    case x if x >= 86400000      => s"${BigDecimal(x / 86400000d).setScale(1, BigDecimal.RoundingMode.HALF_UP)} d"

  }

  def timeElapsed(started: ZonedDateTime, endTime: Option[ZonedDateTime]): String = (started, endTime) match {
    case (started, None)        => formatIntervalMillis(ZonedDateTime.now().toInstant.toEpochMilli - started.toInstant.toEpochMilli)
    case (startTs, Some(endTs)) => formatIntervalMillis(endTs.toInstant.toEpochMilli - startTs.toInstant.toEpochMilli)
  }

  def timeElapsedInFraction(started: ZonedDateTime, endTime: Option[ZonedDateTime]): String = (started, endTime) match {
    case (started, None)        => formatIntervalMillisToDecimal(ZonedDateTime.now().toInstant.toEpochMilli - started.toInstant.toEpochMilli)
    case (startTs, Some(endTs)) => formatIntervalMillisToDecimal(endTs.toInstant.toEpochMilli - startTs.toInstant.toEpochMilli)
  }
}

object MetricConverter {

  def getRam: Long =  {
    val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean().asInstanceOf[com.sun.management.OperatingSystemMXBean]
    bean.getTotalPhysicalMemorySize
  }

  def toReadableSize(bytes: Long): String = {
    val absB =
      if (bytes == Long.MinValue) Long.MaxValue
      else Math.abs(bytes)
    if (absB < 1024) return bytes + " B"
    var value = absB
    val ci    = new StringCharacterIterator("KMGTPE")
    var i     = 40
    while ({
      i >= 0 && absB > (0xfffccccccccccccL >> i)
    }) {
      value >>= 10
      ci.next
      i -= 10
    }
    value *= java.lang.Long.signum(bytes)
    val readable = "%.1f %cB"
    readable.format(value / 1024.0, ci.current)
  }
}

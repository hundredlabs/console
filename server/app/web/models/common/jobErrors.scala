package web.models.common

import com.gigahex.commons.models.{ErrorSource, ErrorType, JVMErrorStack, JVMException, SeverityLevel, TraceElement}
import play.api.libs.json.Json

trait JobErrorJson {

  implicit val errSourceFmt     = Json.formatEnum(ErrorSource)
  implicit val sevLevelFmt      = Json.formatEnum(SeverityLevel)
  implicit val traceElementFmt  = Json.format[TraceElement]
  implicit val appError         = Json.format[JVMException]
  implicit val errorType        = Json.format[ErrorType]
  implicit val JVMErrorStackFmt = Json.format[JVMErrorStack]
}

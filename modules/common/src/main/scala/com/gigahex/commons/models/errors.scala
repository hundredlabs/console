package com.gigahex.commons.models

import com.gigahex.commons.models.ErrorSource.ErrorSource
import com.gigahex.commons.models.SeverityLevel.SeverityLevel

sealed trait ErrorType {
  val level: SeverityLevel
}

case class TraceElement(className: String, methodName: String, lineNum: Int, fileName: String)
case class JVMException(message: String, cause: String, level: SeverityLevel, traces: Seq[TraceElement]) extends ErrorType
case class JVMErrorStack(message: String, level: SeverityLevel, stackTrace: Seq[String])
object SeverityLevel extends Enumeration {
  type SeverityLevel = Value

  val INFO  = Value("info")
  val WARN  = Value("warn")
  val ERROR = Value("error")
  val FATAL = Value("fatal")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(INFO)
}

object ErrorSource extends Enumeration {
  type ErrorSource = Value

  val SOURCE_CODE       = Value("code")
  val APP_RUNTIME       = Value("runtime")
  val APP_CONFIGURATION = Value("configuration")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(SOURCE_CODE)
}

case class RunError(cause: String, severityLevel: SeverityLevel, errorSource: ErrorSource, error: ErrorType)

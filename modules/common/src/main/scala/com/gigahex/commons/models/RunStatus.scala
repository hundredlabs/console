package com.gigahex.commons.models


object RunStatus extends Enumeration {
  type RunStatus = Value

  val Starting = Value("starting")
  val Running  = Value("running")
  val Skipped  = Value("skipped")
  val Stopped    = Value("stopped")
  val NotRunning    = Value("not running")
  val Completed    = Value("completed")
  val Started    = Value("started")
  val NotStarted    = Value("not started")
  val Failed    = Value("failed")
  val Succeeded = Value("succeeded")
  val Waiting = Value("waiting")
  val TimeLimitExceeded = Value("exceeded")
  val Unknown = Value("unknown")
  val Logging = Value("push logs")

  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(Unknown)
}

object JobType extends Enumeration {
  type JobType = Value

  val spark = Value("spark")
  val flink = Value("flink")
  val pipeline = Value("pipeline")
  val default = Value("default")
  def withNameOpt(s: String): Value = values.find(_.toString == s).getOrElse(default)
}


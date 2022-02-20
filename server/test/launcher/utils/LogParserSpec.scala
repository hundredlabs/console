package web.utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers


class LogParserSpec extends AnyFlatSpec with Matchers{

  behavior of "Log parser"
  it should "parse a line" in {
    val line = """2020-04-01 02:09:20.131 +0530||INFO ||task-result-getter-0||org.apache.spark.scheduler.TaskSetManager||Finished task 0.0 in stage 3.0 (TID 4) in 115 ms on 192.168.1.3 (executor 1) (1/2)"""
  }

}

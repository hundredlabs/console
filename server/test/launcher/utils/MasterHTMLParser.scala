package web.utils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import utils.HtmlParser

class MasterHTMLParser extends AnyFlatSpec with Matchers{

  behavior of "Master HTML Parser"
  it should "parse html for spark 3.0.3" in {
    val html = scala.io.Source.fromResource("master.html").getLines().mkString("\n")
    HtmlParser.parseMasterHTML(html)
  }

  it should "parse html for spark 3.2.0" in {
    val html = scala.io.Source.fromResource("master-spark-v3.2.0.html").getLines().mkString("\n")
    HtmlParser.parseMasterHTML(html, "3.2.0")
  }

}

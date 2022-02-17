package com.gigahex.commons.constants

object AppSettings {

  def getBinDir(root: String): String = s"$root/gigahex/bin"
  val sparkCDN = "https://packages.gigahex.com/spark"
  val kakfaCDN = "https://packages.gigahex.com/kafka"
  val hadoopCDN = "https://packages.gigahex.com/hadoop"

}

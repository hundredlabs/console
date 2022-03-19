package com.gigahex.services

sealed trait ServiceConnection {
  val name : String
}

case class AWSS3Connection(name: String, awsKey: String, awsSecretKey: String, bucket: String, region: String) extends ServiceConnection
case class PgConnection(name: String, database: String, username: String, password: String, hostname: String, port: Int = 5432) extends ServiceConnection
case class MySQLConnection(name: String, database: String, username: String, password: String, hostname: String, port: Int = 3306) extends ServiceConnection
case class MariaDBConnection(name: String, database: String, username: String, password: String, hostname: String, port: Int = 3306) extends ServiceConnection
package com.gigahex.cassandra

import com.gigahex.services.{CassandraConnection, MySQLConnection, PgConnection}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

class TestCassandraConnection extends AnyFlatSpec with Matchers {

  val conn = CassandraConnection(
    name = "test",
    datacenter = "datacenter1",
    contactPoints = Seq("127.0.0.1:9042"),
    username = "",
    password = ""
  )

  it should "get database info" in {
    val meta = CassandraService.getDBInfo(conn)
    val schemas = CassandraService.getCatalogs(conn)
    val tables = CassandraService.listTables("sales", conn)
    val qResult = CassandraService.executeQuery("""CREATE TABLE store.shopping_cart3 (
                                                  |userid text PRIMARY KEY,
                                                  |item_count int,
                                                  |last_update_timestamp timestamp
                                                  |);""".stripMargin, conn)
    assert(meta.isSuccess)
  }



}

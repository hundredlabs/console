package com.gigahex.postgres

import com.gigahex.services.{MySQLConnection, PgConnection}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

class TestPgConnection extends AnyFlatSpec with Matchers {

  val conn = PgConnection(
    name = "test",
    database = "localgiga",
    username = "giga-admin",
    hostname = "127.0.0.1",
    password = "pgpass",
    port = 8032
  )

  it should "get database info" in {
    val meta = PostgresDBService.getDBInfo(conn)
    val schemas = PostgresDBService.getSchemas(conn)
    val catalogs = PostgresDBService.getCatalogs(conn)
    val tables = PostgresDBService.listTables("public", conn)
    val qs = PostgresDBService.executeQuery("select * from agents", conn)

    assert(meta.isSuccess)
    assert(schemas.isSuccess)
    assert(catalogs.isSuccess)
  }

  it should "connect to cockroachDB" in {
    val cockroachDBConn = PgConnection(
      name = "test-cockroach",
      database = "defaultdb",
      username = "shad",
      hostname = "free-tier12.aws-ap-south-1.cockroachlabs.cloud",
      password = "EJyoAR-gUCh0JHTZRhSBUg",
      port = 26257
    )
    val meta = PostgresDBService.getDBInfo(cockroachDBConn)
    val schemas = PostgresDBService.getSchemas(cockroachDBConn)
    val catalogs = PostgresDBService.getCatalogs(cockroachDBConn)
    val tables = PostgresDBService.listTables("defaultdb", conn)


    val qs = PostgresDBService.executeQuery("select * from agents", conn)

    assert(meta.isSuccess)
    assert(schemas.isSuccess)
    assert(catalogs.isSuccess)
  }



}

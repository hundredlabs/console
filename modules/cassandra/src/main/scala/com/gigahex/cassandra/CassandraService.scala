package com.gigahex.cassandra

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

import com.datastax.oss.driver.api.core.{CqlIdentifier, CqlSession}
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.cql.{ResultSet, Row}
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint
import com.gigahex.services.{CassandraConnection, DatabaseServer, DatabaseServiceProvider}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import scala.util.Try

object CassandraService extends DatabaseServiceProvider[CassandraConnection] {

  private val connections: ConcurrentHashMap[String, CqlSession] = new ConcurrentHashMap[String, CqlSession]()

  private def usingSession[T](config: CassandraConnection)(handler: CqlSession => T): Try[T] = {
    val connectionId = config.contactPoints.mkString(",")
    val session = connections.computeIfAbsent(
      connectionId,
      (_) => {
        val endpoints = config.contactPoints.map(x => new InetSocketAddress(x.split(":")(0), x.split(":")(1).toInt))
        var sessionBuilder = CqlSession
          .builder()
          .addContactPoints(endpoints.asJavaCollection)
          .withLocalDatacenter(config.datacenter)

        sessionBuilder =
          if (config.username.trim.isBlank) sessionBuilder
          else {
            sessionBuilder.withAuthCredentials(config.username, config.password)

          }
        sessionBuilder.build()

      }
    )

    Try(handler(session))
  }



  override def getDBInfo(config: CassandraConnection): Try[DatabaseServer] = {
    usingSession(config) { session =>
      val ps        = session.prepare("select release_version from system.local where key = 'local'")
      val rs        = session.execute(ps.bind())
      val dbVersion = rs.one().getString("release_version")
      val result    = dbVersion.split("\\.")
      DatabaseServer(result(0).toInt, result(1).toInt, "Cassandra", result(2).toInt)
    }
  }

  /**
    * No schema concept defined for Cassandra. Return same as keyspaces
    * @param config
    * @return
    */
  override def getSchemas(config: CassandraConnection): Try[List[String]] = usingSession(config) { session =>
    session.getMetadata.getKeyspaces.asScala.map {
      case (_, metadata) => metadata.getName.toString
    }.toList
  }

  /**
    * List all the keyspaces(user defined) for the cassandra
    * @param config
    * @return
    */
  override def getCatalogs(config: CassandraConnection): Try[List[String]] = usingSession(config) { session =>
    session.getMetadata.getKeyspaces.asScala.map {
      case (_, metadata) => metadata.getName.toString
    }.toList
  }

  override def listTables(schema: String, config: CassandraConnection): Try[List[String]] = usingSession(config) { session =>
    session.getMetadata.getKeyspace(schema).get().getTables().keySet().asScala.map(_.toString).toList

  }

  private def buildMap(row: Row, columns: mutable.HashSet[CqlIdentifier]): Map[String, Object] = {
    columns.map {
      id => (id.toString -> row.getObject(id))
    }.toMap

  }

  override def executeQuery(q: String, config: CassandraConnection): Try[Vector[Map[String, Object]]] = usingSession(config) { session =>
    val rs        = session.execute(q)
    val colDefs = rs.getColumnDefinitions
    val columnNames = mutable.HashSet.empty[CqlIdentifier]
    for(i <- 0 to colDefs.size() - 1){
      columnNames.add(colDefs.get(i).getName)
    }
    rs.asScala.map(row => buildMap(row, columnNames)).toVector
  }

  override def executeUpdate(q: String, config: CassandraConnection): Try[Int] = executeQuery(q, config).map(_.size)

}

package com.gigahex.postgres

import java.sql.Connection

import com.gigahex.services.{DatabaseServer, DatabaseServiceProvider, MySQLConnection}
import scalikejdbc._
import java.sql.ResultSet

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Try

object MySQLDatabaseService extends DatabaseServiceProvider[MySQLConnection] {

  private val connectionIds = new mutable.HashSet[String]()

  private def usingConfig[T](config: MySQLConnection)(handler: Connection => T): Try[T] = {
    val id = s"${config.hostname}:${config.port}"
    if(!connectionIds.contains(id)) {
      Class.forName("com.mysql.cj.jdbc.Driver")
      ConnectionPool.add(id, s"jdbc:mysql://${config.hostname}:${config.port}/${config.database}", config.username, config.password)
    }

    using(ConnectionPool.borrow(id)) { c =>
      Try(handler(c))
    }
  }

  override def getDBInfo(config: MySQLConnection): Try[DatabaseServer] = {
    usingConfig(config){ conn =>
      val dbMetadata = conn.getMetaData
      DatabaseServer(majorVersion = dbMetadata.getDatabaseMajorVersion,
        minorVersion = dbMetadata.getDatabaseMinorVersion,
        productName = dbMetadata.getDatabaseProductName)
    }
  }

  override def getSchemas(config: MySQLConnection): Try[List[String]] = {
    usingConfig(config){ conn =>
      val rs = conn.getMetaData.getSchemas
      val ls = new ListBuffer[String]
      while(rs.next()){
        ls.addOne(rs.getString("TABLE_SCHEM"))
      }
      ls.toList
    }
  }

  override def getCatalogs(config: MySQLConnection): Try[List[String]] = {
    usingConfig(config){ conn =>
      val rs = conn.getMetaData.getCatalogs
      val ls = new ListBuffer[String]
      while(rs.next()){
        ls.addOne(rs.getString("TABLE_CAT"))
      }
      ls.toList
    }
  }

  override def listTables(schema: String, config: MySQLConnection): Try[List[String]] = {
    usingConfig(config){ conn =>
      val rs = conn.getMetaData.getTables(schema, "", null, Array("TABLE"))
      val ls = new ListBuffer[String]
      while(rs.next()){
        ls.addOne(rs.getString("TABLE_NAME"))
      }
      ls.toList
    }
  }

  private def buildMap(queryResult: ResultSet, colNames: Seq[String]): Option[Map[String, Object]] =
    if (queryResult.next())
      Some(colNames.map(n => n -> queryResult.getObject(n)).toMap)
    else
      None

  override def executeQuery(q: String, config: MySQLConnection): Try[Vector[Map[String, Object]]] = {
    usingConfig(config){ conn =>
      val rs = conn.prepareStatement(q).executeQuery()
      val md = rs.getMetaData
      val colNames = (1 to md.getColumnCount) map md.getColumnName
      Iterator.continually(buildMap(rs, colNames)).takeWhile(_.isDefined).map(_.get).toVector
    }
  }

  override def executeUpdate(q: String, config: MySQLConnection): Try[Int] = {
    usingConfig(config){ conn =>
      conn.prepareStatement(q).executeUpdate()
    }
  }

}

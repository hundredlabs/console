package com.gigahex.services

import java.io.File

import com.gigahex.services.fs.{FileListing, FileListingResult}

import scala.util.Try

trait DataServiceProvider[T <: ServiceConnection] {

  def isValidConfig(config: T): Try[Boolean]

  def uploadFile(config: T, path: String, file: File): Try[String]

  def deleteFile(config: T, path: String): Try[Boolean]

  def listFileItems(config: T, path: Option[String], marker: Option[String]): Try[FileListingResult]

  def createDirectory(config: T, path: String): Try[Boolean]

}

trait DatabaseServiceProvider[T <: ServiceConnection] {

  def getDBInfo(config: T): Try[DatabaseServer]

  def getSchemas(config: T): Try[List[String]]

  def getCatalogs(config: T): Try[List[String]]

  def listTables(schema: String, config: T): Try[List[String]]

  def executeQuery(q: String, config: T): Try[Vector[Map[String, Object]]]

  def executeUpdate(q: String, config: T): Try[Int]

}

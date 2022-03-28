package web.models.formats

import com.gigahex.services.fs.{FailedFileListing, FileListing, FileListingResult}
import com.gigahex.services.{
  AWSS3Connection,
  CassandraConnection,
  CockroachDBConnection,
  DatabaseServer,
  MariaDBConnection,
  MySQLConnection,
  PgConnection,
  RequestQueryExecution,
  ServiceConnection
}
import play.api.libs.json.Json

trait ConnectionFormats {

  implicit val s3ConnectionFmt        = Json.format[AWSS3Connection]
  implicit val pgConnectionFmt        = Json.format[PgConnection]
  implicit val mysqlConnectionFmt     = Json.format[MySQLConnection]
  implicit val mariadbConnectionFmt   = Json.format[MariaDBConnection]
  implicit val cockroachConnFmt       = Json.format[CockroachDBConnection]
  implicit val cassandraConnectionFmt = Json.format[CassandraConnection]
  implicit val connectionServiceFmt   = Json.format[ServiceConnection]
  implicit val fileListingFmt         = Json.format[FileListing]
  implicit val fileListingResultFmt   = Json.format[FileListingResult]
  implicit val failedFileListingFmt   = Json.format[FailedFileListing]

}

trait DBFormats {
  implicit val dbServer = Json.format[DatabaseServer]
  implicit val qReq     = Json.format[RequestQueryExecution]
}

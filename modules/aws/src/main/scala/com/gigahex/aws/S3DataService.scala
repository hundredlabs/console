package com.gigahex.aws

import java.io.File

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.gigahex.services.fs.{FileListing, FileListingResult}
import com.gigahex.services.{AWSS3Connection, DataServiceProvider, ServiceConnection}
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import scala.jdk.CollectionConverters._
import scala.util.Try

object S3DataService extends DataServiceProvider[AWSS3Connection] {

  def usingS3Client[T](config: AWSS3Connection)(clientFn: AmazonS3 => T): Try[T] = {
    val credentials = new BasicAWSCredentials(config.awsKey, config.awsSecretKey)
    val s3 = AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withRegion(config.region)
      .build()
    Try(clientFn(s3))

  }

  override def isValidConfig(config: AWSS3Connection): Try[Boolean] = {
    usingS3Client(config) { s3 =>
      s3.doesBucketExistV2(config.bucket)
    }
  }

  /**
    * Deletes the file using the given path
    * @param config
    * @param path
    * @return
    */
  override def deleteFile(config: AWSS3Connection, path: String): Try[Boolean] = {
    usingS3Client(config) { s3 =>
      s3.deleteObject(config.bucket, path)
      !s3.doesObjectExist(config.bucket, path)
    }
  }

  /**
    * Using s3 client, it uploads the file and returns the version
    * @param config
    * @param path
    * @param file
    * @return
    */
  override def uploadFile(config: AWSS3Connection, path: String, file: File): Try[String] = {
    usingS3Client(config) { s3 =>
      s3.putObject(config.bucket, path, file).getVersionId
    }
  }

  override def listFileItems(config: AWSS3Connection, path: Option[String], marker: Option[String]): Try[FileListingResult] = {
    usingS3Client(config) { s3 =>
      val request = path match {
        case None        => new ListObjectsV2Request().withBucketName(config.bucket).withDelimiter("/")
        case Some(value) => new ListObjectsV2Request().withBucketName(config.bucket).withPrefix(value)
      }

      val result = if (marker.isDefined) s3.listObjectsV2(request.withContinuationToken(marker.get)) else s3.listObjectsV2(request)
      val directories = result.getCommonPrefixes.asScala.toList.map(s => FileListing(s, true, Some(0), 0, None))
        val files = result.getObjectSummaries.asScala.toList
        .map(s => FileListing(name = s.getKey, s.getKey.endsWith("/"), Some(s.getSize), s.getLastModified.toInstant.getEpochSecond, Option(s.getOwner).map(_.getDisplayName) ))
      val nextMarker = if (result.getNextContinuationToken != null) Some(result.getNextContinuationToken) else None
      FileListingResult(hasMore = result.isTruncated, files ++ directories, nextMarker)
    }
  }

  override def createDirectory(config: AWSS3Connection, path: String): Try[Boolean] = usingS3Client(config) { s3 =>

    val metadata = new ObjectMetadata
    metadata.setContentLength(0L)
    val inputStream = new ByteArrayInputStream(new Array[Byte](0))
    val putObjectRequest = new PutObjectRequest(config.bucket, path, inputStream, metadata)
    s3.putObject(putObjectRequest)
    s3.doesObjectExist(config.bucket, path)
  }
}

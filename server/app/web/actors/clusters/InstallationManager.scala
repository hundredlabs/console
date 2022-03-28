package web.actors.clusters

import java.io.{File, FileInputStream, FileOutputStream, FileWriter, IOException}
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.io.Source

class InstallationManager(downloadUrl: String, downloadDir: String, installPath: String, ws: WSClient) {

  def download(onProgress: String => Unit, extract: Boolean = true)(implicit ec: ExecutionContext): Future[String] = Future {
    blocking {
      val conn                = new URL(downloadUrl).openConnection()
      val inputStream         = conn.getInputStream()
      val readableByteChannel = Channels.newChannel(inputStream)
      try {
        val rootDirectory = new File(downloadDir)
        if (!rootDirectory.exists()) {
          Files.createDirectories(Paths.get(downloadDir))
        }
        val maxLength = conn.getContentLengthLong
        val filePath  = s"${downloadDir}/${System.currentTimeMillis()}.tar.gz"

        val rcbc = ReadableConsumerByteChannel(readableByteChannel, downloadedSize => {
          onProgress(s"$downloadedSize/$maxLength")
        })

        val fileOutputStream = new FileOutputStream(filePath)
        val fileChannel      = fileOutputStream.getChannel
        fileChannel.transferFrom(rcbc, 0, Long.MaxValue)
        if(extract){
          extractTar(filePath, installPath)
          new File(filePath).delete()
        }
        filePath
      } catch {
        case e: Exception => throw e
      } finally {
        readableByteChannel.close()
        inputStream.close()
      }
    }
  }

  def modifyConfig(relativePath: String)(handler: String => String): Unit = {
    val path = s"${installPath}${relativePath}"
    val bufferedSource = Source.fromFile(path)
    val newLines = bufferedSource.getLines().map(handler).toSeq
    val fw = new FileWriter(path)
    newLines.foreach { l =>
      fw.write(l + "\n")
    }
    fw.flush()
    fw.close()
    bufferedSource.close
  }

  def setFilesExecutable(dirPath: String, endsWith: String): Unit = {
    val dir = new File(dirPath)
    dir.listFiles().filter(_.getAbsolutePath.endsWith(endsWith)).foreach { f =>
      f.setExecutable(true, true)
    }
  }


  def extractTar(source: String, targetDir: String): String = {
    val gzipIn = new GzipCompressorInputStream(new FileInputStream(source))
    val tarIn  = new TarArchiveInputStream(gzipIn)
    try {

      var entry = tarIn.getNextEntry
      while (entry != null) {
        val name = s"${targetDir}/${entry.getName}"
        val f    = new File(name)

        if (entry.isDirectory) {
          if (!f.isDirectory && !f.mkdirs) throw new IOException("failed to create directory " + f)
        } else {
          val parent = f.getParentFile();
          if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("failed to create directory " + parent);
          }
          val o = Files.newOutputStream(f.toPath())
          IOUtils.copy(tarIn, o)
          o.flush()
          o.close()
        }
        entry = tarIn.getNextEntry
      }
      targetDir
    } catch {
      case e: Exception => {
        throw e
      }
    } finally {
      gzipIn.close()
      tarIn.close()
    }
  }

}

case class ReadableConsumerByteChannel(rbc: ReadableByteChannel, onRead: Long => Unit) extends ReadableByteChannel {

  var downloadedSize = 0L
  override def read(dst: ByteBuffer): Int = {
    val bytesRead = rbc.read(dst)
    downloadedSize = downloadedSize + bytesRead
    onRead(downloadedSize)
    bytesRead
  }

  override def isOpen: Boolean = rbc.isOpen

  override def close(): Unit = rbc.close()
}
package edu.uci.ics.texera.web.resource.dashboard.file

import edu.uci.ics.amber.engine.common.amberexception.WorkflowRuntimeException
import edu.uci.ics.amber.error.WorkflowRuntimeError
import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.FileDao
import edu.uci.ics.texera.workflow.common.Utils
import org.jooq.types.UInteger

import java.io._
import java.nio.file.{Files, Path}

object UserFileUtils {
  private val FILE_CONTAINER_PATH: Path = {
    Utils.amberHomePath.resolve("user-resources").resolve("files")
  }

  private val fileDao = new FileDao(SqlServer.createDSLContext.configuration)
  def storeFile(fileStream: InputStream, fileName: String, userID: String): Unit = {
    createFileDirectoryIfNotExist(UserFileUtils.getFileDirectory(userID))
    checkFileDuplicate(UserFileUtils.getFilePath(userID, fileName))
    writeToFile(UserFileUtils.getFilePath(userID, fileName), fileStream)
  }

  def getFilePath(userID: String, fileName: String): Path = {
    getFileDirectory(userID).resolve(fileName)
  }

  def getFileDirectory(userID: String): Path = FILE_CONTAINER_PATH.resolve(userID)

  @throws[FileIOException]
  private def createFileDirectoryIfNotExist(directoryPath: Path): Unit = {
    if (!Files.exists(directoryPath))
      try Files.createDirectories(directoryPath)
      catch {
        case e: IOException =>
          throw FileIOException(e.getMessage)
      }
  }

  @throws[FileIOException]
  private def checkFileDuplicate(filePath: Path): Unit = {
    if (Files.exists(filePath)) throw FileIOException("File already exists.")
  }

  @throws[FileIOException]
  private def writeToFile(filePath: Path, fileStream: InputStream): Unit = {
    val charArray = new Array[Char](1024)
    val reader = new BufferedReader(new InputStreamReader(fileStream))
    val writer = new BufferedWriter(new FileWriter(filePath.toString))
    var bytesRead = 0
    try while ({
      bytesRead = reader.read(charArray)
      bytesRead
    } != -1) writer.write(charArray, 0, bytesRead)
    catch {
      case e: IOException =>
        throw FileIOException("Error occurred while writing file on disk: " + e.getMessage)
    } finally {
      if (reader != null) reader.close()
      if (writer != null) writer.close()
    }
  }

  def getFilePathByInfo(owner: String, filename: String, username: String): Option[Path] = {
    val fid = FileAccessUtils.getFileId(owner, filename)
    val uid = FileAccessUtils.getUidOfUser(username)
    if (FileAccessUtils.hasAccessTo(uid, fid)) {
      val path = fileDao.fetchByFid(fid).get(0).getPath
      Some(Path.of(path))
    } else {
      None
    }
  }

  def getFilePathByIds(uid: UInteger, fid: UInteger): Option[Path] = {
    if (FileAccessUtils.hasAccessTo(uid, fid)) {
      val path = fileDao.fetchByFid(fid).get(0).getPath
      Some(Path.of(path))
    } else {
      None
    }
  }

  @throws[FileIOException]
  def deleteFile(filePath: Path): Unit = {
    try Files.deleteIfExists(filePath)
    catch {
      case e: Exception =>
        throw FileIOException(
          "Error occur when deleting the file " + filePath.toString + ": " + e.getMessage
        )
    }
  }

  case class FileIOException(message: String)
      extends WorkflowRuntimeException(
        WorkflowRuntimeError(
          message,
          Thread.currentThread().getStackTrace.mkString("\n"),
          Map.empty
        )
      )
      with Serializable
}

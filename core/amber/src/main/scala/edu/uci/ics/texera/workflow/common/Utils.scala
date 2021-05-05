package edu.uci.ics.texera.workflow.common

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import java.nio.file.{Files, Path, Paths}
import java.text.SimpleDateFormat
import scala.annotation.tailrec

object Utils {

  final val objectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .setSerializationInclusion(Include.NON_NULL)
    .setSerializationInclusion(Include.NON_ABSENT)
    .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

  /**
    * Gets the real path of the amber home directory by:
    * 1): check if the current directory is texera/core/amber
    * if it's not then:
    * 2): search the siblings and children to find the texera home path
    *
    * @return the real absolute path to amber home directory
    */
  lazy val amberHomePath: Path = {
    val currentWorkingDirectory = Paths.get(".").toRealPath()
    // check if the current directory is the amber home path
    if (isAmberHomePath(currentWorkingDirectory)) {
      currentWorkingDirectory
    } else {
      // from current path's parent directory, search its children to find amber home path
      // current max depth is set to 2 (current path's siblings and direct children)
      val searchChildren = Files
        .walk(currentWorkingDirectory.getParent, 2)
        .filter((path: Path) => isAmberHomePath(path))
        .findAny
      if (searchChildren.isPresent) {
        searchChildren.get
      }
      throw new RuntimeException(
        "Finding texera home path failed. Current working directory is " + currentWorkingDirectory
      )
    }
  }
  val AMBER_HOME_FOLDER_NAME = "amber";

  /**
    * Retry the given logic with a backoff time interval. The attempts are executed sequentially, thus blocking the thread.
    * Backoff time is doubled after each attempt.
    * @param attempts total number of attempts. if n <= 1 then it will not retry at all, decreased by 1 for each recursion.
    * @param baseBackoffTimeInMS time to wait before next attempt, started with the base time, and doubled after each attempt.
    * @param fn the target function to execute.
    * @tparam T any return type from the provided function fn.
    * @return the provided function fn's return, or any exception that still being raised after n attempts.
    */
  @tailrec
  def retry[T](attempts: Int, baseBackoffTimeInMS: Long)(fn: => T): T = {
    try {
      fn
    } catch {
      case e: Throwable =>
        if (attempts > 1) {
          // TODO: change the following to logger
          e.printStackTrace()
          println(
            "retrying after " + baseBackoffTimeInMS + "ms, number of attempts left: " + (attempts - 1)
          )
          Thread.sleep(baseBackoffTimeInMS)
          retry(attempts - 1, baseBackoffTimeInMS * 2)(fn)
        } else throw e
    }
  }

  private def isAmberHomePath(path: Path): Boolean = {
    path.toRealPath().endsWith(AMBER_HOME_FOLDER_NAME)
  }
}

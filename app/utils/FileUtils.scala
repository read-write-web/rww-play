package utils

import java.nio.file.Path
import scala.language.reflectiveCalls

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object FileUtils {

  val FilenamePattern = """(.*)[.](.*)""".r

  /**
   * Gets the extension for a file path (it should work with absolute and relative paths)
   * @param filePath
   * @return
   */
  def getFileExtension(filePath: Path): Option[String] = for {
    filenamPath <- Option(filePath.getFileName)
    extension <- getFileExtension(filenamPath.toString)
  } yield extension


  /**
   * Gets the extension for a filename
   * @param filename
   * @return
   */
  def getFileExtension(filename: String): Option[String] = filename match {
    case FilenamePattern(name,extension) => Some(extension)
    case _ => None
  }

  /**
   * Used for reading/writing to database, files, etc.
   * Code From the book "Beginning Scala"
   * http://www.amazon.com/Beginning-Scala-David-Pollak/dp/1430219890
   */
  def using[A <: {def close(): Unit}, B](param: A)(f: A => B): B =
    try { f(param) } finally { param.close() }

}

package rww.ldp

import org.w3.banana.BananaException

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object LDPExceptions {

  case class ParentDoesNotExist(message: String) extends Exception(message) with BananaException
  case class ResourceDoesNotExist(message: String) extends Exception(message) with BananaException
  case class RequestNotAcceptable(message: String) extends Exception(message) with BananaException
  case class AccessDenied(message: String) extends Exception(message) with BananaException
  case class PreconditionFailed(message: String) extends Exception(message) with BananaException
  case class UnsupportedMediaType(message: String) extends Exception(message) with BananaException
  case class StorageError(message: String)  extends Exception(message) with BananaException
  case class UnparsableSource(message: String,cause: Throwable)  extends Exception(message,cause) with BananaException

}

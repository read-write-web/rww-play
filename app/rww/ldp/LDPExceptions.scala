package rww.ldp

import org.w3.banana.BananaException
import rww.play.AuthorizedModes

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
  case class InformationNotFound(message: String) extends Exception(message) with BananaException
  case class ServerException(message: String) extends Exception(message) with BananaException
  case class AccessDeniedAuthModes(authinfo: AuthorizedModes) extends Exception("No access to resource") with BananaException
  case class ETagsDoNotMatch(message: String) extends Exception(message) with BananaException
  case class MissingEtag(message: String) extends Exception(message) with BananaException
  case class PropertiesConflict(message: String) extends Exception(message) with BananaException
}

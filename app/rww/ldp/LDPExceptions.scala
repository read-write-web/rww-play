package rww.ldp

import org.w3.banana.{RDF, PointedGraph, BananaException}
import rww.auth.SigInfo
import rww.play.AuthorizedModes

import scala.util.control.ControlThrowable

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
  case class ETagsMatch(message: String) extends Exception(message) with BananaException
  case class MissingEtag(message: String) extends Exception(message) with BananaException
  case class PropertiesConflict(message: String) extends Exception(message) with BananaException
  case class OperationNotSupportedException(message: String) extends Exception(message) with BananaException

  sealed trait HttpAuthException extends ControlThrowable
  trait SignatureAuthException extends HttpAuthException
  case class SignatureRequestException(msg: String) extends HttpAuthException
  //todo: the exception here should be one returned by rww.execute
  case class FetchException(sigInfo: SigInfo, e: Throwable) extends SignatureAuthException
  case class SignatureVerificationException(msg: String, sigInfo: SigInfo) extends SignatureAuthException
  case class KeyIdException[Rdf<:RDF](msg: String, sigInfo: SigInfo, pg: PointedGraph[Rdf]) extends SignatureAuthException
  case class KeyFetchException(msg: String, sigInfo: SigInfo) extends SignatureAuthException
}

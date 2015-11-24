package rww.ldp

import java.net.URI

import org.w3.banana.{BananaException, PointedGraph, RDF}
import rww.auth.SigInfo
import rww.ldp.auth.Method
import rww.play.AuthorizedModes
import rww.play.auth.Subject

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
  case class ETagsDoNotMatch(message: String) extends Exception(message) with BananaException
  case class ETagsMatch(message: String) extends Exception(message) with BananaException
  case class MissingEtag(message: String) extends Exception(message) with BananaException
  case class PropertiesConflict(message: String) extends Exception(message) with BananaException
  case class OperationNotSupportedException(message: String) extends Exception(message) with BananaException


  sealed trait AuthZException extends Exception
  case class MissingACLException(resource: URI) extends AuthZException
  case class NoAuthorization(subject: Subject, on: URI, mode: Method.Value) extends AuthZException

  // Authentiction Exceptions
  sealed trait AuthNException extends Exception
  case class ClientAuthNDisabled(msg: String, e: Option[Throwable]=None) extends AuthNException
  case class OtherAuthNException(e: Throwable) extends AuthNException

  case class TLSAuthNException(cause: Throwable) extends AuthNException

  trait HttpAuthNException extends AuthNException
  case class SignatureRequestException(msg: String) extends HttpAuthNException

  trait SignatureAuthNException extends HttpAuthNException
  //todo: the exception here should be one returned by rww.execute
  case class FetchException(sigInfo: SigInfo, e: Throwable) extends SignatureAuthNException
  case class SignatureVerificationException(msg: String, sigInfo: SigInfo) extends SignatureAuthNException
  case class KeyIdException[Rdf<:RDF](msg: String, sigInfo: SigInfo, pg: PointedGraph[Rdf]) extends SignatureAuthNException
}

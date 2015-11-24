package rww.ldp.auth

import java.io.UnsupportedEncodingException
import java.security.interfaces.RSAPublicKey
import java.security.{NoSuchAlgorithmException, Principal, Signature}

import org.w3.banana._
import org.w3.banana.binder.RecordBinder
import rww.auth.SigInfo
import rww.ldp.CertBinder
import rww.ldp.LDPCommand._
import rww.ldp.LDPExceptions.{ServerException, FetchException, KeyIdException}
import rww.ldp.actor.RWWActorSystem

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/**
 * as specified by
 * https://tools.ietf.org/html/draft-cavage-http-signatures-05
 * but requiring that the key be an http(s) URL and be dereferenceable
 */
case class WebKeyVerifier[Rdf <: RDF](
  rww: RWWActorSystem[Rdf]
)(implicit
  ops: RDFOps[Rdf],
  val recordBinder: RecordBinder[Rdf],
  val ec: ExecutionContext
) {

  import ops._
  import org.w3.banana.diesel._

  val cert       = CertPrefix[Rdf]
  val certbinder: CertBinder[Rdf] = new CertBinder[Rdf]()

  import certbinder._

  /**
   *
   * @param siginfo the valid SigInfo collected in the request
   * @return a Principal or a SignatureAuthException exception in the Try
   */
  def verify(siginfo: SigInfo): Future[Principal] = {
    val keyid = URI(siginfo.keyId.toString)
    fetchKeyPGraph(keyid)
      .recoverWith({ case NonFatal(e) => Future.failed(FetchException(siginfo, e)) })
      .flatMap { pg =>
        toKey(pg, siginfo).flatMap { key =>
          verifySig(siginfo, key)
        }.asFuture
      }

  }

  /**
   * This function is private as the pubkey has to correspond to the keyid
   * @param siginfo
   * @param pubkey  the pubkey corresponding to the keyid in siginfo
   * @return a Success of WebKeyPrincipal if it is verified, otherwise a
   *         SignatureVerificationException in the Try
   */
  private
  def verifySig(siginfo: SigInfo, pubkey: RSAPublicKey): Try[WebKeyPrincipal] = {
    import SigInfo._
    import siginfo._
    try {
      val sig = Signature.getInstance(algorithm)
      sig.initVerify(pubkey)
      sig.update(siginfo.sigText.getBytes("US-ASCII")) //should be ascii only
      if (sig.verify(sigbytes)) Success(WebKeyPrincipal(keyId.toURI))
      else SigVFail("could not cryptographically verify signature", siginfo)
    } catch {
      case nsa: NoSuchAlgorithmException => SigVFail("could not find implementation for " +
        algorithm, siginfo)
      case ue: UnsupportedEncodingException => Failure(ServerException("could not find US-ASCII Encoding!"))
      case iae: IllegalArgumentException => SigVFail("could not decode base64 signature", siginfo)
    }

  }

  def fetchKeyPGraph(keyId: Rdf#URI): Future[PointedGraph[Rdf]] =
    rww.execute(getLDPR(keyId.fragmentLess)).map(PointedGraph(keyId, _))

  //todo: This should be more general and return any available type of key
  /**
   *
   * @param keypg
   * @param s SigInfo, in order to pass the info in the KeyIdException
   * @return an RSAPublicKey or a KeyIdException in the failure of the Try
   */
  def toKey(keypg: PointedGraph[Rdf], s: SigInfo): Try[RSAPublicKey] =
    keypg.as[RSAPublicKey] match {
      case Success(key) => Success(key)
      case Failure(e) => Failure(KeyIdException[Rdf](e.getMessage,s,keypg))
    }


}

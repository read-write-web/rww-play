package rww.auth

import java.net.{MalformedURLException, URL}
import java.security.{PrivateKey, Signature}
import java.util.Base64

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.w3.banana.RDF
import play.api.mvc.RequestHeader
import rww.ldp.LDPExceptions.{ClientAuthNDisabled, SignatureRequestException, SignatureVerificationException}
import rww.ldp.auth.WebKeyVerifier
import rww.play.auth.{AuthN, Subject}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


object SigInfo {
  def SigFail(errorMsg: String) = Failure(SignatureRequestException(errorMsg))

  def SigVFail(errorMsg: String, sigInfo: SigInfo) =
    Failure(SignatureVerificationException(errorMsg, sigInfo))

  val base64decoder = Base64.getDecoder

  val base64encoder = Base64.getEncoder

  /**
    *
    * @param text to sign
    * @param privkey the private key to sign the text
    * @param sigType  eg SHA256withRSA
    * @return a Try of the Signed Bytes (which often then need to be hex encoded)
    *         The Try can capture
    *         - java.security.NoSuchAlgorithmException
    *         - java.security.InvalidKeyException
    *         - java.security.SignatureException
    */
  def sign(text: String, privkey: PrivateKey, sigType: String): Try[Array[Byte]] = {
    Try {
      val sig = Signature.getInstance(sigType)
      sig.initSign(privkey)
      sig.update(text.getBytes("US-ASCII"))
      sig.sign()
    }
  }

}



case class SigInfo (
  val sigText: String,
  val keyId: URL,
  val algorithm: String,
  val sigbytes: Array[Byte] //how can I force this to be immutable, without duplication?
) {
  def sig = scala.collection.immutable.IndexedSeq(sigbytes)
}


/**
  * Even though we verify the Authorization header, this is only about Authenticating
  * the user.
  *
  * Authorization also requires verification that the subject is allowed access.
  *
  * Implements a number of Http Authorization methods
  * currently only WebKeyVerification as defined by
  * https://tools.ietf.org/html/draft-cavage-http-signatures-05
  * @param verifier
  * @param base
  * @tparam Rdf
  */
class HttpAuthentication[Rdf <: RDF](
  verifier: WebKeyVerifier[Rdf], base: URL
)(implicit
  ec: ExecutionContext
) extends AuthN with LazyLogging {

  import AuthN._
  import org.w3.banana.TryW

  def apply(req: RequestHeader): Future[Subject] = {
    val auths = for (auth <- req.headers.getAll("Authorization"))
      yield HttpHeader.parse("Authorization", auth)

    val seqOfFuturePrincipals = auths.collect {
      case Ok(Authorization(GenericHttpCredentials("Signature", _, params)), _) =>
        parseSignatureInfo(req, params).asFuture.flatMap(verifier.verify(_))
    }
    if (seqOfFuturePrincipals.isEmpty) {
      Future.failed(ClientAuthNDisabled("No 'Authorization: Signature ...' http header"))
    }
    else toSubject(seqOfFuturePrincipals)
  }



  /**
   *
   * @param req The Request
   * @param params from the parsed Authorization header
   * @return
   * //todo: use the akka Uri class
   */
  def parseSignatureInfo(req: RequestHeader, params: Map[String, String]): Try[SigInfo] = {
    import SigInfo._
    for {
      keyUrl <- params.get("keyId")
          .fold[Try[URL]](SigFail("no keyId attribute")){ id=>
            Try ( new URL(base,id) ) recoverWith {
              case e: MalformedURLException => SigFail("could not transform keyId to URL")
            }
          }
      algo <- params.get("algorithm")
        .fold[Try[String]](SigFail("algorithm was not specified")){
          //java standard names http://docs.oracle
          // .com/javase/8/docs/technotes/guides/security/StandardNames.html
          case "rsa-sha256" => Success("SHA256withRSA")  //sadly java does not provide a typed
          // non mutable Signature object
          case algo => SigFail(s"algorithm '$algo' not known")
        }
      signature <- params.get("signature")
        .fold[Try[Array[Byte]]](SigFail("no signature was sent!")){ sig =>
           Try(base64decoder.decode(sig)).recoverWith {
             case e: IllegalArgumentException => SigFail("signature is not a base64 encoding")
           }
        }
      sigText <- buildSignatureText(req, params.get("headers").getOrElse("Date"))
    } yield SigInfo(sigText, keyUrl, algo, signature)
  }


  def buildSignatureText(req: RequestHeader, headerList: String): Try[String] = try {
    Success(headerList.split(" ").map {
      case rt@"(request-target)" =>
        rt + ": " + req.method.toLowerCase + " " + req.path + {
          if (req.rawQueryString != "")
            "?" + req.rawQueryString
          else ""
        }
      case name =>
        val values = req.headers.getAll(name)
        if (values.isEmpty)
          throw SignatureRequestException(s"found no header for $name in ${req.headers}")
        else name.toLowerCase + ": " + values.mkString(",")
    }.mkString("\n")
    )
  } catch {
    //for discussion on this type of control flow see:
    //   http://stackoverflow.com/questions/2742719/how-do-i-break-out-of-a-loop-in-scala
    //   http://stackoverflow.com/questions/12892701/abort-early-in-a-fold
    //   http://www.tzavellas.com/techblog/2010/09/20/catching-throwable-in-scala/
    case e: SignatureVerificationException => Failure(e)
  }


}

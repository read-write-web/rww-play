package rww.auth

import java.security.Principal
import java.security.cert.{Certificate, X509Certificate}

import com.typesafe.scalalogging.slf4j.Logging
import org.w3.banana.RDF
import play.api.mvc.RequestHeader
import rww.auth.BrowserCertBehavior._
import rww.ldp.auth.{Claim, WebIDVerifier}
import rww.play.auth.{AuthN, AuthenticationError, Subject}

import scala.concurrent.{ExecutionContext, Future}

/**
 * WebID Authentication
 * @param verifier verifier for an x509 claim
 * @tparam Rdf Type of RDF library
 */
class WebIDAuthN[Rdf <: RDF](
  verifier: WebIDVerifier[Rdf]
)(implicit
  ec: ExecutionContext
) extends AuthN with Logging {


  def apply(headers: RequestHeader): Future[Subject] = {
    val certificateRequired: Boolean = browserDoesNotSupportsTLSWantMode(headers)
    logger.debug(s"Certificate required (TLS Need mode)? $certificateRequired")
    headers.certs(certificateRequired) flatMap { certs: Seq[Certificate] =>
      logger.debug(s"Certificates found=${certs.size}")
      val principals: List[Future[Principal]] =
        certs.headOption.map { cert =>
          cert match {
            case x509: X509Certificate => {
              val x509claim = Claim.ClaimMonad.point(x509)
              verifier.verify(x509claim)
            }
            case other => List()
          }
        }.getOrElse(List())

      //todo: is there a way to avoid loosing the granularity of the previous futures?
      //this I think forces all of the WebIDs to be verified before the future is ready, where I
      // may prefer
      //to get going as soon as I find the first...
      val futurePrincipals: Future[List[Principal]] = Future.sequence(principals)
      futurePrincipals.map(principals => Subject(principals))
        .transform(identity, AuthenticationError(_))
    }
  }


}

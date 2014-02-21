package rww.play.auth

import play.api.mvc.RequestHeader
import concurrent.Future
import org.w3.banana.RDF
import java.security.cert.{X509Certificate, Certificate}
import java.security.Principal
import net.sf.uadetector.service.UADetectorServiceFactory
import rww.ldp.auth.{WebIDPrincipal, Claim, WebIDVerifier}
import net.sf.uadetector.{UserAgentType, UserAgentFamily}
import com.typesafe.scalalogging.slf4j.Logging
import rww.auth.BrowserCertBehavior


/**
 * The subject of this authentication.
 * Generally the subject is a single principal which is a WebID.
 *
 * But for Henry there may be other corner cases where the principals could contain 2 WebID (multiple webIds can be linked to a certificate)
 * or some cases where the principals are not WebIDPrincipal instances
 *
 * @param principals
 */
case class Subject(principals: List[Principal]) {
  lazy val webIds = principals.flatMap{ p =>
    p match {
      case wp: WebIDPrincipal => Some(wp.webid)
      case _ => None
    }
  }
}

/**
 * Authentication function
 */
trait AuthN extends (RequestHeader => Future[Subject])

case class AuthenticationError(cause: Throwable) extends Exception(cause)


/**
 * WebID Authentication
 * @param verifier verifier for an x509 claim
 * @tparam Rdf Type of RDF library
 */
class WebIDAuthN[Rdf <: RDF](verifier: WebIDVerifier[Rdf]) extends AuthN with Logging {
  import verifier.ec


  def apply(headers: RequestHeader): Future[Subject] = {
    val certificateRequired: Boolean = BrowserCertBehavior.browserDoesNotSupportsTLSWantMode(headers)
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
      //this I think forces all of the WebIDs to be verified before the future is ready, where I may prefer
      //to get going as soon as I find the first...
      val futurePrincipals: Future[List[Principal]]  = Future.sequence(principals)
      futurePrincipals.map(principals => Subject(principals) ).transform(identity,AuthenticationError(_))
    }
  }


}



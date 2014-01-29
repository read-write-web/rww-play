package rww.play.auth

import play.api.mvc.RequestHeader
import concurrent.Future
import org.w3.banana.RDF
import java.security.cert.{X509Certificate, Certificate}
import java.security.Principal
import net.sf.uadetector.service.UADetectorServiceFactory
import rww.ldp.auth.{WebIDPrincipal, Claim, WebIDVerifier}
import net.sf.uadetector.UserAgentFamily
import com.typesafe.scalalogging.slf4j.Logging


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
      //this I think forces all of the WebIDs to be verified before the future is ready, where I may prefer
      //to get going as soon as I find the first...
      val futurePrincipals: Future[List[Principal]]  = Future.sequence(principals)
      futurePrincipals.map(principals => Subject(principals) ).transform(identity,AuthenticationError(_))
    }
  }

   lazy val agentParser =  UADetectorServiceFactory.getResourceModuleParser

  /**
   *  Some agents do not send client certificates unless required by a NEED.
   *
   *  This is a problem for them, as it ends up breaking the SSL connection for agents that do not send a cert.
   *  For human agents this is a bigger problem, as one would rather send them a message of explanation for what
   *  went wrong, with perhaps pointers to other methods  of authentication, rather than showing an ugly connection
   *  broken/disallowed window ) For Opera and AppleWebKit browsers (Safari) authentication requests should
   *  therefore be done over javascript, on resources that NEED a cert, and which can fail cleanly with a
   *  javascript exception.  )
   *
   *  It would be useful if this could be updated by server from time to  time from a file on the internet,
   *  so that changes to browsers could update server behavior.
   *
   *  Note the library we use is based on information from http://user-agent-string.info/parse
   *
   * bertails: could be an implicit class + value class
   */
  def browserDoesNotSupportsTLSWantMode(req: RequestHeader): Boolean =  {
    req.headers.get("User-Agent").map { userAgentString =>
      val userAgent = agentParser.parse(userAgentString)
      FamiliesNotSupportingTLSWantMode.contains(userAgent.getFamily) || ajaxRequest(req)
    }.getOrElse(false)
  }


  private val FamiliesNotSupportingTLSWantMode = Set(
    UserAgentFamily.CURL,
    UserAgentFamily.JAVA,
    UserAgentFamily.SAFARI,
    UserAgentFamily.OPERA,
    // see https://github.com/stample/rww-play/issues/74, it seems to work half the time on Chrome with Want mode :(
    UserAgentFamily.CHROME
  )

  private def ajaxRequest(req: RequestHeader): Boolean = req.headers.get("X-Requested-With").map(_.trim.equalsIgnoreCase("XMLHttpRequest")).getOrElse(false)

}



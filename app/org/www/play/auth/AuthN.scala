package org.www.play.auth

import play.api.mvc.RequestHeader
import concurrent.Future
import org.www.readwriteweb.play.auth.Subject
import org.w3.banana.{BananaException, RDF}
import java.security.cert.{X509Certificate, Certificate}
import scalaz.Validation
import java.security.Principal


/**
 * Authentication function
 */
trait AuthN extends (RequestHeader => Future[Subject])

/**
 * WebID Authentication
 * @param verifier verifier for an x509 claim
 * @tparam Rdf Type of RDF library
 */
class WebIDAuthN[Rdf <: RDF](implicit verifier: WebIDVerifier[Rdf]) extends AuthN {
  import verifier.ec


  def apply(headers: RequestHeader): Future[Subject] = {
    headers.certs(must(headers)).flatMap{ certs: Seq[Certificate]=>
      val principals: List[Future[Validation[BananaException, Principal]]] =
        certs.headOption.map { cert =>
          cert match {
            case x509: X509Certificate => {
              val x509claim = Claim.ClaimMonad.point(x509)
              verifier.verify(x509claim).map { bf => bf.inner }
            }
            case other => List()
          }
        }.getOrElse(List())

      //todo: is there a way to avoid loosing the granularity of the previous futures?
      //this I think forces all of the WebIDs to be verified before the future is ready, where I may prefer
      //to get going as soon as I find the first...
      val futurePrincipals: Future[List[Validation[BananaException, Principal]]]  = Future.sequence(principals)
      futurePrincipals.map { principals => Subject(principals) }
    }
  }

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
   * bertails: could be an implicit class + value class
   * */
  def must(req: RequestHeader): Boolean =  {
    req.headers.get("User-Agent") match {
      case Some(agent) => (agent contains "Java")  | (agent contains "AppleWebKit")  |
        (agent contains "Opera") | (agent contains "libcurl")
      case None => true
    }
  }
}



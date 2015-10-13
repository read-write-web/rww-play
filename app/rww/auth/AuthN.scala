package rww.play.auth

import java.security.Principal

import play.api.mvc.RequestHeader
import rww.ldp.LDPExceptions.HttpAuthException
import rww.ldp.auth.WebIDPrincipal

import scala.concurrent.Future


/**
 * Close to java's notion of a Subject: it groups together a number
 * of verified credentials ( for the moment this is simplified to Principals )
 * and a number of failed attempts at authentication ( perhaps should be outside this class )
 * There are different principals, for example a public key principl, a WebID principal, etc...
 *
 * To think about:
 * One may want to tie each principal to the credentials that verified it: eg, Certificate or HTTP Auth.
 *
 * @param principals
 */
case class Subject(principals: List[Principal],failures: List[HttpAuthException]=List()) {
  lazy val webIds = principals.collect{
      case wp: WebIDPrincipal => wp
    }
}

/**
 * Authentication function
 */
trait AuthN extends (RequestHeader => Future[Subject])

case class AuthenticationError(cause: Throwable) extends Exception(cause)








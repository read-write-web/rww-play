package org.w3.readwriteweb.play.auth

import play.api.mvc._
import javax.security.auth.Subject
import java.security.Principal
import play.api.libs.concurrent.Promise
import java.security.cert.Certificate


/**
 * An Authorization Action
 * Wraps an Action, which it authorizes (or not)
 * @param guard a method that filters requests, into those that are authorized (maps to true) and those that are not
 * @param action the action that will be run if authorized
 * @tparam A the type of the request body
 */
case class AuthZ[A](guard: RequestHeader => Boolean)(action: Action[A]) extends Action[A] {

  def apply(request: Request[A]): Result = {
    if (guard(request)) action(request)
    else Results.Unauthorized
  }

  override
  def parser = action.parser
}

/**
 * A class of Guards, that provide web access control functions for each request
 *
 * The individual guards are given a function from requests to subject/authors of that request
 * and a function from requests to groups of Subjects that are allowed to make such a request
 * and finds out if a particular request is allowed or not.
 *
 * @param subject a function from requests to subjects of that request
 * @param group a function from requests to groups that are allowed to make that request
 */
case class Guard(
    subject: RequestHeader => Subject,
    group: RequestHeader => Group) extends (RequestHeader => Boolean) {

  /**
   * @param request the request made
   * @return true, iff that request is allowed
   */
  def apply(request: RequestHeader): Boolean = group(request).member(subject(request))
}

trait Group {
   def member(subj: =>Subject): Boolean
}

/**
 * The group that every agent is a member of .
 */
object EveryBody extends Group {
   def member(subj: =>Subject) = true
}




/**
 * A very silly check that just requires the user to be authenticated with TLS
 * if the path starts with "/a"
 */
//case class ACheck(req: RequestHeader) extends Check {
//
//  def request(subj: => Promise[Subject]): Boolean = req.path.startsWith("/a")
//
//  def subject = req.certs.map{certs=>
//     val subj = new Subject()
//     subj.getPublicCredentials.add(certs)
//     subj.getPrincipals.add(PubKeyPrincipal(certs(0)))
//     subj
//  }
//
//}
//
///** A principal where the identifier is just the public key itself */
//case class PubKeyPrincipal(cert: Certificate) extends Principal {
//  //would be good to find a better way to write this out
//  def getName = cert.getPublicKey.toString
//}
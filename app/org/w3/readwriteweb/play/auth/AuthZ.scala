package org.w3.readwriteweb.play.auth

import play.api.mvc._
import javax.security.auth.Subject
import java.security.Principal
import play.api.libs.concurrent.Promise
import java.security.cert.{X509Certificate, Certificate}
import org.w3.play.auth.{JenaWebIDAuthN, Claim, WebIDPrincipal}
import scalaz._
import Scalaz._
import java.util.Collections
import org.w3.banana.BananaException

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
 * An Authorization Action
 * Wraps an Action, which it authorizes (or not)
 * @param guard a method that filters requests, into those that are authorized (maps to true) and those that are not
 * @param action the action that will be run if authorized
 * @tparam A the type of the request body
 */
case class AsyncAuthZ[A](guard: RequestHeader => Promise[Boolean])(action: Action[A]) extends Action[A] {

  def apply(request: Request[A]): Result = {
    AsyncResult {
      guard(request).map { bool =>
        if (bool) action(request)
        else Results.Unauthorized
      }
    }
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


/**
 * An Aynchronous Guard, that may take time to find the Subject of the request, and take time
 * to determine the group that have access to the resource requested.
 * @param subject
 * @param group
 */
case class AGuard( subject: RequestHeader => Promise[Subject],
                       group: RequestHeader => Promise[Group]) extends (RequestHeader => Promise[Boolean]) {
  /**
   * @param request the request made
   * @return true, iff that request is allowed
   */
  def apply(request: RequestHeader): Promise[Boolean] = group(request).flatMap { g =>
    g.asyncMember(subject(request))
  }

}
//
// Some obvious groups
//

trait Group {
   def member(subj: => Subject): Boolean
   def asyncMember(subj: => Promise[Subject]): Promise[Boolean] = subj map { s => member(s) }
}

/**
 * The group that every agent is a member of .
 * There is therefore never any need to determine the subject: that calculation can be ignored.
 */
object EveryBody extends Group {
   def member(subj: => Subject) = true
   override def asyncMember(subj: => Promise[Subject]) = Promise.pure(true)
}

/**
 * The group of people with a valid WebID
 */
object WebIDAgent extends Group {
  import scala.collection.JavaConversions.asScalaSet
  def member(subj: => Subject): Boolean = subj.getPrincipals.exists(_.isInstanceOf[WebIDPrincipal])
}

//
// Some useful Subject extractors
//

trait AsyncSubjectFinder extends (RequestHeader => Promise[Subject])

object AWebIDFinder extends AsyncSubjectFinder {
  def apply(headers: RequestHeader) = {
    val res = headers.certs.map(certs=>
      certs(0) match {
        case x509: X509Certificate => {
          val x509claim = Claim.ClaimMonad.point(x509)
          val promiseOfValidations: Promise[List[Validation[BananaException,Principal]]] =
            Promise.sequence(JenaWebIDAuthN.verify(x509claim))
          val futurePrincipals : Promise[List[Principal]] = promiseOfValidations.map {
            _.filter(_.isSuccess).map(_.toOption.get)
          }

          val futureSubj: Promise[Subject] = for ( principalList <- futurePrincipals ) yield {
            import collection.JavaConversions._
            new Subject(true,new java.util.HashSet(principalList),Collections.singleton(x509claim),new java.util.HashSet())
          }
          futureSubj
        }
        case other => Promise.pure(new Subject)
      }
    )
    res.flatMap(p=>p)
  }
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
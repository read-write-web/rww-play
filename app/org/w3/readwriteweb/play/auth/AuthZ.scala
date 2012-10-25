/*
 * Copyright 2012 Henry Story, http://bblfish.net/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.w3.readwriteweb.play.auth

import play.api.mvc._
import java.security.Principal
import java.security.cert.{X509Certificate, Certificate}
import org.w3.play.auth.{WebIDVerifier, Claim, WebIDPrincipal}
import scalaz._
import Scalaz._
import java.util.Collections
import org.w3.banana._
import scala.concurrent.{ExecutionContext, Future}
import scala.Some
import org.w3.play.auth.WebIDPrincipal
import util.FutureValidation
import play.api.mvc.AsyncResult
import scala.Some
import play.api.libs.iteratee.{Input, Done}


/**
 * Something that should end up working with javax.security.auth.Subject, but with a better API.
 */
case class Subject(principals: List[BananaValidation[Principal]])

object Anonymous extends Subject(List())

/**
 * An Authorization Action
 * Wraps an Action, which it authorizes (or not)
 * @param guard a method that filters requests, into those that are authorized (maps to true) and those that are not
 * @param action the action that will be run if authorized
 * @tparam A the type of the request body
 */
//case class AuthZ[A](guard: RequestHeader => Boolean)(action: Action[A]) extends Action[A] {
//
//  def apply(request: Request[A]): Result = {
//    if (guard(request)) action(request)
//    else Results.Unauthorized
//  }
//
//  override
//  def parser = action.parser
//}

/**
 * a group of agents
 * This is used to determine a Subject's membership of the Group
 */
trait Group {

  /**
   * determine if a subject is a member of the group
   * We don't return boolean, because the subj passed is passed lazily and we want to recuperate its value, if it was
   * calculated. We pass the subject lazily since we want to avoid the cost of authentication if possible )
   * todo: should the Subject returned be a filtered version of the original subject, containing only Principals
   * that were accepted?
   * @param subj the Subject as a lazy function so that unprotected resources don't need to ask for authentication
   * @return The Subject authorized
   */

  def member(subj: => Subject): Option[Subject]

  def asyncMembers(subj: => Future[Subject])(implicit ec: ExecutionContext): Future[Option[Subject]]  =
    subj map { s => member(s) }
}


/**
 * Authenticated Action creator.
 * objects of this class can be tuned to create authenticated actions given an Authentication function
 * and an access control function.
 *
 * val WebIDAuthN = new WebIDAuthN[Jena]()
 *
 * def webId(rg: pathParams) =
 *    WebIDAuthN { authReq =>
 *        Ok("You are authorized. We found a WebID: "+authReq.user)
 *    }
 *
 *
 * @param authn An Authentication function from request headers to Future[Subject]
 * @param acl   An Acl function from request to a Future[Group]. The Group can decide whether Subjects are members of it
 * @param onUnauthorized Result to return on failure
 * @param ec ExecutionContext
 */
class Auth( authn: RequestHeader => Future[Subject],
               acl: RequestHeader => Future[Group],
               onUnauthorized: RequestHeader => Result)
                        (implicit val ec: ExecutionContext)  {


    def apply[A]( p: BodyParser[A])( action: AuthRequest[A] => Result): Action[A] =
      Action(p) {  req =>
          AsyncResult {
            for {group <- acl(req)
                 subject <- group.asyncMembers(authn(req))
            } yield {
              subject.map(s => action(AuthRequest(s, req))).getOrElse(onUnauthorized(req))
            }
          }
      }

   import play.api.mvc.BodyParsers._
   def apply( action: AuthRequest[_] => Result): Action[AnyContent] = apply(parse.anyContent)(action)

}


/**
 * An Authorized Request
 * @param user  the user that is authorized
 * @param request the request the user was authorized for
 * @tparam A the type of the Request
 */
case class AuthRequest[A]( val user: Subject, request: Request[A] ) extends WrappedRequest(request)




/**
 * A class of Guards, that provide web access control functions for each request
 *
 * The individual guards are given a function from requests to findSubject/authors of that request
 * and a function from requests to groups of Subjects that are allowed to make such a request
 * and finds out if a particular request is allowed or not.
 *
 * @param subject a function from requests to subjects of that request
 * @param group a function from requests to groups that are allowed to make that request
 */
//case class Guard(
//    subject: RequestHeader => Subject,
//    group: RequestHeader => Group) extends (RequestHeader => Boolean) {
//
//  /**
//   * @param request the request made
//   * @return true, iff that request is allowed
//   */
//  def apply(request: RequestHeader): Boolean = group(request).member(subject(request))
//}


/**
 * An Aynchronous Guard, that may take time to find the Subject of the request, and take time
 * to determine the findGroup that have access to the resource requested.
 * @param findSubject
 * @param findGroup
 */
//case class AGuard( findSubject: RequestHeader => Future[Subject],
//                   findGroup:   RequestHeader => Future[Group])
//  (implicit ec: ExecutionContext)  {
//  /**
//   * @param request the request made
//   * @return true, iff that request is allowed
//   */
//  def apply(request: RequestHeader): Future[Subject] = findGroup(request).flatMap { g =>
//    g.asyncMember(findSubject(request))
//  }
//
//}
//
// Some obvious groups
//


/**
 * The Group that every agent is a member of .
 * There is therefore never any need to determine the findSubject: that calculation can be ignored.
 */
object EveryBody extends Group {
   val futureAnonymous = Future.successful(Some(Anonymous))

  /**
   * Since everybody is a member of this group, all users are equal.
   * @param subj
   * @return the anonymous agent ( todo: think about this )
   */
   override def asyncMembers(subj: => Future[Subject])(implicit ec: ExecutionContext) = futureAnonymous

  /**
   * determine if a subject is a member of the group
   * We don't return boolean, because the subj passed is passed lazily and we want to recuperate its value, if it was
   * calculated. We pass the subject lazily since we want to avoid the cost of authentication if possible )
   * @param subj
   * @return
   */
  def member(subj: => Subject) = Some(Anonymous)
}

/**
 * The group of people with a valid WebID
 */
object WebIDGroup extends Group {

  def member(subj: => Subject): Option[Subject] = {
    val subject = subj
    if ( subject.principals.exists(_.exists(_.isInstanceOf[WebIDPrincipal])) )
      Some(subject)
    else None
  }


}

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




/**
 * A very silly check that just requires the user to be authenticated with TLS
 * if the path starts with "/a"
 */
//case class ACheck(req: RequestHeader) extends Check {
//
//  def request(subj: => Futur[Subject]): Boolean = req.path.startsWith("/a")
//
//  def findSubject = req.certs.map{certs=>
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
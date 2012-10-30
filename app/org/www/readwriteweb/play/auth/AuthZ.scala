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

package org.www.readwriteweb.play.auth

import play.api.mvc._
import java.security.Principal
import org.w3.banana._
import jena.Jena
import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.AsyncResult
import scala.Some
import org.www.play.auth.{AuthN, WebIDAuthN, WebIDVerifier, WebIDPrincipal}
import java.net.URL
import org.www.readwriteweb.play.PlayWriterBuilder
import scalaz.Validation
import controllers.Application.WebIDAuth
import play.api.mvc.BodyParsers.parse


/**
 * Something that should end up working with javax.security.auth.Subject, but with a better API.
 */
case class Subject(principals: List[BananaValidation[Principal]], authzPrincipals: List[Principal]=List()) {
  lazy val validPrincipals = principals.flatMap { pv =>
    pv.toOption
  }
  lazy val webIds = validPrincipals.flatMap{ p =>
    p match {
      case wp: WebIDPrincipal => Some(wp.webid)
      case _ => None
    }
  }
}

object Anonymous extends Subject(List())

trait WebRequest[Rdf<:RDF] {
  def subject: Future[Subject]
  def method: Mode
  def meta: Rdf#URI
  def uri: Rdf#URI
}

/**
 * class to wrap web request methods, as these usually have information missing.
 * Allows one to tie together information that is strongly related to the web
 * request. For example it is important to know the full URL of a request in order
 * to know if a remote ACL is speaking about it
 *
 * @param authn authentication function
 * @param base base url, to turn a request into a full url
 * @param metaFun: method to calculate metadata location
 * @param req request header to wrap
 * @param ops
 * @tparam Rdf
 */
class PlayWebRequest[Rdf<:RDF](authn: AuthN, base: URL, metaFun: String => URL)(req: RequestHeader)
                              (implicit ops: RDFOps[Rdf]) extends WebRequest[Rdf] {
  lazy val subject: Future[Subject] = authn(req)

  val method = req.method match {
    case "GET" => Read
    case "PUT" => Write
    case "POST" => Control
    case "DELETE" => Write
  }

  /**
   * location of metadata
   */
  val meta = ops.URI(metaFun(req.path).toString)

  /**
   * actual URI of the resource: needed to be able to work out from remote
   */
  lazy val uri = {
    ops.URI(new URL(base,req.path).toString)
  }
}




/**
 * A guard determines access a request.
 * This is parameterised on Refusal Type and Acceptance Types as a guard protecting access
 * to resources by types not related to user identity, may want to return differnt types of objects
 * @tparam AcceptT  The Type of the Acceptance
 */
trait Guard[AcceptT,Rdf<:RDF] {
  /**
   *
   * @param request  the request to give access to - The full request is passed as it is possible that
   *                 the body contains information needed for authorization
   * @return A future answer on whether or not to allow access. The future will fail with an exception
   *         reporting the reason of the failure.
   */
  def allow(request: WebRequest[Rdf]): Future[AcceptT]
}

trait IdGuard[Rdf<:RDF] extends Guard[Subject,Rdf]


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
 * @param guard The guard protecting the resource
 * @param webRequest Wraps the request header into a WebRequest
 * @param ec ExecutionContext
 */
class Auth[Rdf<:RDF](guard: IdGuard[Rdf],webRequest: RequestHeader => WebRequest[Rdf])
                    (implicit ec: ExecutionContext)  {


  /**
   *
   * @param p
   * @param onUnauthorized
   * @param action
   * @tparam A
   * @return
   */
    def apply[A]( p: BodyParser[A]=parse.anyContent)
                (onUnauthorized: AuthFailure[A] => Result)
                ( action: AuthRequest[A] => Result): Action[A] =
      Action(p) {  req: Request[A] =>
        val futureSubj: Future[Subject] = guard.allow(webRequest(req))
        AsyncResult {
          futureSubj.map( subj => action(AuthRequest[A](subj, req))).recover{
            case failure: Exception => onUnauthorized(AuthFailure(failure,req))  //what about other throwables?
          }
        }
      }

//   import play.api.mvc.BodyParsers._
//   def apply(onUnauthorized: AuthFailure[A] => Result)( action: AuthRequest[A] => Result): Action[AnyContent] =
//     apply(parse.anyContent)(onUnauthorized)(action)

}


/**
 * An Authorized Request
 * @param user  the user that is authorized
 * @param request the request the user was authorized for
 * @tparam A the type of the Request
 */
case class AuthRequest[A]( val user: Subject, request: Request[A] ) extends WrappedRequest(request)

case class AuthFailure[A]( val exception: Exception, request: Request[A]) extends WrappedRequest(request)



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





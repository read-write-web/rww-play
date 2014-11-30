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

package rww.play

import java.net.{URI => jURI, URL => jURL}

import _root_.play.api.Logger
import _root_.play.api.libs.Files.TemporaryFile
import _root_.play.api.libs.iteratee.Enumerator
import akka.http.model.headers._
import akka.http.model.parser.HeaderParser._
import org.w3.banana._
import org.w3.banana.io.MimeType
import rww.ldp.LDPCommand._
import rww.ldp.LDPExceptions._
import rww.ldp._
import rww.ldp.actor.RWWActorSystem
import rww.ldp.auth.{WACAuthZ, WebIDPrincipal}
import rww.ldp.model.{BinaryResource, LDPC, LDPR, NamedResource}
import rww.play.auth.AuthN

import scala.concurrent.{ExecutionContext, Future}
import scalaz.Either3
import scalaz.Either3._


// TODO not appropriate place
object Method extends Enumeration {
  val Read = Value
  val Write = Value
  val Append = Value
}


case class AuthorizedModes(user: List[WebIDPrincipal], path: String, modesAllowed: Set[Method.Value])

/**
 * This permits to transmit a result and to add an User header in the request which contains the URI of the authenticated user's WebID
 * @param webid of the authenticated user
 * @param result the result
 * @tparam R
 */
case class IdResult[R](webid: jURI, result: R) {
  lazy val id = List(WebIDPrincipal(webid))
}

/**
 * A result with all the Authorization Mode info
 * @param authInfo
 * @param result
 * @tparam R
 */
case class AuthResult[R](authInfo: AuthorizedModes, result: R)

//the idea of this class was to not be reliant on Play! We need RequestHeader in order to do authentication
//todo: find a way of abstracting this

import _root_.play.api.mvc.{RequestHeader => PlayRequestHeader}


class ResourceMgr[Rdf <: RDF](base: jURL, rww: RWWActorSystem[Rdf], authn: AuthN, authz: WACAuthZ[Rdf])
                             (implicit ops: RDFOps[Rdf], sparqlOps: SparqlOps[Rdf],
                              ec: ExecutionContext) {

  import authz._
  import ops._
  import org.w3.banana.diesel._

  val ldp = LDPPrefix[Rdf]
  val rdfs = RDFSPrefix[Rdf]
  val wac = WebACLPrefix[Rdf]


  /**
   * @param path of the resource
   * @return the pair consisting of the collection and the name of the resource to make a request on
   */
  private def split(path: String): Pair[String, String] = {
    val i = path.lastIndexOf('/')
    if (i < 0) ("", path)
    else (path.substring(0, i + 1), path.substring(i + 1, path.length))
  }

  def patch(content: RwwContent)(implicit request: PlayRequestHeader): Future[IdResult[Boolean]] = {
    val path = request.path
    content match {
      case updatedQuery: PatchRwwContent[Rdf] => for {
        id <- auth(request, request.getAbsoluteURI.toString, Method.Write)
        x <- rww.execute(
          for {
            resrc <- getResource(URI(request.getAbsoluteURI.toString))
            y <- HttpResourceUtils.ifMatch(resrc){ () =>
              patchLDPR(URI(path), updatedQuery.query, Map())
            }
          } yield y
        )
      } yield IdResult(id, x)
      case _ => Future.failed(new Exception("PATCH requires application/sparql-update message content"))
    }
  }


  def put(content: RwwContent)(implicit request: PlayRequestHeader): Future[IdResult[Rdf#URI]] = {
    import HttpResourceUtils.ifMatch
    val path = request.path
    val (collection, file) = split(path)
    if ("" == file) Future.failed(new PropertiesConflict("Cannot do a PUT on a collection"))
    else for {
      id <- auth(request, request.getAbsoluteURI.toString, Method.Write)
      f <- content match {
        //todo: arbitrarily for the moment we only allow a PUT of same type things graphs on graphs, and other on other
        case grc: GraphRwwContent[Rdf] => {
          rww.execute(for {
            resrc <- getResource(URI(request.getAbsoluteURI.toString))
            x <- resrc match {
              case ldpc: LDPC[Rdf] => {
                throw PropertiesConflict("cannot do a PUT on an LDPContainer, it clashes with ldp:contains which are server managed")
              }
              case ldpr: LDPR[Rdf] => ifMatch(resrc) { () =>
                  putLDPR(ldpr.location, grc.graph)
                }
              case other => throw OperationNotSupported(s"Not expected resource type for path $request.getAbsoluteURI.toString, type: $other")
            }
          } yield IdResult[Rdf#URI](id, resrc.location))
        }
        case BinaryRwwContent(tmpFile, mime) => {
          rww.execute(
            for {
              resrc <- getResource(URI(request.getAbsoluteURI.toString))
            } yield resrc match {
              case binaryResource: BinaryResource[Rdf] => {
                //todo: very BAD. This will block the agent, and so on long files break the collection.
                //this needs to be sent to another agent, or it needs to be rethought
                ifMatch(resrc) { () =>
                  Enumerator.fromFile(tmpFile.file) |>>> binaryResource.writeIteratee
                  IdResult(id, resrc.location)
                }
              }
              case _ => throw OperationNotSupported("currently we don't permit overwriting an RDF resource with a non-rdf one ")
            })
        }
        case _ => Future.failed(new Exception("cannot apply method - improve bug report"))
      }
    } yield f
  }


  /**
   *
   * @param request
   * @param path
   * @param mode the mode of the request
   * @return AuthorizeModes for the resource and user ( may authenticate user with TLS if needed )
   */
  def fullAuthInfo(request: PlayRequestHeader, path: String, mode: Method.Value): Future[AuthorizedModes] = {
    Logger.info(s"fullAuthInfo(_,$path,$mode)")
    //1. find out what the user is authenticated as
    //todo: we should be able to deal with a session containing multiple ids. WebIDs & e-mail addresses, etc...
    val webIdsList = request.session.get("webid").toList.map(u => WebIDPrincipal(new jURI(u)))
    val relativePathUri = URI(path)
    //2. find out all rights for this user

    //todo: what we are missing here is a way for the user to say that he prefers to be authenticated
    //because otherwise for most resources we may just return what the public user is allowed access to.

    getAllowedMethodsForAgent(relativePathUri, webIdsList).flatMap { allowedMethods =>
      Logger.info(s"the allowed methods for agent $webIdsList is $allowedMethods")
      //3. if the requested method is allowed
      if (allowedMethods.contains(mode)) {
        Future.successful(AuthorizedModes(webIdsList, path, allowedMethods))
      }
      else if (webIdsList.isEmpty) {
        //todo: we need a way to tell that the user has been asked to authenticate but does not want to be
        //if the person has not been authenticated yet, authenticate if possible, then verify
        authn(request).flatMap { subject =>
          val newWebIds = subject.webIds
          Logger.info(s" user agent is identified by $newWebIds")
          getAllowedMethodsForAgent(relativePathUri, newWebIds).map { authzModes =>
            Logger.info(s"the allowed methods for the now newly authenticated agent $newWebIds is $allowedMethods")
            AuthorizedModes(newWebIds, path, authzModes)
          }
        }
      } else {
        // the user cannot access the resource in the desired mode, and has not authenticated successfully
        Future.successful(AuthorizedModes(webIdsList, path, allowedMethods))
      }
    }
  }

  /**
   * todo: This could be made part of the Play Action.
   *
   * @param request
   * @param path the resource on which an action is required
   * @param mode the type of the action
   * @return the authorized agent WebID
   */
  def auth(request: PlayRequestHeader, path: String, mode: Method.Value): Future[jURI] = {
    getAuthorizedWebIDsFor(URI(path), wacIt(mode)).flatMap { agents =>
      Logger.debug(s"Agents found for $path with mode $mode are: $agents")
      if (agents.contains(foaf.Agent)) {
        Logger.info(s"All agents can access with mode $mode on ${request.path}")
        Future.successful(new jURI(foaf.Agent.toString))
      }
      else if (agents.isEmpty) {
        Future.failed(AccessDenied(s"No agents found is allowed to access with mode $mode on ${request.path}"))
      }
      else {
        authn(request).flatMap { subject =>
          subject.webIds.find { wid =>
            agents.contains(URI(wid.webid.toString))
          } match {
            case Some(id) => {
              Logger.info(s"Access allowed with mode $mode on ${request.path}. Subject $subject has been found on the allowed agents list $agents")
              Future.successful(id.webid)
            }
            case None => Future.failed(AccessDenied(s"No access for $mode on ${request.path}. Subject is $subject and allowed agents are $agents"))
          }
        }
      }
    }
  }


  def get(request: PlayRequestHeader, uri: java.net.URI): Future[AuthResult[NamedResource[Rdf]]] = {
    fullAuthInfo(request, uri.toString, Method.Read).flatMap { authmodes =>
      Logger.info(s"get(_,$uri) fullAuthInfo returns $authmodes")
      if (authmodes.modesAllowed.contains(Method.Read)) {
        rww.execute(getResource(URI(uri.toString), None)).map(id => AuthResult(authmodes, id))
      } else {
        Future.failed(AccessDeniedAuthModes(authmodes))
      }
    }
  }

  def makeLDPR(collectionPath: String, content: Rdf#Graph, slug: Option[String])
              (implicit request: PlayRequestHeader): Future[IdResult[Rdf#URI]] = {
    val uric: Rdf#URI = URI(request.getAbsoluteURI.toString)
    for {
      id <- auth(request, request.getAbsoluteURI.toString, Method.Write)
      x <- rww.execute(
        for {
          r <- createLDPR(uric, slug, content)
          meta <- getMeta(r)
          //locally we know we always have an ACL rel
          //todo: but this should really be settable in turtle files. For example it may be much better
          //todo: if every file in a directory just use the acl of the directory. So that would require the
          //todo: collection to specify how to build up the acls.
          aclg = (meta.acl.get -- wac.include ->- URI(".acl")).graph
          _ <- updateLDPR(meta.acl.get, add = aclg.triples)
        } yield {
          r
        }
      )
    } yield IdResult(id, x)
  }


  /**
   * HTTP POST of a graph
   * @param request
   * @param slug a suggested name for the resource
   * @param content
   * @return
   */
  def postGraph(slug: Option[String], content: Option[Rdf#Graph])(implicit request: PlayRequestHeader): Future[IdResult[Rdf#URI]] = {

    // just on RWWPlay we can adopt the convention that if the object ends in a "/"
    // then it is a collection, anything else is not a collection
    val (collection, file) = split(request.path)
    val g = content.getOrElse(emptyGraph)
    if ("" == file) {
      //todo: do we still need both createLDPR and createContainer if the type of action is determined by the content?
      if (find(g, URI(""), rdf.typ, ldp.Container).hasNext)
        makeCollection(collection, slug, content)
      else makeLDPR(collection, g, slug)
    } else {
      Future.failed(WrongTypeException("POSTing on a LDPR that is not an LDPC is not defined")) //
    }
  }

  def makeCollection(coll: String, slug: Option[String], content: Option[Rdf#Graph])
                    (implicit request: PlayRequestHeader): Future[IdResult[Rdf#URI]] = {
    for {
      id <- auth(request, request.getAbsoluteURI.toString, Method.Write)
      x <- rww.execute {
        for {
          c <- createContainer(URI(request.getAbsoluteURI.toString), slug,
            content.getOrElse(Graph.empty) union Graph(Triple(URI(""), rdf.typ, ldp.Container))
          )
          meta <- getMeta(c)
          //locally we know we always have an ACL rel
          //todo: but this should really be settable in turtle files. For example it may be much better
          //todo: if every file in a directory just use the acl of the directory. So that would require the
          //todo: collection to specify how to build up the acls.
          aclg = (meta.acl.get -- wac.include ->- URI("../.acl")).graph
          _ <- updateLDPR(meta.acl.get, add = aclg.triples)
        } yield c
      }
    } yield IdResult(id, x)
  }

  def postBinary(path: String, slug: Option[String], tmpFile: TemporaryFile, mime: MimeType)
                (implicit request: PlayRequestHeader): Future[IdResult[Rdf#URI]] = {
    val (collection, file) = split(path)
    if ("" != file) Future.failed(WrongTypeException("Can only POST binary on a Collection"))
    else {
      val containerUri = request.getAbsoluteURI.toString
      Logger.debug(s"Will post binary on containerUri=$containerUri with slug=$slug and mimeType=$mime")
      for {
        id <- auth(request, containerUri, Method.Write)
        x <- rww.execute {
          for {
            binaryResource <- createBinary(URI(containerUri), slug, mime)
            meta <- getMeta(binaryResource.location)
            //locally we know we always have an ACL rel
            //todo: but this should really be settable in turtle files. For example it may be much better
            //todo: if every file in a directory just use the acl of the directory. So that would require the
            //todo: collection to specify how to build up the acls.
            aclg = (meta.acl.get -- wac.include ->- URI(".acl")).graph
            _ <- updateLDPR(meta.acl.get, add = aclg.triples)
          } yield {
            Enumerator.fromFile(tmpFile.file) |>>> binaryResource.writeIteratee
            binaryResource.location
          }
        }
      } yield IdResult(id, x)
    }
  }


  def delete(implicit request: PlayRequestHeader): Future[IdResult[Unit]] = for {
    id <- auth(request, request.getAbsoluteURI.toString, Method.Write)
    e <- rww.execute(
      for {
      //todo: this is an example where it is important that both of the following commands happen in the same action
      //todo: currently same actor messages are not parsed in the same loop
      //either that or the deleteResource() needs to pass the etag - but then why not the whole request
        resrc <- getResource(URI(request.getAbsoluteURI.toString))
        unit <- HttpResourceUtils.ifMatch(resrc) { () =>
          deleteResource(URI(request.getAbsoluteURI.toString))
        }
      } yield unit )
  } yield IdResult(id,e)


  def postQuery(path: String, query: QueryRwwContent[Rdf])(implicit request: PlayRequestHeader): Future[IdResult[Either3[Rdf#Graph, Rdf#Solutions, Boolean]]] = {
    val (collection, file) = split(path)

    import sparqlOps._
    //clearly the queries could be simplified here.
    for {
      id <- auth(request, request.getAbsoluteURI.toString, Method.Write)
      e <- rww.execute {
        if ("" != file)
          fold(query.query)(
            select => selectLDPR(URI(path), select, Map.empty).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            construct => constructLDPR(URI(path), construct, Map.empty).map(left3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            ask => askLDPR(URI(path), ask, Map.empty).map(right3[Rdf#Graph, Rdf#Solutions, Boolean] _)
          )
        else
          fold(query.query)(
            select => selectLDPC(URI(path), select, Map.empty).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            construct => constructLDPC(URI(path), construct, Map.empty).map(left3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            ask => askLDPC(URI(path), ask, Map.empty).map(right3[Rdf#Graph, Rdf#Solutions, Boolean] _)
          )
      }
    } yield IdResult(id, e)

  }


}

object HttpResourceUtils {

  /**
   * @return true if able to proceed with request
   */
  def ifMatch[Rdf <: RDF, Result](resource: NamedResource[Rdf])(function: () => Result)
                                 (implicit request: PlayRequestHeader): Result = {
    val ifmatch = request.headers.get("If-Match")
    val result =ifmatch.map(value =>
      for {
        x <- parseHeader(RawHeader("If-Match", value)).right
      } yield {
        val `If-Match`(etagRange) = x
        EntityTag.matchesRange(resource.etag.get, etagRange, false)
      }
    ) map {
      case Left(_) => false // If-Match header did not parse so false
      case Right(result) => result
    }
    result match {
      case Some(true) => function()
      case Some(false) =>
        throw ETagsDoNotMatch(s"received If-Match ${ifmatch} but resource has etag ${resource.etag}  ")
      case None => throw MissingEtag("altering the resource requires an ETag")
    }
  }


  def ifNoneMatch[Rdf <: RDF](result: NamedResource[Rdf])(implicit request: PlayRequestHeader): Boolean = {

    request.headers.get("If-None-Match").map { value =>
      for {
        x <- parseHeader(RawHeader("If-None-Match", value)).right
      } yield {
        val `If-None-Match`(etagRange) = x
        EntityTag.matchesRange(result.etag.get, etagRange, true)
      }
    } match {
      case None => true //No If-Match header so pass
      case Some(Left(_)) => false // If-Match header did not parse so false
      case Some(Right(result)) => result
    }

  }
}

trait ReadWriteWebException extends Exception

case class CannotCreateResource(msg: String) extends ReadWriteWebException


case class Request(method: String, path: String)

case class Put[Rdf <: RDF](path: String, content: RwwContent)

case class Query[Rdf <: RDF](query: QueryRwwContent[Rdf], path: String)

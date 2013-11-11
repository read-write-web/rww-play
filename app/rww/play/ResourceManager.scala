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

import org.w3.banana._
import org.w3.banana.ldp.LDPCommand._
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumerator
import scalaz.Either3
import scalaz.Either3._
import play.api.libs.Files.TemporaryFile
import rww.play.auth.AuthN
import org.w3.banana.ldp._
import scala.Some
import org.w3.banana.ldp.auth.WACAuthZ
import java.net.URL


//the idea of this class was to not be reliant on Play! We need RequestHeader in order to do authentication
//todo: find a way of abstracting this
import play.api.mvc.{Request=>PlayRequest, RequestHeader=>PlayRequestHeader}


class ResourceMgr[Rdf <: RDF](base: URL, rww: RWW[Rdf], authn: AuthN, authz: WACAuthZ[Rdf])
                             (implicit ops: RDFOps[Rdf], sparqlOps: SparqlOps[Rdf],
                              ec: ExecutionContext) {

  import ops._
  import diesel._
  import syntax._

  import authz._

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

  def patch(request: PlayRequestHeader, content: RwwContent): Future[Boolean] = {
    val path = request.path
    val (collection, file) = split(path)
    content match {
      case updatedQuery: PatchRwwContent[Rdf] => for {
        _ <- auth(request, new URL(base, path).toString, Method.write)
        x <- rww.execute(patchLDPR(URI(path), updatedQuery.query, Map()))
      } yield x
      case _ => Future.failed(new Exception("PATCH requires application/sparql-update message content"))
    }
  }


  def put(request: PlayRequestHeader, content: RwwContent): Future[Rdf#URI] = {
    val path = request.path
    val (collection, file) = split(path)
    if ("" == file) Future.failed(new Exception("Cannot do a PUT on a collection"))
    else for {
      _ <- auth(request, new URL(base, path).toString, Method.write)
      f <- content match {
        //todo: arbitrarily for the moment we only allow a PUT of same type things graphs on graphs, and other on other
        case grc: GraphRwwContent[Rdf] => {
          rww.execute(for {
            resrc <- getResource(URI(file))
            x <- resrc match {
              case ldpr: LDPR[Rdf] => deleteResource(ldpr.location).flatMap(_ => createLDPR[Rdf](ldpr.location, Some(file), grc.graph))
              case _ => throw new Error("yoyo")
            }
          } yield resrc.location)
        }
        case BinaryRwwContent(tmpFile, mime) => {
          rww.execute(
            for {
              resrc <- getResource(URI(file))
            //                if (resrc.isInstanceOf[BinaryResource[Rdf]])
            } yield {
              val b = resrc.asInstanceOf[BinaryResource[Rdf]]
              //todo: very BAD. This will block the agent, and so on long files break the collection.
              //this needs to be sent to another agent, or it needs to be rethought
              Enumerator.fromFile(tmpFile.file)(ec)(b.write)
              resrc.location
            })
        }
        case _ => Future.failed(new Exception("cannot apply method - improve bug report"))
      }
    } yield f
  }

  //    val res = store.execute(Command.PUT(LinkedDataResource(resourceURI, PointedGraph(resourceURI, model))))
  //    res
  //  }

  def wacIt(mode: Method.Value) = mode match {
    case Method.read => wac.Read
    case Method.write => wac.Write
  }


  /**
   * todo: This could be made part of the Play Action.
   *
   * @param request
   * @param path
   * @param mode
   * @return
   */
  def auth(request: PlayRequestHeader, path: String, mode: Method.Value): Future[Unit] = {
    getAuthFor(URI(path), wacIt(mode)).flatMap {
      agents =>
        if (agents.contains(foaf.Agent)) Future.successful(())
        else if (agents.isEmpty) {
          Future.failed(AccessDenied(s"no agents allowed access with $mode on ${request.path}"))
        }
        else {
          authn(request).flatMap {
            subject =>
              val a = subject.webIds.exists {
                wid =>
                  agents.contains(URI(wid.toString))
              }
              if (a) {
                Future.successful(())
              }
              else {
                Future.failed(AccessDenied(s"no access for $mode on ${request.path}"))
              }
          }
        }
    }
  }


  def get(request: PlayRequestHeader, path: String): Future[NamedResource[Rdf]] = {
    for {
      _ <- auth(request, new URL(base, path).toString, Method.read)
      x <- rww.execute {
        getResource(URI(path), None)
      }
    } yield x
  }

  def makeLDPR(request: PlayRequestHeader, collectionPath: String, content: Rdf#Graph, slug: Option[String]): Future[Rdf#URI] = {
    val uric: Rdf#URI = URI(collectionPath)
    for {
      _ <- auth(request, new URL(base, request.path).toString, Method.write)
      x <- rww.execute(
        for {
          r <- createLDPR(uric, slug, content)
          meta <- getMeta(r)
          //locally we know we always have an ACL rel
          //todo: but this should really be settable in turtle files. For example it may be much better
          //todo: if every file in a directory just use the acl of the directory. So that would require the
          //todo: collection to specify how to build up the acls.
          aclg = (meta.acl.get -- wac.include ->- URI(".acl")).graph
          _ <- updateLDPR(meta.acl.get, add = aclg.toIterable)
        } yield {
          r
        }
      )
    } yield x
  }


  /**
   * HTTP POST of a graph
   * @param path
   * @param content
   * @param slug a suggested name for the resource
   * @return
   */
  def postGraph(request: PlayRequestHeader, slug: Option[String], content: Option[Rdf#Graph]): Future[Rdf#URI] = {
    // just on RWWPlay we can adopt the convention that if the object ends in a "/"
    // then it is a collection, anything else is not a collection
    val (collection, file) = split(request.path)
    val g = content.getOrElse(emptyGraph)
    if ("" == file) {
      //todo: do we still need both createLDPR and createContainer if the type of action is determined by the content?
      if (find(g, URI(""), rdf.typ, ldp.Container).hasNext)
        makeCollection(request, collection, slug, content)
      else makeLDPR(request, collection, g, slug)
    } else {
      Future.failed(WrongTypeException("POSTing on a LDPR that is not an LDPC is not defined")) //
    }
  }

  def makeCollection(request: PlayRequestHeader, coll: String, slug: Option[String], content: Option[Rdf#Graph]): Future[Rdf#URI] = {
    for {
      _ <- auth(request, new URL(base, request.path).toString, Method.write)
      x <- rww.execute {
        for {
          c <- createContainer(URI(coll), slug,
            content.getOrElse(Graph.empty) union Graph(Triple(URI(""), rdf.typ, ldp.Container))
          )
          meta <- getMeta(c)
          //locally we know we always have an ACL rel
          //todo: but this should really be settable in turtle files. For example it may be much better
          //todo: if every file in a directory just use the acl of the directory. So that would require the
          //todo: collection to specify how to build up the acls.
          aclg = (meta.acl.get -- wac.include ->- URI("../.acl")).graph
          _ <- updateLDPR(meta.acl.get, add = aclg.toIterable)
        } yield c
      }
    } yield x
  }

  def postBinary(request: PlayRequestHeader, path: String, slug: Option[String], tmpFile: TemporaryFile, mime: MimeType): Future[Rdf#URI] = {
    val (collection, file) = split(path)
    val ldpc = URI(collection)
    if ("" != file) Future.failed(WrongTypeException("Can only POST binary on a Collection"))
    else
      for {
        _ <- auth(request, new URL(base, request.path).toString, Method.write)
        x <- rww.execute {
          for {
            b <- createBinary(URI(path), slug, mime)
          } yield {
            Enumerator.fromFile(tmpFile.file)(ec)(b.write)
            b.location
          }
        }
      } yield x

  }


  def delete(request: PlayRequestHeader): Future[Unit] = {
    val path = request.path
    val (collection, file) = split(path)
    for {
      _ <- auth(request, new URL(base, path).toString, Method.write)
      e <- rww.execute(deleteResource(URI(path)))
    } yield e
    //    rww.execute{
    //        for {
    //          x <- deleteResource(URI(file))
    //          y <- updateLDPR(URI(""),remove=List(
    //              Tuple3[Rdf#NodeMatch,Rdf#NodeMatch,Rdf#NodeMatch](ldpc.uri,dsl.ops.ANY,URI(file))).toIterable)
    //        } yield y
    //      }
    //    } yield { Unit }
  }

  def postQuery(request: PlayRequestHeader, path: String, query: QueryRwwContent[Rdf]): Future[Either3[Rdf#Graph, Rdf#Solutions, Boolean]] = {
    val (collection, file) = split(path)
    import sparqlOps._
    //clearly the queries could be simplified here.
    for {
      _ <- auth(request, new URL(base, path).toString, Method.write)
      e <-rww.execute {
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
    } yield e

  }


}

trait ReadWriteWebException extends Exception

case class CannotCreateResource(msg: String) extends ReadWriteWebException


case class Request(method: String, path: String)

case class Put[Rdf <: RDF](path: String, content: RwwContent)

case class Query[Rdf <: RDF](query: QueryRwwContent[Rdf], path: String)

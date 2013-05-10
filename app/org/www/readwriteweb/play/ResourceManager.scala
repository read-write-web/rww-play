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

package org.www.readwriteweb.play

import org.w3.banana._
import org.w3.banana.ldp.LDPCommand._
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumerator
import scalaz.Either3
import scalaz.Either3._
import play.api.libs.Files.TemporaryFile
import play.api.mvc.RequestHeader
import org.www.play.auth.AuthN
import org.w3.banana.ldp._
import scala.Some
import org.w3.banana.ldp.auth.WACAuthZ
import java.net.URL

class ResourceMgr[Rdf <: RDF](base: URL, rww: RWW[Rdf], authn: AuthN, authz: WACAuthZ[Rdf])
                             (implicit ops: RDFOps[Rdf], sparqlOps: SparqlOps[Rdf],
                              ec: ExecutionContext) {
  import ops._
  import diesel._
  import syntax.graphW

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
    if (i <0) ("",path)
    else (path.substring(0,i+1),path.substring(i+1,path.length))
  }



  def put(path: String, content: RwwContent): Future[Rdf#URI] = {
    val (collection, file) = split(path)
    if ("" == file) Future.failed(new Exception("Cannot do a PUT on a collection"))
    else {
      content match {
        //todo: arbitrarily for the moment we only allow a PUT of same type things graphs on graphs, and other on other
        case grc: GraphRwwContent[Rdf] => {
          rww.execute(for {
               resrc <- getResource(URI(file))
               x <- resrc match {
                   case ldpr: LDPR[Rdf] =>  deleteResource(ldpr.location).flatMap(_ => createLDPR[Rdf](ldpr.location,Some(file),grc.graph))
                   case _ =>                throw new Error("yoyo")
                 }
            } yield resrc.location)
        }
        case BinaryRwwContent(tmpFile, mime)  => {
         rww.execute(
              for {
                resrc <- getResource(URI(file))
//                if (resrc.isInstanceOf[BinaryResource[Rdf]])
              } yield {
                val b = resrc.asInstanceOf[BinaryResource[Rdf]]
                //todo: very BAD. This will block the agent, and so on long files break the collection.
                //this needs to be sent to another agent, or it needs to be rethought
                Enumerator.fromFile(tmpFile.file)(b.write)
                resrc.location
              })
        }
        case _ => Future.failed(new Exception("cannot apply method - improve bug report"))
      }
    }
  }

//    val res = store.execute(Command.PUT(LinkedDataResource(resourceURI, PointedGraph(resourceURI, model))))
//    res
//  }

  def wacIt(mode:Method.Value) = mode match {
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
  def auth(request: RequestHeader, path: String, mode: Method.Value): Future[Unit] =
    getAuthFor(URI(path), wacIt(mode)).flatMap { agents =>
      if (agents.contains(foaf.Agent)) Future.successful(())
      else {
        authn(request).flatMap { subject =>
          val a = subject.webIds.exists{ wid =>
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


  def get(request: RequestHeader, path: String): Future[NamedResource[Rdf]] = {
    for {
        _ <- auth(request,new URL(base,path).toString,Method.read)
        x <- rww.execute{ getResource(URI(path),None) }
    } yield x
  }

  def makeLDPR(collectionPath: String, content: Rdf#Graph,  slug: Option[String]): Future[Rdf#URI] = {
    val uric: Rdf#URI = URI(collectionPath)
    rww.execute(
        for {
          r <- createLDPR(uric,slug, content)
          git =  ( uric -- rdfs.member ->- r ).graph.toIterable
          _ <- updateLDPR(r,add=git)
        } yield r
      )
  }




  /**
   * HTTP POST of a graph
   * @param path
   * @param content
   * @param slug a suggested name for the resource
   * @return
   */
  def postGraph(path: String, slug: Option[String], content: Option[Rdf#Graph] ): Future[Rdf#URI] = {
    // just on RWWPlay we can adopt the convention that if the object ends in a "/"
    // then it is a collection, anything else is not a collection
    val (collection, file) = split(path)
    println(s"($collection, $file)")
    val g = content.getOrElse(emptyGraph)
    if ("" == file) {
      //todo: do we still need both createLDPR and createContainer if the type of action is determined by the content?
      if (find(g,URI(""),rdf.typ,ldp.Container).hasNext)
           makeCollection(collection,slug,content)
      else makeLDPR(collection,g,slug)
    } else {
      rww.execute {
          for {
            _ <- updateLDPR(URI(path),add=g.toIterable)
          } yield URI(path)
       }
    }
  }

  def makeCollection(coll: String, slug: Option[String], content: Option[Rdf#Graph]): Future[Rdf#URI] = {
    println(s"makeCollection($coll,$slug,...)")
    rww.execute(createContainer(URI(coll),slug,
      content.getOrElse(Graph.empty) union Graph(
        Triple(URI(""), rdf.typ, ldp.Container)
      )
    ))
  }

  def postBinary(path: String, slug: Option[String], tmpFile: TemporaryFile, mime: MimeType): Future[Rdf#URI] = {
    val (collection, file) = split(path)
    println(s"postBinary($path, $slug, $tmpFile, $mime)")
    val ldpc = URI(collection)
    if (""!=file) Future.failed(new Exception("Cannot only POST binary on a Collection"))
    else {
      val r = rww.execute {
        for {
          b <- createBinary(URI(path),slug,mime)
          _ <- updateLDPR(ldpc, add = (ldpc -- rdfs.member ->- b.location).graph.toIterable)
        } yield {
          Enumerator.fromFile(tmpFile.file)(b.write)
          b.location
        }
      }
      r
    }
  }

  def delete(path: String): Future[Unit] = {
    val (collection, file) = split(path)
    println(s"($collection, $file)")
    rww.execute(deleteResource(URI(path)))
//    rww.execute{
//        for {
//          x <- deleteResource(URI(file))
//          y <- updateLDPR(URI(""),remove=List(
//              Tuple3[Rdf#NodeMatch,Rdf#NodeMatch,Rdf#NodeMatch](ldpc.uri,dsl.ops.ANY,URI(file))).toIterable)
//        } yield y
//      }
//    } yield { Unit }
  }

  def postQuery(path: String, query: QueryRwwContent[Rdf]): Future[Either3[Rdf#Graph,Rdf#Solutions,Boolean]] = {
    val (collection, file) = split(path)
    import sparqlOps._
    //clearly the queries could be simplified here.
    rww.execute {
        if ("" != file)
          fold(query.query)(
            select => selectLDPR(URI(file), select, Map.empty).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            construct => constructLDPR(URI(file), construct, Map.empty).map(left3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            ask => askLDPR(URI(file), ask, Map.empty).map(right3[Rdf#Graph, Rdf#Solutions, Boolean] _)
          )
        else
          fold(query.query)(
            select => selectLDPC(URI(path),select, Map.empty).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            construct => constructLDPC(URI(path), construct, Map.empty).map(left3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            ask => askLDPC(URI(path),ask, Map.empty).map(right3[Rdf#Graph, Rdf#Solutions, Boolean] _)
          )
      }
   }


}

trait ReadWriteWebException extends Exception

case class CannotCreateResource(msg: String) extends ReadWriteWebException


case class Request(method: String, path: String)

case class Put[Rdf <: RDF](path: String, content: RwwContent)

case class Query[Rdf <: RDF](query: QueryRwwContent[Rdf], path: String)

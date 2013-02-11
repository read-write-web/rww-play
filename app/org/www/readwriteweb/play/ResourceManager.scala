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
import org.w3.banana.plantain.LDPCommand._
import concurrent.{Future, ExecutionContext}
import org.w3.banana.plantain._
import play.api.libs.iteratee.Enumerator
import scalaz.Either3
import scalaz.Either3._
import scala.Tuple3
import scala.Some
import play.api.libs.Files.TemporaryFile
import java.net.URL
import play.api.mvc.RequestHeader

class ResourceMgr[Rdf <: RDF](ldps: RWW[Rdf])
                             (implicit dsl: Diesel[Rdf],
                              sparqlOps: SparqlOps[Rdf],
                              authz: AuthZ[Rdf],
                              ec: ExecutionContext) {
  import dsl._
  import dsl.ops._
  import authz._
  import System.out

  val ldp = LDPPrefix[Rdf]
  val rdfs = RDFsPrefix[Rdf]
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
        case GraphRwwContent(graph: Rdf#Graph) => {
          ldps.execute(for {
               resrc <- getResource(URI(file))
               x <- resrc match {
                   case ldpr: LDPR[Rdf] =>  deleteResource(ldpr.uri).flatMap(_ => createLDPR[Rdf](ldpr.uri,Some(file),graph))
                   case _ =>                throw new Error("yoyo")
                 }
            } yield resrc.uri)
        }
        case BinaryRwwContent(tmpFile, mime)  => {
         ldps.execute(
              for {
                resrc <- getResource(URI(file))
//                if (resrc.isInstanceOf[BinaryResource[Rdf]])
              } yield {
                val b = resrc.asInstanceOf[BinaryResource[Rdf]]
                //todo: very BAD. This will block the agent, and so on long files break the collection.
                //this needs to be sent to another agent, or it needs to be rethought
                Enumerator.fromFile(tmpFile.file)(b.write)
                resrc.uri
              })
        }
        case _ => Future.failed(new Exception("cannot apply method - improve bug report"))
      }
    }
  }

//    val res = store.execute(Command.PUT(LinkedDataResource(resourceURI, PointedGraph(resourceURI, model))))
//    res
//  }

  def get( path: String): Future[NamedResource[Rdf]] = {
    for {
        x <- ldps.execute{ getResource(URI(path)) }
    } yield x
  }

  def makeLDPR(collectionPath: String, content: GraphRwwContent[Rdf],  slug: Option[String]): Future[Rdf#URI] = {
    val uric: Rdf#URI = URI(collectionPath)
    ldps.execute(
        for {
          r <- createLDPR(uric,slug, content.graph)
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
  def postGraph(path: String, content: GraphRwwContent[Rdf],  slug: Option[String] ): Future[Rdf#URI] = {
    // just on RWWPlay we can adopt the convention that if the object ends in a "/"
    // then it is a collection, anything else is not a collection
    val (collection, file) = split(path)
    out.println(s"($collection, $file)")
    if ("" == file) {
      makeLDPR(collection,content,slug)
    } else {
      ldps.execute {
          for {
            _ <- updateLDPR(URI(path),add=content.graph.toIterable)
          } yield URI(path)
       }
    }
  }

  def makeCollection(coll: String, path: String, content: Option[Rdf#Graph]): Future[Rdf#URI] = {
    out.println(s"makeCollection($coll,$path,...)")
    val file = path.substring(coll.length)
    ldps.execute(createContainer(URI(coll),Some(file),content.getOrElse(Graph.empty)))
//      .map { ldpc =>
//       if (content != None) ldpc.execute(updateLDPR(URI(file),remove=Iterable.empty,add=content.get.toIterable))
//       ldpc.uri
//    }
  }

  def postBinary(path: String, slug: Option[String], tmpFile: TemporaryFile, mime: MimeType): Future[Rdf#URI] = {
    val (collection, file) = split(path)
    out.println(s"postBinary($path, $slug, $tmpFile, $mime)")
    val ldpc = URI(collection)
    if (""!=file) Future.failed(new Exception("Cannot only POST binary on a Collection"))
    else {
      val r = ldps.execute {
        for {
          b <- createBinary(URI(path),slug,mime)
          _ <- updateLDPR(ldpc, add = (ldpc -- rdfs.member ->- b.uri).graph.toIterable)
        } yield {
          Enumerator.fromFile(tmpFile.file)(b.write)
          b.uri
        }
      }
      r
    }
  }

  def delete(path: String): Future[Unit] = {
    val (collection, file) = split(path)
    out.println(s"($collection, $file)")
    ldps.execute(deleteResource(URI(path)))
//    ldps.execute{
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
    ldps.execute {
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

object LDPPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new FOAFPrefix(ops)
}

class LDPPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("ldp", "http://www.w3.org/ns/ldp#")(ops) {
  val Container = apply("container")
  val membershipSubject = apply("membershipSubject")
  val membershipPredicate = apply("membershipPredicate")
  val nextPage = apply("nextPage")
  val pageOf = apply("pageOf")
}

object RDFsPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new RDFsPrefix(ops)
}

class RDFsPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("ldp", "http://www.w3.org/2000/01/rdf-schema#")(ops) {
  val member = apply("member")
}

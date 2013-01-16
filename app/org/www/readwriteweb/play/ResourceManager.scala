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

class ResourceMgr[Rdf <: RDF](ldps: LDPS[Rdf])(implicit dsl: Diesel[Rdf],
                              sparqlOps: SparqlOps[Rdf],
                              ec: ExecutionContext) {
  import dsl._
  import dsl.ops._
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
          for {
            ldpc <-  ldps.getLDPC(URI(collection)) recoverWith { case _ => ldps.createLDPC(URI(collection)) }
            uri <- ldpc.execute(for {
               resrc <- getResource(URI(file))
               x <- resrc match {
                   case ldpr: LDPR[Rdf] =>  createLDPR[Rdf](Some(resrc.uri),graph)
                   case _ =>                throw new Error("yoyo")
                 }
            } yield resrc.uri)
         } yield uri
        }
        case BinaryRwwContent(tmpFile, mime)  => {
          for {
            ldpc <-  ldps.getLDPC(URI(collection)) recoverWith { case _ => ldps.createLDPC(URI(collection)) }
            uri <- ldpc.execute(
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
          } yield uri
        }
        case _ => Future.failed(new Exception("cannot apply method - improve bug report"))
      }
    }
  }

//    val res = store.execute(Command.PUT(LinkedDataResource(resourceURI, PointedGraph(resourceURI, model))))
//    res
//  }

  def get(path: String): Future[NamedResource[Rdf]] = {
    val (collection, file) = split(path)
    for {
        ldpc <- ldps.getLDPC(URI(collection))
        x   <- ldpc.execute{ getResource(URI(file)) }
    } yield x
  }


  def postGraph(path: String, content: GraphRwwContent[Rdf],  slug: Option[String] ): Future[Rdf#URI] = {
    // just on RWWPlay we can adopt the convention that if the object ends in a "/"
    // then it is a collection, anything else is not a collection
    val (collection, file) = split(path)
    out.println(s"($collection, $file)")
    if ("" == file) {
      for {
        ldpc <- ldps.getLDPC(URI(collection)) recoverWith { case _ => ldps.createLDPC(URI(collection)) }
        name: Option[Rdf#URI] = slug.map(u=>URI(u))
        uri <- ldpc.execute(
            for {
              r <- createLDPR(name, content.graph)
              git =  ( ldpc.uri -- rdfs.member ->- r ).graph.toIterable
              _ <- updateLDPR(ldpc.uri,add=git)
          } yield r
        )
      } yield uri
    } else {
      for {
        ldpc <- ldps.getLDPC(URI(collection)) recoverWith { case _ => ldps.createLDPC(URI(collection)) }
        gr <- ldpc.execute {
          for {
            gr <- updateLDPR(URI(file),remove=Iterable.empty,add=content.graph.toIterable)
            newGr <- getLDPR(URI(file))
          } yield newGr
        }
      } yield URI(path)
    }
  }

  def delete(path: String): Future[Unit] = {
    val (collection, file) = split(path)
    out.println(s"($collection, $file)")
    for{
      ldpc <- ldps.getLDPC(URI(collection))
      _ <- ldpc.execute{
        for {
          x <- deleteResource(URI(file))
          y <- updateLDPR(URI(file),remove=List(Tuple3[Rdf#NodeMatch,Rdf#NodeMatch,Rdf#NodeMatch](URI(file),dsl.ops.ANY,dsl.ops.ANY)).toIterable)
        } yield y
      }
    } yield { Unit }
  }

  def postQuery(path: String, query: QueryRwwContent[Rdf]): Future[Either3[Rdf#Graph,Rdf#Solutions,Boolean]] = {
    val (collection, file) = split(path)
    import sparqlOps._
    for {
      ldpc <- ldps.getLDPC(URI(collection))
      e3 <- ldpc.execute {
        if ("" != file)
          fold(query.query)(
            select => selectLDPR(URI(file), select, Map.empty).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            construct => constructLDPR(URI(file), construct, Map.empty).map(left3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            ask => askLDPR(URI(file), ask, Map.empty).map(right3[Rdf#Graph, Rdf#Solutions, Boolean] _)
          )
        else
          fold(query.query)(
            select => selectLDPC(select, Map.empty).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            construct => constructLDPC( construct, Map.empty).map(left3[Rdf#Graph, Rdf#Solutions, Boolean] _),
            ask => askLDPC(ask, Map.empty).map(right3[Rdf#Graph, Rdf#Solutions, Boolean] _)
          )
      }
    } yield e3
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

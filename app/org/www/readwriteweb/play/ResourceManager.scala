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
import plantain.LDPCommand._
import concurrent.{Future, ExecutionContext}
import plantain._
import play.api.libs.iteratee.Enumerator
import scala.Some


trait ReadWriteWebException extends Exception

case class CannotCreateResource(msg: String) extends ReadWriteWebException


case class Request(method: String, path: String)

case class Put[Rdf <: RDF](path: String, content: RwwContent)

case class Query[Rdf <: RDF](query: QueryRwwContent[Rdf], path: String)



class ResourceMgr[Rdf <: RDF](ldps: LDPS[Rdf])
                         (implicit ops: RDFOps[Rdf],
                          sparqlOps: SparqlOps[Rdf],
                          ec: ExecutionContext ) {
  import ops._
  import System.out

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
         x   <- ldpc.execute{
           if ("" == file) {
              ???
           } else {
             for { r <- getResource(URI(file)) } yield r
           }
         }
    } yield x
  }


  def post(path: String, content: RwwContent,  slug: Option[String] ): Future[Rdf#URI] = {
    // just on RWWPlay we can adopt the convention that if the object ends in a "/"
    // then it is a collection, anything else is not a collection
    val (collection, file) = split(path)
    out.println(s"($collection, $file)")
    content match {
      case GraphRwwContent(graph: Rdf#Graph) => {
        if ("" == file) {
          for{
            ldpc <- ldps.getLDPC(URI(collection)) recoverWith { case _ => ldps.createLDPC(URI(collection)) }
            xx = out.println("got to here")
            name: Option[Rdf#URI] = slug.map(u=>URI(u))
            uri <- ldpc.execute(createLDPR(name, graph))
          } yield { uri }
        } else {
          out.println("in else clause of post")
          for {
            ldpc <- ldps.getLDPC(URI(collection)) recoverWith { case _ => ldps.createLDPC(URI(collection)) }
            gr <- ldpc.execute {
              for {
                gr <- updateLDPR(URI(file),remove=Iterable.empty,add=graph.toIterable)
                newGr <- getLDPR(URI(file))
              } yield newGr
            }
          } yield URI(path)
        }
      }
    }
  }

  def delete(path: String): Future[Unit] = {
    val (collection, file) = split(path)
    out.println(s"($collection, $file)")
    for{
      ldpc <- ldps.getLDPC(URI(collection))
      _ <- ldpc.execute(deleteResource(URI(file)))
    } yield { Unit }
  }


  //  def query(q: Rdf#Query, path: String): Future[Either3[Rdf#Graph, Rdf#Solutions, Boolean]] = {
//    val resourceURI =  absolute(path)
//
//    import sparqlOps._
//     import Either3._
//     fold(q)(
//       select => store.executeSelect(select).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean]_),
//       construct => store.executeConstruct(construct).map(left3[Rdf#Graph, Rdf#Solutions, Boolean]_),
//       ask => store.executeAsk(ask).map(right3[Rdf#Graph, Rdf#Solutions, Boolean]_)
//     )

}


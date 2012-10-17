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
import java.io.File
import org.w3.banana.jena._
import java.net.URL
import scalaz.Either3
import org.w3.readwriteweb.play.{RwwContent, QueryRwwContent}
import concurrent.ExecutionContext


trait ReadWriteWebException extends Exception

case class CannotCreateResource(msg: String) extends ReadWriteWebException


case class Request(method: String, path: String)

case class Put[Rdf <: RDF](path: String, content: RwwContent)

case class Query[Rdf <: RDF](query: QueryRwwContent[Rdf], path: String)



class ResourceMgr
[Rdf <: RDF, +SyntaxType](file: File, url: URL,
                          store: RDFStore[Rdf, BananaFuture])
                         (implicit ops: RDFOps[Rdf],
                          sparqlOps: SparqlOps[Rdf],
                          diesl: Diesel[Rdf],
                          ec: ExecutionContext ) {
  import ops._
  val graphStore = GraphStore[Rdf, BananaFuture](store)

  private def absolute(path: String) = URI(new URL(url,path).toString)

  def put(path: String, model: Rdf#Graph): BananaFuture[Unit] = {
    val resourceURI =absolute(path)
    System.out.println("new resource name="+resourceURI)
    val res = store.execute(Command.PUT(LinkedDataResource(resourceURI, PointedGraph(resourceURI, model))))
    res
  }

  def get(path: String): BananaFuture[Rdf#Graph] = {
    graphStore.getGraph(absolute(path))
  }

  def query(q: Rdf#Query, path: String): BananaFuture[Either3[Rdf#Graph, Rdf#Solutions, Boolean]] = {
    val resourceURI =  absolute(path)

    import sparqlOps._
     import Either3._
     fold(q)(
       select => store.executeSelect(select).map(middle3[Rdf#Graph, Rdf#Solutions, Boolean]_),
       construct => store.executeConstruct(construct).map(left3[Rdf#Graph, Rdf#Solutions, Boolean]_),
       ask => store.executeAsk(ask).map(right3[Rdf#Graph, Rdf#Solutions, Boolean]_)
     )
  }
}

class JenaResourceMgr(file: File, url: URL, store: RDFStore[Jena, BananaFuture])(implicit ec: ExecutionContext)
  extends ResourceMgr[Jena, Turtle](file, url, store)(JenaOperations, JenaSparqlOps, JenaDiesel,ec)
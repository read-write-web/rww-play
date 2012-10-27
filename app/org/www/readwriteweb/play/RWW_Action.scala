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

import play.api.mvc._
import java.net.URL
import org.w3.banana.jena._
import java.io.File
import akka.util.Timeout
import com.hp.hpl.jena.sparql.core.DatasetGraphFactory
import org.www.play.auth.{WebIDAuthN, WebIDVerifier}
import org.www.play.rdf.jena.{JenaSparqlQueryIteratee, JenaConfig}


object ReadWriteWeb_App extends Controller {
  import play.api.libs.concurrent._

  import JenaConfig._

  implicit val timeout = Timeout(10 * 1000)

  //todo: needed for WebIDVerifier, but that should be changed
  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _

  implicit val JenaWebIDAuthN = new WebIDVerifier[Jena]()
  val jenaRwwBodyParser = new RwwBodyParser[Jena](JenaOperations, JenaSparqlOps,
    jenaAsync.graphIterateeSelector, JenaSparqlQueryIteratee.sparqlSelector )

  val WebIDAuthN = new WebIDAuthN[Jena]()


  //  if this class were shipped as a plugin, then the code below might work.
//  But it is a bit odd given that you have to specify whether something is secure or not.
//
//  lazy val url = call.absoluteURL(true)
//  def call : Call = org.w3.readwriteweb.play.routes.ReadWriteWeb_App.get

  val store = JenaStore(DatasetGraphFactory.createMem())

  val url = new URL("http://localhost:9000/2012/")
//  lazy val rwwActor = system.actorOf(Props(new JenaResourceManager(new File("test_www"), url, store)), name = "rwwActor")
  lazy val rwwActor =  new JenaResourceMgr(new File("test_www"), url, store)

  //import some implicits
  import JenaRDFWriter.{selector=>RDFWriterSelector}
  import JenaSolutionsWriter.{solutionsWriterSelector=>SparqWriterSelector}
  import org.w3.banana.BooleanWriter.{selector=>BoolWriterSelector}
  import org.www.readwriteweb.play.PlayWriterBuilder._

  //    JenaRDFBlockingWriter.WriterSelector()
//    req.accept.collectFirst {
//      case "application/rdf+xml" =>  (writeable(JenaRdfXmlWriter),ContentTypeOf[Jena#Graph](Some("application/rdf+xml")))
//      case "text/turtle" => (writeable(JenaTurtleWriter), ContentTypeOf[Jena#Graph](Some("text/turtle")))
//      case m @ SparqlAnswerJson.mime => (writeable(JenaSparqlJSONWriter), ContentTypeOf[JenaSPARQL#Solutions](Some(m)))
//    }.get

  def get(path: String) = Action { request =>
    System.out.println("in GET on resource <"+path+">")

    Async {
    val future = for (graph <- rwwActor.get( path ) failMap { e => ExpectationFailed(e.getMessage)})
    yield {
            writerFor[Jena#Graph](request)(RDFWriterSelector).map {
              wr => result(200, wr)(graph)
            } getOrElse {
              UnsupportedMediaType("could not find serialiserfor Accept types"+request.headers.get(play.api.http.HeaderNames.ACCEPT))
            }
          }
     future.fold(f=>f,s=>s)
    }

  }

  def put(path: String) = Action(jenaRwwBodyParser) {
    request =>
      System.out.println("in PUT on <"+path+">")
      val msg = request.body match {
        case GraphRwwContent(graph: Jena#Graph) => {
          val future = for {
            answer <- rwwActor.put(path, graph) failMap { e => ExpectationFailed(e.getMessage) }
          } yield {
             Ok("Succeeded")
          }
          Async {
            future.fold(f=>f,s=>s)
          }
        }
        case _ => throw new Exception("error")
      }
      msg
  }

  def post(path: String) = Action(jenaRwwBodyParser) {
    request =>
      import play.api.Play.current
      //this is a good piece of code for a future, as serialising the graph is very fast
      System.out.println("in POST on <"+request.uri+">")
      System.out.println("triple num== " + request.body)
      request.body match {
        case GraphRwwContent(graph: Jena#Graph) => {
          Async {
            Akka.future {
             writerFor[Jena#Graph](request)(RDFWriterSelector).map { wr=>
                result(200,wr)(graph)
             }.getOrElse(UnsupportedMediaType("cannot parse content type".getBytes("UTF-8")))
            }
          }
        }
        case QueryRwwContent(q: Jena#Query) => {
          val future = for (answer <- rwwActor.query(q,path) failMap (e => ExpectationFailed(e.getMessage)))
          yield {
             answer.fold(
               graph => writerFor[Jena#Graph](request)(RDFWriterSelector).map {
                 wr => result(200, wr)(graph)
               },
               sol => writerFor[Jena#Solutions](request)(SparqWriterSelector).map {
                  wr => result(200, wr)(sol)
                },
                bool => writerFor[Boolean](request)(BoolWriterSelector).map {
                  wr => result(200, wr)(bool)
                }
              ).getOrElse(UnsupportedMediaType("cannot parse content type".getBytes("UTF-8")))
          }
          Async {
            future.fold(f=>f,s=>s)
          }
        }
        case _ => Ok("received content")
      }
  }


}





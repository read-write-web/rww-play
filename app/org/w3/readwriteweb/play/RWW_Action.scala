package org.w3.readwriteweb.play

import play.api.mvc._
import org.w3.banana._
import java.net.URL
import org.w3.banana.jena._
import java.io.File
import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import scalaz.{Either3, Validation}


object ReadWriteWeb_App extends Controller {
  import akka.pattern.ask
  import play.api.libs.concurrent._
  import PlayWriterBuilder._

  val system = ActorSystem("MySystem")
  implicit val timeout = Timeout(10 * 1000)

//  if this class were shipped as a plugin, then the code below might work.
//  But it is a bit odd given that you have to specify whether something is secure or not.
//
//  lazy val url = call.absoluteURL(true)
//  def call : Call = org.w3.readwriteweb.play.routes.ReadWriteWeb_App.get

  val url = new URL("https://localhost:8443/2012/")
  lazy val rwwActor = system.actorOf(Props(new ResourceManager(new File("test_www"), url)), name = "rwwActor")

  //import some implicits
  import JenaRDFBlockingWriter.{WriterSelector=>RDFWriterSelector}
  import SparqlSolutionsWriter.{WriterSelector=>SparqWriterSelector}
  import org.w3.banana.BooleanWriter.{WriterSelector=>BoolWriterSelector}


//    JenaRDFBlockingWriter.WriterSelector()
//    req.accept.collectFirst {
//      case "application/rdf+xml" =>  (writeable(JenaRdfXmlWriter),ContentTypeOf[Jena#Graph](Some("application/rdf+xml")))
//      case "text/turtle" => (writeable(JenaTurtleWriter), ContentTypeOf[Jena#Graph](Some("text/turtle")))
//      case m @ SparqlAnswerJson.mime => (writeable(JenaSparqlJSONWriter), ContentTypeOf[JenaSPARQL#Solutions](Some(m)))
//    }.get



  def get(path: String) = Action { request =>
    System.out.println("in GET with path="+request.path)
    val future = for (answer <- rwwActor ask Request("GET", path ) mapTo manifest[Validation[BananaException, Jena#Graph]])
    yield {
         answer.fold(
          e => ExpectationFailed(e.getMessage),
          g => {
            writerFor[Jena#Graph](request)(RDFWriterSelector).map {
              wr => result(200, wr)(g)
            } getOrElse {
              UnsupportedMediaType("could not find serialiserfor Accept types"+request.headers.get(play.api.http.HeaderNames.ACCEPT))
            }
          }
         )
    }
    Async {
     future.asPromise
    }

  }

  def put(path: String) = Action(jenaRwwBodyParser) {
    request =>
      System.out.println("in put with path="+path)
      val msg = request.body match {
        case GraphRwwContent(graph) => Put[Jena](path,request.body)
        case _ => throw new Exception("error")
      }
      val future = for (answer <- rwwActor ask msg mapTo manifest[Validation[BananaException, Unit]])
      yield {
        answer.fold(
          e => ExpectationFailed(e.getMessage),
          _ =>  Ok("Succeeded")
        )
      }
      Async {
        future.asPromise
      }
  }

  def post(path: String) = Action(jenaRwwBodyParser) {
    request =>
      import play.api.Play.current
      //this is a good piece of code for a future, as serialising the graph is very fast
      System.out.println("triple num== " + request.body)
      request.body match {
        case GraphRwwContent(graph: Jena#Graph) => {
          Async {
            Akka.future {
             writerFor[Jena#Graph](request)(RDFWriterSelector).map { wr=>
                result(200,wr)(graph)
             }.getOrElse(UnsupportedMediaType("cannot parse content type"))
            }
          }
        }
        case q: QueryRwwContent[Jena] => {
          val future = for (answer <- rwwActor ask Query[Jena](q,path) mapTo manifest[Validation[BananaException,Either3[Jena#Solutions, Jena#Graph, Boolean]]])
          yield {
            answer.fold(
              e => ExpectationFailed(e.getMessage),
              answer => answer.fold(
                sol => writerFor[Jena#Solutions](request)(SparqWriterSelector).map {
                  wr => result(200, wr)(sol)
                },
                graph => writerFor[Jena#Graph](request)(RDFWriterSelector).map {
                  wr => result(200, wr)(graph)
                },
                bool => writerFor[Boolean](request)(BoolWriterSelector).map {
                  wr => result(200, wr)(bool)
                }
              ).getOrElse(UnsupportedMediaType("cannot parse content type"))
            )
          }
          Async {
            future.asPromise
          }
        }
        case _ => Ok("received content")
      }
  }


}





package org.www.readwriteweb.play

import play.api.mvc.Action
import org.w3.banana._
import org.w3.banana.plantain.{BinaryResource, LDPR}
import play.api.mvc.ResponseHeader
import play.api.mvc.SimpleResult
import play.api.mvc.Results._
import concurrent.ExecutionContext
import play.api.libs.iteratee.Enumerator
import java.io.{StringWriter, PrintWriter}

/**
 * ReadWriteWeb Controller for Play
 */
trait ReadWriteWeb[Rdf <: RDF]{

  def rwwActor: ResourceMgr[Rdf]
  implicit def writerSelector: WriterSelector[Rdf#Graph]
  implicit def rwwBodyParser: RwwBodyParser[Rdf]
  implicit def ec: ExecutionContext


  //todo: needed for WebIDVerifier, but that should be changed
  //  implicit def mkSparqlEngine = sparqlEngine.makeSparqlEngine _

  //  val jenaRwwBodyParser = new RwwBodyParser[Rdf]//(JenaOperations, JenaSparqlOps, jenaAsync.graphIterateeSelector, JenaSparqlQueryIteratee.sparqlSelector )

  //val WebIDAuthN = new WebIDAuthN[Jena]()


  //  if this class were shipped as a plugin, then the code below might work.
  //  But it is a bit odd given that you have to specify whether something is secure or not.
  //
  //  lazy val url = call.absoluteURL(true)
  //  def call : Call = org.w3.readwriteweb.play.routes.ReadWriteWebApp.get


  //  val url = new URL("http://localhost:9000/2012/")
  //  lazy val rwwActor = system.actorOf(Props(new JenaResourceManager(new File("test_www"), url, store)), name = "rwwActor")
  //lazy val rwwActor =  new JenaResourceMgr(new File("test_www"), url, store)

  import org.www.readwriteweb.play.PlayWriterBuilder._

  def about = Action {
    Ok( views.html.rww.ldp() )
  }

  def stackTrace(e: Throwable) = {
    val sw = new StringWriter(1024)
    e.printStackTrace(new PrintWriter(sw))
    sw.getBuffer.toString
  }

  //    JenaRDFBlockingWriter.WriterSelector()
  //    req.accept.collectFirst {
  //      case "application/rdf+xml" =>  (writeable(JenaRdfXmlWriter),ContentTypeOf[Jena#Graph](Some("application/rdf+xml")))
  //      case "text/turtle" => (writeable(JenaTurtleWriter), ContentTypeOf[Jena#Graph](Some("text/turtle")))
  //      case m @ SparqlAnswerJson.mime => (writeable(JenaSparqlJSONWriter), ContentTypeOf[JenaSPARQL#Solutions](Some(m)))
  //    }.get

  def get(path: String) = Action { request =>
    System.out.println("in GET on resource <" + path + ">")

    Async {
      val res = for {
        namedRes <- rwwActor.get(path)
      } yield {
        namedRes match {
          case ldpr: LDPR[Rdf] =>
            writerFor[Rdf#Graph](request)(writerSelector).map {
              wr => result(200, wr)(ldpr.graph)
            } getOrElse {
              UnsupportedMediaType("could not find serialiser for Accept types " +
                request.headers.get(play.api.http.HeaderNames.ACCEPT))
            }
          case bin: BinaryResource[Rdf] => {
            SimpleResult(
              header = ResponseHeader(200, Map("Content-Type" -> "todo")),
              body = bin.reader(1024 * 8)
            )
          }
        }
      }
      res recover {
        case e => ExpectationFailed(e.getMessage+"\n"+stackTrace(e))
      }
    }
  }

  def put(path: String) = Action(rwwBodyParser) { request =>
    Async {
      val future = for {
        answer <- rwwActor.put(path, request.body)
      } yield {
        Ok("Succeeded")
      }
      future recover {
        case e => ExpectationFailed(e.getMessage +"\n"+stackTrace(e))
      }
    }
  }

  def post(path: String) = Action(rwwBodyParser) { request =>
    Async {
      System.out.println(s"post($path)")
      val future = for {
        location <- rwwActor.post(path, request.body, None)
      } yield {
        Ok.withHeaders("Content-Location"->location.toString)
      }
      future recover {
        case e => ExpectationFailed(e.getMessage+"\n"+stackTrace(e))
      }
    }
  }

  def delete(path: String) = Action { request =>
    Async {
      System.out.println(s"post($path)")
      val future = for {
        _ <- rwwActor.delete(path)
      } yield {
        Ok
      }
      future recover {
        case e => ExpectationFailed(e.getMessage+"\n"+stackTrace(e))
      }
    }
  }


  //      import play.api.Play.current
  // keeping this for when I get back to queries....
  //        case QueryRwwContent(q: Jena#Query) => Async {
  //          val future = for (answer <- rwwActor.query(q,path))
  //          yield {
  //             answer.fold(
  //               graph => writerFor[Jena#Graph](request)(RDFWriterSelector).map {
  //                 wr => result(200, wr)(graph)
  //               },
  //               sol => writerFor[Jena#Solutions](request)(SparqWriterSelector).map {
  //                  wr => result(200, wr)(sol)
  //                },
  //                bool => writerFor[Boolean](request)(BoolWriterSelector).map {
  //                  wr => result(200, wr)(bool)
  //                }
  //              ).getOrElse(UnsupportedMediaType("cannot parse content type".getBytes("UTF-8")))
  //          }
  //          future recover { case e => ExpectationFailed(e.getMessage)}
  //        }
  //        case _ => Ok("received content")
  //      }
}








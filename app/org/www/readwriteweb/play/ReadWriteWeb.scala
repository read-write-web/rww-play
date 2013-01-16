package org.www.readwriteweb.play

import play.api.mvc.Action
import org.w3.banana._
import org.w3.banana.plantain.{BinaryResource, LDPR}
import play.api.mvc.Results._
import concurrent.ExecutionContext
import java.io.{StringWriter, PrintWriter}
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader

/**
 * ReadWriteWeb Controller for Play
 */
trait ReadWriteWeb[Rdf <: RDF]{

  def rwwActor: ResourceMgr[Rdf]
  implicit def rwwBodyParser: RwwBodyParser[Rdf]
  implicit def ec: ExecutionContext

  implicit def graphWriterSelector: WriterSelector[Rdf#Graph]
  implicit def solutionsWriterSelector: WriterSelector[Rdf#Solutions]
  implicit val boolWriterSelector: WriterSelector[Boolean] = BooleanWriter.selector

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
            writerFor[Rdf#Graph](request).map {
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
        case nse: NoSuchElementException => NotFound(nse.getMessage+stackTrace(nse))
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
        case nse: NoSuchElementException => NotFound(nse.getMessage+stackTrace(nse))
        case e => ExpectationFailed(e.getMessage +"\n"+stackTrace(e))
      }
    }
  }

  def post(path: String) = Action(rwwBodyParser) { request =>
    Async {
      System.out.println(s"post($path)")
      val future = request.body match {
        case rwwGraph: GraphRwwContent[Rdf] => {
           for {
            location <- rwwActor.postGraph(path, rwwGraph, None)
          } yield {
            Ok.withHeaders("Content-Location" -> location.toString)
          }
        }
        case rwwQuery: QueryRwwContent[Rdf] => {
          for {
            answer <- rwwActor.postQuery(path,rwwQuery)
          } yield {
             answer.fold(
               graph => writerFor[Rdf#Graph](request).map {
                 wr => result(200, wr)(graph)
               },
               sol => writerFor[Rdf#Solutions](request).map {
                  wr => result(200, wr)(sol)
                },
                bool => writerFor[Boolean](request).map {
                  wr => result(200, wr)(bool)
                }
              ).getOrElse(UnsupportedMediaType(s"Cannot publish anser of type ${answer.getClass} as"+
                s"one of the mime types given ${request.headers.get("Accept")}"))
          }
        }
//        case _ => Ok("received content")
      }
      future recover {
        case nse: NoSuchElementException => NotFound(nse.getMessage+stackTrace(nse))
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
        Gone
      }
      future recover {
        case nse: NoSuchElementException => NotFound(nse.getMessage+stackTrace(nse))
        case e => ExpectationFailed(e.getMessage+"\n"+stackTrace(e))
      }
    }
  }
}








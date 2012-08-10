package org.w3.readwriteweb.play

import play.api.mvc.{Controller, Action}
import java.net.URL
import org.w3.banana.RDF
import org.w3.banana.jena.{RDFWriterSelector, JenaRDFBlockingWriter, Jena}
import org.w3.readwriteweb.play.PlayWriterBuilder._
import play.api.libs.ws.ResponseHeaders
import akka.util.Timeout
import org.w3.play.remote._
import scalaz.{Failure, Success}
import org.w3.play.remote.LocalException
import scalaz.Success
import scalaz.Failure
import org.w3.play.remote.RemoteException

/**
 * A <a href="http://www.w3.org/TR/cors/">CORS</a> proxy that allows a client to fetch remote RDF
 * resources that do not have the required CORS headers.
 *
 * Currently this only permits GET operations. It is unclear what PUT, POST, DELETE operations would
 * look like for a CORS proxy.
 *
 */
class CORSProxy[Rdf<:RDF](fetcher: GraphFetcher[Rdf], writerSelector: RDFWriterSelector[Rdf#Graph]) extends Controller {

  // turn a header map into an (att,val) sequence
  private implicit def sequentialise(headers: Map[String,Seq[String]]) = headers.toSeq.flatMap(pair=>pair._2.map(v=>(pair._1,v)))

  def get(url: String) = Action {
    request =>
      System.out.println("in CORSProxy.get("+url+")")
      val iri = new URL(url)
      implicit val timeout = Timeout(10 * 1000)
      val promiseResult = for (answer <- fetcher.corsFetch(iri, request.headers.toMap))
      yield {
        answer match {
          case Failure(RemoteException(msg, headers)) => ExpectationFailed(msg)
          case Failure(LocalException(msg)) =>  ExpectationFailed(msg)
          case Success(GraphNHeaders(graph: Rdf#Graph, head)) => writerFor[Rdf#Graph](request)(writerSelector).map { wr =>
            val hdrs = head.headers - "ContentType"
            //todo: this needs to be refined a lot, and thought through quite a lot more carefully
            val code = if (head.status == 200) 203
                       else head.status

            val corsHeaders = if (!hdrs.contains("Access-Control-Allow-Origin")) {
               val origin = request.headers.get("Origin")
               hdrs + ("Access-Control-Allow-Origin" -> Seq(origin.getOrElse("*")))
            } else {
              hdrs
            }
            result(code, wr)(graph).withHeaders(corsHeaders: _*)
          } getOrElse {
            UnsupportedMediaType("could not find serialiserfor Accept types"+request.headers.get(play.api.http.HeaderNames.ACCEPT))
          }
        }
     }
     Async { promiseResult }
  }
}

object JenaCORSProxy extends CORSProxy[Jena](JenaGraphFetcher,JenaRDFBlockingWriter.WriterSelector)


//class JenaServiceConnection extends ServiceConnection[Jena](JenaAsync.graphIterateeSelector)


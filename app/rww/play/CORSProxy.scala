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

package rww.play

import play.api.mvc.{Controller, Action}
import org.w3.banana.{RDFOps, WriterSelector, RDF}
import rww.play.PlayWriterBuilder._
import akka.util.Timeout
import akka.actor.ActorSystem
import rww.ldp._
import rww.ldp.model.LDPR

/**
* A <a href="http://www.w3.org/TR/cors/">CORS</a> proxy that allows a client to fetch remote RDF
* resources that do not have the required CORS headers.
*
* Currently this only permits GET operations. It is unclear what PUT, POST, DELETE operations would
* look like for a CORS proxy.
*
*/
class CORSProxy[Rdf<:RDF](val wsClient: WebClient[Rdf])
                         (implicit ops: RDFOps[Rdf], writerSelector: WriterSelector[Rdf#Graph])
  extends Controller {

  import ops._

  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = system.dispatcher
  // turn a header map into an (att,val) sequence
  private implicit def sequentialise(headers: Map[String,Seq[String]]) = headers.toSeq.flatMap(pair=>pair._2.map(v=>(pair._1,v)))

  def get(url: String) = Action.async { request =>
    val iri = URI(url)
    implicit val timeout = Timeout(10 * 1000)
    import PlayWriterBuilder.writerFor

    val futureGraph = for {
      nr <- wsClient.get(iri)
    } yield {
      writerFor(request)(writerSelector).map { wr =>
        nr match {
          case ldpr: LDPR[Rdf] => {
            val hdrs = request.headers.toSimpleMap - "ContentType"
            //todo: this needs to be refined a lot, and thought through quite a lot more carefully

            val corsHeaders = if (!hdrs.contains("Access-Control-Allow-Origin")) {
              val origin = request.headers.get("Origin")
              hdrs + ("Access-Control-Allow-Origin" -> origin.getOrElse("*"))
            } else {
              hdrs
            }
            result(203, wr)(ldpr.graph).withHeaders(corsHeaders.toSeq: _*)
          }
          case other => {
            UnsupportedMediaType("Cannot proxy non rdf resources at present. Request sent " +
              request.headers.get(play.api.http.HeaderNames.ACCEPT))
          }
        }
      }.getOrElse(
        UnsupportedMediaType("could not find RDF type of resource at remote location" +
          request.headers.get(play.api.http.HeaderNames.ACCEPT))
      )
    }
    futureGraph recover {
      case RemoteException(msg, headers) => ExpectationFailed(msg)
      case LocalException(msg) => ExpectationFailed(msg)
    }
  }

}





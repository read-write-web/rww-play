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

import _root_.play.api.mvc.{AnyContent, SimpleResult, Controller, Action,Request => PlayRequest}
import org.w3.banana.{RDFOps, WriterSelector, RDF,Writer}
import rww.play.PlayWriterBuilder._
import akka.actor.ActorSystem
import rww.ldp._
import rww.ldp.model.{NamedResource, LDPR}
import java.net.{NoRouteToHostException, ConnectException}
import utils.ThrowableUtils.RootCauseExtractor
import java.nio.channels.UnresolvedAddressException
import java.util.concurrent.TimeoutException
import com.google.common.base.Throwables

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

  private def AllowOriginHeader(origin: String) = ("Access-Control-Allow-Origin" -> "*")

  // see https://github.com/stample/rww-play/issues/77
  // we don't want security warning on browser if there's an error
  private def errorResult(result: SimpleResult): SimpleResult = result.withHeaders(AllowOriginHeader("*"))


  private val UnresolvedAddressExceptionExtractor = RootCauseExtractor.of[UnresolvedAddressException]
  private val NoRouteToHostExceptionExtractor = RootCauseExtractor.of[NoRouteToHostException]
  private val ConnectExceptionExtractor = RootCauseExtractor.of[ConnectException]
  private val TimeoutExceptionExtractor = RootCauseExtractor.of[TimeoutException]



  def get(url: String) = Action.async { implicit request =>
    val futureResponse = for {
      namedResource <- wsClient.get( URI(url) )
    } yield createResultForNamedResource(namedResource)
    futureResponse recover {
      case RemoteException(msg, headers) => errorResult(ExpectationFailed(msg))
      case MissingParserException(err) => errorResult(ExpectationFailed(err))
      case ParserException(msg,err) => errorResult(ExpectationFailed(msg+"\n"+err))
      case LocalException(msg) => errorResult(ExpectationFailed(msg))
      // TODO these low level exceptions should rather be handled at the client level and expose other exceptions
      // because exception introspection (looking for root cause) is bad and create coupling
      case UnresolvedAddressExceptionExtractor(e) => errorResult(BadGateway(Throwables.getStackTraceAsString(e)))
      case NoRouteToHostExceptionExtractor(e) => errorResult(BadGateway(Throwables.getStackTraceAsString(e)))
      case ConnectExceptionExtractor(e) => errorResult(GatewayTimeout(Throwables.getStackTraceAsString(e)))
      case TimeoutExceptionExtractor(e) => errorResult(GatewayTimeout(Throwables.getStackTraceAsString(e)))
      case e: Exception => errorResult(InternalServerError(Throwables.getStackTraceAsString(e)))
    }
  }

  private def createResultForNamedResource(namedResource: NamedResource[Rdf])(implicit request: PlayRequest[AnyContent]): SimpleResult = {
    writerFor(request)(writerSelector).map { writer =>
      namedResource match {
        case ldpr: LDPR[Rdf] => createResultForLDPR(ldpr,writer)
        case other => UnsupportedMediaType(s"Cannot proxy non rdf resources at present. Request sent ${request.headers.get(play.api.http.HeaderNames.ACCEPT)}")
      }
    }.getOrElse(UnsupportedMediaType(s"could not find RDF type of resource at remote location ${request.headers.get(play.api.http.HeaderNames.ACCEPT)}"))
  }


  private def createResultForLDPR(ldpr: LDPR[Rdf], writer: Writer[Rdf#Graph, Any])(implicit request: PlayRequest[AnyContent]): SimpleResult = {
    val hdrs = request.headers.toSimpleMap - "ContentType"
    //todo: this needs to be refined a lot, and thought through quite a lot more carefully
    val corsHeaders = if (!hdrs.contains("Access-Control-Allow-Origin")) {
      val origin = request.headers.get("Origin")
      hdrs + ("Access-Control-Allow-Origin" -> origin.getOrElse("*"))
    } else {
      hdrs
    }
    result(203, writer)(ldpr.graph).withHeaders(corsHeaders.toSeq: _*)
  }




}





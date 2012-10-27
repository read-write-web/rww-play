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

import play.api.mvc.{Controller, Action}
import java.net.URL
import org.w3.banana.{WriterSelector, RDF}
import org.www.readwriteweb.play.PlayWriterBuilder._
import akka.util.Timeout
import org.www.play.remote._
import org.www.play.remote.LocalException
import scalaz.Success
import scalaz.Failure
import org.www.play.remote.RemoteException
import akka.actor.ActorSystem
import org.www.play.rdf.IterateeSelector

/**
 * A <a href="http://www.w3.org/TR/cors/">CORS</a> proxy that allows a client to fetch remote RDF
 * resources that do not have the required CORS headers.
 *
 * Currently this only permits GET operations. It is unclear what PUT, POST, DELETE operations would
 * look like for a CORS proxy.
 *
 */
class CORSProxy[Rdf<:RDF](val graphSelector: IterateeSelector[Rdf#Graph], writerSelector: WriterSelector[Rdf#Graph]) extends Controller {

  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = system.dispatcher
  // turn a header map into an (att,val) sequence
  private implicit def sequentialise(headers: Map[String,Seq[String]]) = headers.toSeq.flatMap(pair=>pair._2.map(v=>(pair._1,v)))
  implicit val fetcher = new GraphFetcher[Rdf](graphSelector)

  def get(url: String) = Action {
    request =>
      System.out.println("in CORSProxy.get("+url+")")
      val iri = new URL(url)
      implicit val timeout = Timeout(10 * 1000)
      val promiseResult = for {
        answer <- fetcher.corsFetch(iri, request.headers.toMap).inner
      } yield {
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




//class JenaServiceConnection extends ServiceConnection[Jena](JenaAsync.graphIterateeSelector)


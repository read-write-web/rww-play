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

package org.w3.play.remote

import java.net.URL
import org.w3.banana._
import org.w3.play.rdf.IterateeSelector
import play.api.libs.iteratee.{Input, Done, Iteratee}
import play.api.libs.ws.WS
import scala.Some
import play.api.libs.ws.ResponseHeaders
import scalaz.{Failure, Success, Validation}
import org.w3.banana.jena.Jena
import org.w3.play.rdf.jena.JenaAsync
import concurrent.{ExecutionContext, Future}

/**
 * Fetches graphs remotely on the web
 * @param graphSelector graph transformers
 * @tparam Rdf
 */
class GraphFetcher[Rdf<: RDF](val graphSelector: IterateeSelector[Rdf#Graph])(implicit ec: ExecutionContext)  {

  /**
   * @param url
   * @param headers
   * @return
   */
  def corsFetch(url: URL, headers: Map[String, Seq[String]]):
   BananaFuture[GraphNHeaders[Rdf]] = {
    System.out.println("in ServiceConnection Fetching " + url)
    val hdrs = for (key <- (headers - "Accept").keys;
                    value <- (headers(key))) yield (key, value)
    val promiseIteratee = fetch(url, hdrs)
    //question: should I send back a Validation[Graph] or a Promise[Graph] or the complex Promise[Itera...] that I do
    //also this assumes that the method in flatMap is only run when the iteratee is finished. That is not so clear
    //from the documentation at the time of writing (2012/08/01)
    val promiseResult: Future[Validation[FetchException, GraphNHeaders[Rdf]]] = promiseIteratee.flatMap(_.run)
    promiseResult
  }

  //request for a URL
  def fetch(url: URL): Future[Validation[FetchException, GraphNHeaders[Rdf]]] = {
    val promiseIteratee = fetch(url, Iterable.empty)
    //question: should I send back a Validation[Graph] or a Future[Graph] or the complex Future[Itera...] that I do
    //also this assumes that the method in flatMap is only run when the iteratee is finished. That is not so clear
    //from the documentation at the time of writing (2012/08/01)
    val promiseResult: Future[Validation[FetchException, GraphNHeaders[Rdf]]] = promiseIteratee.flatMap(_.run)
    promiseResult
  }

  /**
   *  note we prefer rdf/xml and turtle over html, as html does not always contain rdfa, and we prefer those over n3,
   * as we don't have a full n3 parser. Better would be to have a list of available parsers for whatever rdf framework is
   * installed (some claim to do n3 when they only really do turtle)
   * we can't currently accept * as we don't have GRDDL implemented
   * @param url
   * @param headers
   * @return
   */
  def fetch(url: URL, headers: Iterable[(String,String)] ):
    Future[Iteratee[Array[Byte], Validation[FetchException, GraphNHeaders[Rdf]]]] =
      WS.url(url.toExternalForm)
        .withHeaders(headers.toSeq:_*)
        .withHeaders("Accept" -> "application/rdf+xml,text/turtle,application/xhtml+xml;q=0.8,text/html;q=0.7,text/n3;q=0.2")
        .get {
        response: ResponseHeaders =>
          import MimeType._

          // get the new location - in case of 301 only
          // an unintuitive twist in HTTP, see the discussion on the WebID mailing list
          // http://lists.w3.org/Archives/Public/public-webid/2012Apr/0004.html
          // and the bug report on the httpbis mailing list
          // http://trac.tools.ietf.org/wg/httpbis/trac/ticket/154
          // and the definition of httpbis
          val newLocation = if (response.status == 301)
            response.headers.get("Content-Location").flatMap(_.headOption).map {
              loc => new URL(url, loc)
            } else None
          val loc = newLocation orElse Some(new URL(url.getProtocol, url.getAuthority, url.getPort, url.getPath))

          //an improvement could be to guess the content type by looking at the first array of bytes
          response.headers.get("Content-Type") match {
            case Some(headers) =>
              headers.headOption.map {
                value => MimeType(normalize(extract(value)))
              } match {
                case Some(graphSelector(iteratee)) => iteratee(loc).mapDone{
                  case Success(g) => Success(GraphNHeaders(g,response))
                  case Failure(e) => Failure(WrappedException("had problems parsing document returned by server",e))
                }
                case None => Done(Failure(LocalException("no Iteratee/parser for Content-Type " + headers)), Input.Empty)
              }
            case None => Done(Failure(new RemoteException("no Content-Type header specified in response returned by server ", response)), Input.Empty)
          }
      }


}

case class GraphNHeaders[Rdf<:RDF](graph: Rdf#Graph, remote: ResponseHeaders)

trait FetchException extends BananaException
case class RemoteException(msg: String, remote: ResponseHeaders) extends FetchException
case class LocalException(msg: String) extends FetchException
case class WrappedException(msg: String, e: Exception) extends FetchException


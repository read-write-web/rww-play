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
import org.www.play.rdf.IterateeSelector
import play.api.mvc.{RawBuffer, RequestHeader, BodyParser}
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input.Empty
import scala.Left
import scala.Right
import scala.Some
import java.net.URL
import util.{Success, Failure}

/**
 * a RWW bodyParser, like all body parsers, parses content sent from the client
 * to the server. This body parser parses all RDF content: Graphs and SPARQL queries
 * and the rest is passed on as Binary content.
 *
 * @param ops
 * @param sparqlOps
 * @param graphSelector
 * @param sparqlSelector
 * @tparam Rdf
 */
class RwwBodyParser[Rdf <: RDF]
(val ops: RDFOps[Rdf],
 val sparqlOps: SparqlOps[Rdf],
 val graphSelector: IterateeSelector[Rdf#Graph],
 val sparqlSelector: IterateeSelector[Rdf#Query])
  extends BodyParser[RwwContent] {

  import play.api.mvc.Results._
  import play.api.mvc.BodyParsers.parse

  def apply(rh: RequestHeader) =  {
    if (rh.method == "GET" || rh.method == "HEAD") Done(Right(emptyContent), Empty)
    else rh.contentType.map { str =>
      MimeType(str) match {
        case sparqlSelector(iteratee) => iteratee().mapDone {
          case Failure(e) => Left(BadRequest("could not parse query "+e))
          case Success(sparql) => Right(QueryRwwContent(sparql))
        }
        case graphSelector(iteratee) => iteratee(Some(new URL("http://localhost:9000/" + rh.uri))).mapDone {
          case Failure(e) => Left(BadRequest("cought " + e))
          case Success(graph) => Right(GraphRwwContent(graph))
        }
        case mime: MimeType => parse.raw(rh).mapDone {
          _.right.map(rb => BinaryRwwContent(rb, mime.mime))
        }
      }
    }.getOrElse {
      Done(Left(BadRequest("missing Content-type header. Please set the content type in the HTTP header of your message ")),
        Empty)
    }
  }


  override def toString = "BodyParser(" + ops.toString + ")"

}


trait RwwContent

case object emptyContent extends RwwContent

case class GraphRwwContent[Rdf<:RDF](graph: Rdf#Graph) extends RwwContent

case class QueryRwwContent[Rdf<:RDF](query: Rdf#Query) extends RwwContent

case class BinaryRwwContent(binary: RawBuffer, mime: String) extends RwwContent






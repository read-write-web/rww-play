package org.w3.readwriteweb.play

import org.w3.banana._
import jena.{JenaSPARQLOperations, JenaOperations, Jena}
import org.w3.play.rdf.IterateeSelector
import play.api.mvc.{RawBuffer, RequestHeader, BodyParser}
import play.api.libs.iteratee.Done
import play.api.libs.iteratee.Input.Empty
import scala.Left
import scala.Right
import scala.Some
import java.net.URL
import org.w3.play.rdf.jena.{JenaSparqlQueryIteratee, JenaAsync}

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
(val ops: RDFOperations[Rdf],
 val sparqlOps: SPARQLOperations[Rdf],
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
          case Left(e) => Left(BadRequest("could not parse query "+e))
          case Right(sparql) => Right(QueryRwwContent(sparql))
        }
        case graphSelector(iteratee) => iteratee(Some(new URL("http://localhost:9000/" + rh.uri))).mapDone {
          case Left(e) => Left(BadRequest("cought " + e))
          case Right(graph) => Right(GraphRwwContent(graph))
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


object jenaRwwBodyParser extends
RwwBodyParser[Jena](JenaOperations, JenaSPARQLOperations,
  JenaAsync.graphIterateeSelector, JenaSparqlQueryIteratee.sparqlSelector )



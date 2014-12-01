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

import java.io.File
import java.net.URL
import java.nio.file.{Files, Path}

import org.w3.banana._
import org.w3.banana.io.MimeType
import play.api.libs.Files.TemporaryFile
import play.api.libs.iteratee.Input.Empty
import play.api.libs.iteratee.{Done, Iteratee}
import play.api.mvc.{BodyParsers, BodyParser, RequestHeader, Result}
import rww.play.rdf.IterateeSelector

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}

/**
 * a RWW bodyParser, like all body parsers, parses content sent from the client
 * to the server. This body parser parses all RDF content: Graphs and SPARQL queries
 * and the rest is passed on as Binary content.
 *
 * @param ops
 * @param sparqlOps
 * @param graphSelector
 * @tparam Rdf
 */
//todo: pass the base URL
class RwwBodyParser[Rdf <: RDF](base: URL, tmpDir: Path)(implicit ops: RDFOps[Rdf],
                                sparqlOps: SparqlOps[Rdf],
                                graphSelector: IterateeSelector[Rdf#Graph],
                                sparqlSelector: IterateeSelector[Rdf#Query],
                                sparqlUpdateSelector: IterateeSelector[Rdf#UpdateQuery],
                                ec: ExecutionContext)
  extends BodyParser[RwwContent] {

  import play.api.mvc.BodyParsers.parse
  import play.api.mvc.Results._


  def apply(rh: RequestHeader): Iteratee[Array[Byte],Either[Result,RwwContent]] = {

    def messageAndCause(e: Throwable): String = e.getMessage + " due to " + e.getCause

    if (rh.method == "GET" || rh.method == "HEAD" || rh.method == "OPTIONS")
      Done(Right(emptyContent), Empty)  //todo: should this consume all the input?
    else if (!rh.headers.get("Content-Length").exists(Integer.parseInt(_) > 0)) {
      Done(Right(emptyContent), Empty)
    } else {
      rh.contentType.flatMap(MimeType.parse(_)) map { mime: MimeType =>
        rh.method match {
          case "SEARCH" => mime match {
            case sparqlSelector(iteratee) => iteratee(Option(new URL(base, rh.path))).map {
              case Failure(e) => Left(BadRequest(messageAndCause(e)))
              case Success(sparql) => Right(QueryRwwContent(sparql))
            }
            case other => Iteratee.consume[Array[Byte]]().map(_ =>
              Left(UnsupportedMediaType(
                "The SEARCH verb only allows SPARQL Queries at present. You sent " + other
              ))
            )
          }
          case "PATCH" => mime match {
            case sparqlUpdateSelector(iteratee) => iteratee(Option(new URL(base, rh.path))).map {
              case Failure(e) => Left(BadRequest(messageAndCause(e)))
              case Success(update) => Right(PatchRwwContent(update))
            }
            case other => Iteratee.consume[Array[Byte]]().map(_ =>
              Left(UnsupportedMediaType(
                "The PATCH verb only allows SPARQL PATCH Queries at present. You sent " + other
              ))
            )
          }
          case "PUT" | "POST" => mime match {
            case graphSelector(iteratee) => iteratee().map {
              case Failure(e) => Left(BadRequest(messageAndCause(e)))
              case Success(graph) => Right(GraphRwwContent(graph))
            }
            //todo: it would nice not to have to go through temporary files, but be able to pass on the iteratee
            //todo: on systems where the result may be on a remote file system this would be very important.
            case mime: MimeType => {
              val tempFile = Files.createTempFile(tmpDir,"",".tmp")
              val parser = BodyParsers.parse.file(tempFile.toFile).map { tmpf =>
                 BinaryRwwContent(tmpf, mime)
              }
              parser(rh)
            }
          }
        }
      } getOrElse {
        Done(Left(BadRequest("missing Content-type header. Please set the content type in the HTTP header of your message ")),
          Empty)
      }
    }
  }


  override def toString = "BodyParser(" + ops.toString + ")"

}


trait RwwContent

case object emptyContent extends RwwContent

case class GraphRwwContent[Rdf<:RDF](graph: Rdf#Graph) extends RwwContent

case class QueryRwwContent[Rdf<:RDF](query: Rdf#Query) extends RwwContent

case class PatchRwwContent[Rdf<:RDF](query: Rdf#UpdateQuery) extends RwwContent

case class BinaryRwwContent(file: File, mime: MimeType) extends RwwContent






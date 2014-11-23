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

package rww.play.rdf

import java.io.ByteArrayOutputStream
import java.net.URL

import org.w3.banana.{RDF, RDFOps, SparqlOps}
import play.api.libs.iteratee.Iteratee
import rww.ldp.ParserException

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Try}

/**
 * Iteratee for reading in SPARQL Queries
 * @author Henry Story
 */
class SparqlUpdateIteratee[Rdf<:RDF, +SyntaxType]
(implicit sparqlOps: SparqlOps[Rdf], ops: RDFOps[Rdf])
  extends RDFIteratee[Rdf#UpdateQuery, SyntaxType] {

  import sparqlOps._
  /**
   *
   * @param loc the location of the document to evaluate relative URLs (this will not make a connection)
   * @return an iteratee to process a streams of bytes that will parse to an RDF#Graph
   */
  def apply(loc: Option[URL])(implicit ec: ExecutionContext): Iteratee[Array[Byte], Try[Rdf#UpdateQuery]] =
    Iteratee.fold[Array[Byte],ByteArrayOutputStream](new ByteArrayOutputStream()){
    (stream,bytes) => {stream.write(bytes); stream }
  } map { stream =>
      val query = loc.map(b => s"base <${b.toString}> \n").getOrElse("") + new String(stream.toByteArray, "UTF-8") //todo, where do we get UTF-8?
      //todo: https://github.com/w3c/banana-rdf/issues/76
      parseUpdate(query).recoverWith {
        case e: Throwable => Failure(ParserException("failed in parsing <update>\n" + query + "\n</update>", e))
      }
    }

}




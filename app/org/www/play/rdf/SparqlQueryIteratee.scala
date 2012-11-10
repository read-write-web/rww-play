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

package org.www.play.rdf

import org.w3.banana.{SparqlOps, RDF}
import java.net.URL
import play.api.libs.iteratee.{Error, Done, Iteratee}
import java.io.ByteArrayOutputStream
import util.{Try, Failure, Success}

/**
 * Iteratee for reading in SPARQL Queries
 * @author Henry Story
 */
class SparqlQueryIteratee[Rdf<:RDF, +SyntaxType]
(implicit ops: SparqlOps[Rdf])
  extends RDFIteratee[Rdf#Query, SyntaxType] {
  /**
   *
   * @param loc the location of the document to evaluate relative URLs (this will not make a connection)
   * @return an iteratee to process a streams of bytes that will parse to an RDF#Graph
   */
  def apply(loc: Option[URL]): Iteratee[Array[Byte], Try[Rdf#Query]] =
    Iteratee.fold[Array[Byte],ByteArrayOutputStream](new ByteArrayOutputStream()){
    (stream,bytes) => {stream.write(bytes); stream }
  } mapDone { stream =>
      val query = new String(stream.toByteArray,"UTF-8")//todo, where do we get UTF-8?
      ops.Query(query)
    }
}




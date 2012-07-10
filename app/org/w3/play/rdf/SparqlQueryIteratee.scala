package org.w3.play.rdf

import org.w3.banana.{SPARQLOperations, RDF}
import java.net.URL
import play.api.libs.iteratee.Iteratee
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

/**
 * Iteratee for reading in SPARQL Queries
 * @author Henry Story
 */
class SparqlQueryIteratee[Rdf<:RDF, +SyntaxType]
(implicit ops: SPARQLOperations[Rdf])
  extends RDFIteratee[Rdf#Query, SyntaxType] {
  /**
   *
   * @param loc the location of the document to evaluate relative URLs (this will not make a connection)
   * @return an iteratee to process a streams of bytes that will parse to an RDF#Graph
   */
  def apply(loc: Option[URL]) = Iteratee.fold[Array[Byte],ByteArrayOutputStream](new ByteArrayOutputStream()){
    (stream,bytes) => {stream.write(bytes); stream }
  }.mapDone{
    stream =>
      val query = new String(stream.toByteArray,"UTF-8")//todo, where do we get UTF-8?
      System.out.println("received query"+query)
      ops.Query(query).either
  }
}




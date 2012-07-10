package org.w3.play.rdf

import java.net.URL
import play.api.libs.iteratee.Iteratee
import java.io.{IOException, PipedOutputStream, PipedInputStream}
import play.api.libs.concurrent.Akka
import org.w3.banana._
import scalaz.Validation


/**
 * RDF Iteratee to parse graphs, based on blocking parsers. This iteratee
 * will run each parser in its own thread. So this iteratee is not very
 * efficient. Beter find Iteratees that don't work with blocking parsers

 * @param ops RDF operations
 * @param reader the RDFReader this is based on
 * @tparam Rdf the Rdf implementation this is based on
 * @tparam SyntaxType the mime type parsed
 */
class BlockingRDFIteratee[Rdf <: RDF, +SyntaxType]
(implicit ops: RDFOperations[Rdf], reader: RDFReader[Rdf, SyntaxType])
  extends RDFIteratee[Rdf#Graph, SyntaxType] {

  import play.api.Play.current
  import webid.Logger.log

  new net.rootdev.javardfa.jena.RDFaReader
  //import shellac's rdfa parser
  type SIteratee[Result] = Iteratee[Array[Byte], Either[Exception, Result]]

  def apply(loc: Option[URL] = None): SIteratee[Rdf#Graph] = {

    val in = new PipedInputStream()
    val out = new PipedOutputStream(in)
    val blockingIO = Akka.future {
      try {
        //http://www.youtube.com/watch?v=wMFqSjjnte0
        val graph: Validation[BananaException, Rdf#Graph] = reader.read(in, loc.map(_.toString).orNull)
        graph.either
      } finally {
        in.close()
      }
    }

    Iteratee.fold[Array[Byte], PipedOutputStream](out) {
      (out, bytes) => {
        out.write(bytes);
        out
      }
    }.mapDone {
      finished =>
        try {
          out.flush(); out.close()
        } catch {
          case e: IOException => log.warn("exception caught closing stream with " + loc, e)
        }
        blockingIO.await(5000).get //todo: should be settable (but very likely much shorter than 5 seconds, since io succeeded)
    }

  }

}







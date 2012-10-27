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

import java.net.URL
import play.api.libs.iteratee.Iteratee
import java.io.{IOException, PipedOutputStream, PipedInputStream}
import org.w3.banana._
import scalaz.Validation
import scalax.io.Resource
import concurrent.{ExecutionContext, Future}
import util.FutureValidation
import concurrent.util.Duration
import java.util.concurrent.TimeUnit


/**
 * RDF Iteratee to parse graphs, based on blocking parsers. This iteratee
 * will run each parser in its own thread. So this iteratee is not very
 * efficient. Better find Iteratees that don't work with blocking parsers

 * @param ops RDF operations
 * @param reader the RDFReader this is based on
 * @tparam Rdf the Rdf implementation this is based on
 * @tparam SyntaxType the mime type parsed
 */
class BlockingRDFIteratee[Rdf <: RDF, +SyntaxType](reader: RDFReader[Rdf, SyntaxType])
(implicit ops: RDFOps[Rdf], ec: ExecutionContext)
  extends RDFIteratee[Rdf#Graph, SyntaxType] {

  import webid.Logger.log


  //import shellac's rdfa parser:
  new net.rootdev.javardfa.jena.RDFaReader
  //import shellac's rdfa parser

  def apply(loc: Option[URL] = None): Iteratee[Array[Byte], Validation[Exception,Rdf#Graph]] = {

    val in = new PipedInputStream()
    val out = new PipedOutputStream(in)
    val blockingIO: BananaFuture[Rdf#Graph] = FutureValidation(Future {
      reader.read(Resource.fromInputStream(in), loc.map(_.toString).orNull)
    })

    Iteratee.fold1[Array[Byte], PipedOutputStream](out) {
      (out, bytes) => try {
        out.write(bytes);   //this is done synchronously on the thread - it should be fast, but should be tested
        Future.successful(out)
      } catch {
        case ioe: IOException => Future.failed(ioe)
      }
    }.mapDone {
      finished =>
        try {
          out.flush(); out.close()
        } catch {
          case e: IOException => log.warn("exception caught closing stream with " + loc, e)
        }
        blockingIO.await(Duration(2000,TimeUnit.MILLISECONDS)) //ugly!
    }
  }

}







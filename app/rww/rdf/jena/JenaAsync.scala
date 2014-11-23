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

package rww.play.rdf.jena

import org.w3.banana.io.RDFXML
import org.w3.banana.jena.Jena
import rww.play.rdf.{IterateeSelector, RDFIteratee}
import concurrent.ExecutionContext


class JenaAsync(implicit ec: ExecutionContext) {

  implicit val asyncIteratee: RDFIteratee[Jena#Graph, RDFXML] = JenaRdfXmlAsync
  val jenaBlockingRDFIteratee = new JenaBlockingRDFIteratee

  val rdfxmlSelector = try {
    IterateeSelector[Jena#Graph, RDFXML]
  } catch {
    case e: Throwable => { e.printStackTrace(System.out); throw e}
  }
    /**
     * The most efficient Async/Sync selectors for Jena, currently (please update)
     * //currently rdfxml parsing is async, but not turtle
     */
    implicit val graphIterateeSelector: IterateeSelector[Jena#Graph] = try {
      rdfxmlSelector combineWith jenaBlockingRDFIteratee.turtleSelector
    } catch {
      case e: Throwable => {e.printStackTrace(System.out); throw e}
    }



}
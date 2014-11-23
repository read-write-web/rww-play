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

import org.w3.banana.RDF
import org.w3.banana.jena.Jena
import rww.play.rdf.{IterateeSelector, RDFIteratee, SparqlQueryIteratee}

import scala.concurrent.ExecutionContext

object JenaSparqlQueryIteratee {
 import org.w3.banana.jena.Jena._

 implicit def apply(implicit ec: ExecutionContext): RDFIteratee[Jena#Query,RDF#Query] =
  new SparqlQueryIteratee[Jena,RDF#Query]

 def sparqlSelector(implicit ec: ExecutionContext) = IterateeSelector[Jena#Query, RDF#Query]

}
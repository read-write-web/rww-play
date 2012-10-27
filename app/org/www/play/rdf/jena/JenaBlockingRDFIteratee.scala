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

package org.www.play.rdf.jena

import org.w3.banana.jena.{Jena, JenaOperations}
import org.w3.banana.{Syntax, Turtle, RDFXML, RDFReader}
import org.www.play.rdf.{IterateeSelector, RDFIteratee, BlockingRDFIteratee}
import concurrent.ExecutionContext

/**
 *
 * Date: 27/06/2012
 */

class JenaBlockingRDFIteratee(implicit ec: ExecutionContext)  {
  implicit val ops = JenaOperations
  import org.w3.banana.jena.Jena.{turtleReader,rdfxmlReader}

  def apply[SyntaxType](reader: RDFReader[Jena, SyntaxType]) =
    new BlockingRDFIteratee[Jena,SyntaxType](reader)

  implicit val RDFXMLIteratee: RDFIteratee[Jena#Graph,RDFXML] = apply[RDFXML](rdfxmlReader)
  implicit val TurtleIteratee: RDFIteratee[Jena#Graph,Turtle] = apply[Turtle](turtleReader)

  val rdfxmlSelector = IterateeSelector[Jena#Graph, RDFXML](Syntax.RDFXML,RDFXMLIteratee)
  val turtleSelector = IterateeSelector[Jena#Graph, Turtle](Syntax.Turtle,TurtleIteratee)

  implicit val BlockingIterateeSelector: IterateeSelector[Jena#Graph] =
    rdfxmlSelector combineWith turtleSelector


}
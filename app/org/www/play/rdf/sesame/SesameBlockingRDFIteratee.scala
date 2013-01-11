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

package org.www.play.rdf.sesame

import org.w3.banana.{Syntax, Turtle, RDFXML, RDFReader}
import org.www.play.rdf.{IterateeSelector, RDFIteratee, BlockingRDFIteratee}
import concurrent.ExecutionContext
import org.w3.banana.sesame.{SesameOperations, Sesame}

/**
 *
 * Date: 09/01/2013
 */

class SesameBlockingRDFIteratee(implicit ec: ExecutionContext)  {
  implicit val ops = SesameOperations
  import org.w3.banana.sesame.Sesame.{turtleReader,rdfxmlReader}

  def apply[SyntaxType](reader: RDFReader[Sesame, SyntaxType]) =
    new BlockingRDFIteratee[Sesame,SyntaxType](reader)

  implicit val RDFXMLIteratee: RDFIteratee[Sesame#Graph,RDFXML] = apply[RDFXML](rdfxmlReader)
  implicit val TurtleIteratee: RDFIteratee[Sesame#Graph,Turtle] = apply[Turtle](turtleReader)

  val rdfxmlSelector = IterateeSelector[Sesame#Graph, RDFXML](Syntax.RDFXML,RDFXMLIteratee)
  val turtleSelector = IterateeSelector[Sesame#Graph, Turtle](Syntax.Turtle,TurtleIteratee)

  implicit val BlockingIterateeSelector: IterateeSelector[Sesame#Graph] =
    rdfxmlSelector combineWith turtleSelector


}
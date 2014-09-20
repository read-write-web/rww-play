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

package rww.play.rdf.plantain

import org.w3.banana._
import org.w3.banana.plantain.Plantain
import org.w3.banana.sesame.SesameOperations
import rww.play.rdf.{BlockingRDFIteratee, IterateeSelector, RDFIteratee}

import scala.concurrent.ExecutionContext

/**
 * As Plantain does not have its own serializers we go through Sesame for the moment.
 * Slow.
 *
 * Date: 09/01/2013
 */

class PlantainBlockingRDFIteratee(implicit ec: ExecutionContext)  {
  implicit val ops = SesameOperations
  import org.w3.banana.plantain.Plantain.{turtleReader,rdfxmlReader}

  def apply[SyntaxType](reader: RDFReader[Plantain, SyntaxType]) =
    new BlockingRDFIteratee[Plantain,SyntaxType](reader)

  implicit val RDFXMLIteratee: RDFIteratee[Plantain#Graph,RDFXML] = apply[RDFXML](rdfxmlReader)
  implicit val TurtleIteratee: RDFIteratee[Plantain#Graph,Turtle] = apply[Turtle](turtleReader)

  val rdfxmlSelector = IterateeSelector[Plantain#Graph, RDFXML](Syntax.RDFXML,RDFXMLIteratee)
  val turtleSelector = IterateeSelector[Plantain#Graph, Turtle](Syntax.Turtle,TurtleIteratee)

  implicit val BlockingIterateeSelector: IterateeSelector[Plantain#Graph] =
    turtleSelector combineWith rdfxmlSelector

}
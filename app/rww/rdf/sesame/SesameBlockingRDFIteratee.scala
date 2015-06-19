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

package rww.play.rdf.sesame

import org.w3.banana.io._
import org.w3.banana.sesame.Sesame
import rww.play.rdf.{BlockingRDFIteratee, IterateeSelector, RDFIteratee}

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 *
 * Date: 09/01/2013
 */

class SesameBlockingRDFIteratee(implicit ec: ExecutionContext)  {
  implicit val ops = Sesame.ops
  import org.w3.banana.sesame.Sesame.{rdfXMLReader, turtleReader,jsonldReader, ntriplesReader}

  def apply[SyntaxType](reader: RDFReader[Sesame, Try, SyntaxType]) =
    new BlockingRDFIteratee[Sesame,SyntaxType](reader)

  implicit val RDFXMLIteratee: RDFIteratee[Sesame#Graph,RDFXML] = apply[RDFXML](rdfXMLReader)
  implicit val TurtleIteratee: RDFIteratee[Sesame#Graph,Turtle] = apply[Turtle](turtleReader)
  implicit val JSonLDIteratee: RDFIteratee[Sesame#Graph,JsonLd] = apply[JsonLd](jsonldReader)
  implicit val NTriplesIteratee: RDFIteratee[Sesame#Graph,NTriples] = apply[NTriples](ntriplesReader)

  val rdfxmlSelector = IterateeSelector[Sesame#Graph, RDFXML](Syntax.RDFXML,RDFXMLIteratee)
  val turtleSelector = IterateeSelector[Sesame#Graph, Turtle](Syntax.Turtle,TurtleIteratee)
  val jsonLDSelector = IterateeSelector[Sesame#Graph, JsonLd](Syntax.JsonLd,JSonLDIteratee)
  val ntriplesSelector = IterateeSelector[Sesame#Graph, NTriples](Syntax.NTriples,NTriplesIteratee)

  implicit val BlockingIterateeSelector: IterateeSelector[Sesame#Graph] =
    rdfxmlSelector combineWith turtleSelector combineWith jsonLDSelector combineWith ntriplesSelector


}
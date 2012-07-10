package org.w3.play.rdf.jena

import org.w3.banana.jena.Jena
import org.w3.banana.RDFXML
import org.w3.play.rdf.{IterateeSelector, RDFIteratee}


object JenaAsync {

  implicit val asyncIteratee: RDFIteratee[Jena#Graph, RDFXML] = JenaRdfXmlAsync

  val rdfxmlSelector = IterateeSelector[Jena#Graph, RDFXML]

  /**
   * The most efficient Async/Sync selectors for Jena, currently (please update)
   * //currently rdfxml parsing is async, but not turtle
   */
  implicit val graphIterateeSelector: IterateeSelector[Jena#Graph] =
    rdfxmlSelector combineWith JenaBlockingRDFIteratee.turtleSelector


}
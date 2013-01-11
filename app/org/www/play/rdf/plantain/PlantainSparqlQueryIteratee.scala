package org.www.play.rdf.plantain

import org.www.play.rdf.{IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.SparqlQuery
import org.w3.banana.plantain.Plantain

/**
 * User: hjs
 * Date: 10/01/2013
 */
object PlantainSparqlQueryIteratee {
  implicit val apply = new SparqlQueryIteratee[Plantain, SparqlQuery]

  val sparqlSelector = IterateeSelector[Plantain#Query, SparqlQuery]
}

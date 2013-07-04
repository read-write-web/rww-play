package org.www.play.rdf.plantain

import org.www.play.rdf.{SparqlUpdateIteratee, IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.{SparqlUpdate, SparqlQuery}
import org.w3.banana.plantain.Plantain

/**
 * User: hjs
 * Date: 10/01/2013
 */
object PlantainSparqlUpdateIteratee {
  implicit val apply = new SparqlUpdateIteratee[Plantain, SparqlUpdate]

  val sparqlSelector = IterateeSelector[Plantain#UpdateQuery, SparqlUpdate]
}

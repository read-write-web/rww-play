package org.www.play.rdf.plantain

import org.www.play.rdf.{SparqlUpdateIteratee, IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.{SparqlUpdate, SparqlQuery}
import org.w3.banana.plantain.Plantain
import scala.concurrent.ExecutionContext

/**
 * User: hjs
 * Date: 10/01/2013
 */
object PlantainSparqlUpdateIteratee {
  implicit def apply(implicit ec: ExecutionContext) = new SparqlUpdateIteratee[Plantain, SparqlUpdate]

  def sparqlSelector(implicit ec: ExecutionContext) = IterateeSelector[Plantain#UpdateQuery, SparqlUpdate]
}

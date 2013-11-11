package rww.play.rdf.plantain

import rww.play.rdf.{IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.SparqlQuery
import org.w3.banana.plantain.Plantain
import scala.concurrent.ExecutionContext

/**
 * User: hjs
 * Date: 10/01/2013
 */
object PlantainSparqlQueryIteratee {
  implicit def apply(implicit ec: ExecutionContext) = new SparqlQueryIteratee[Plantain, SparqlQuery]

  def sparqlSelector(implicit ec: ExecutionContext) = IterateeSelector[Plantain#Query, SparqlQuery]
}

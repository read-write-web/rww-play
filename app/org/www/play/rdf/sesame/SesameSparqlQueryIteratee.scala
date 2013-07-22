package org.www.play.rdf.sesame

import org.www.play.rdf.{IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.jena.Jena
import org.w3.banana.SparqlQuery
import org.w3.banana.sesame.Sesame
import scala.concurrent.ExecutionContext

/**
 * Date: 10/01/2013
 * Time: 19:43
 */
object SesameSparqlQueryIteratee {

  implicit def apply(implicit ec: ExecutionContext) = new SparqlQueryIteratee[Sesame, SparqlQuery]

  def sparqlSelector(implicit ec: ExecutionContext) = IterateeSelector[Sesame#Query, SparqlQuery]

}
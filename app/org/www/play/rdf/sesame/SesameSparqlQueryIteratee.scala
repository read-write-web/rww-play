package org.www.play.rdf.sesame

import org.www.play.rdf.{IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.jena.Jena
import org.w3.banana.SparqlQuery
import org.w3.banana.sesame.Sesame

/**
 * Date: 10/01/2013
 * Time: 19:43
 */
object SesameSparqlQueryIteratee {

  implicit val apply = new SparqlQueryIteratee[Sesame, SparqlQuery]

  val sparqlSelector = IterateeSelector[Sesame#Query, SparqlQuery]

}
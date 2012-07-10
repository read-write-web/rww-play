package org.w3.play.rdf.jena

import org.w3.play.rdf.{IterateeSelector, SparqlQueryIteratee}
import org.w3.banana.jena.Jena
import org.w3.banana.SparqlQuery

object JenaSparqlQueryIteratee {

 implicit val apply = new SparqlQueryIteratee[Jena, SparqlQuery]

 val sparqlSelector = IterateeSelector[Jena#Query, SparqlQuery]

}
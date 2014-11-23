package rww.play.rdf.sesame

import org.w3.banana.RDF
import org.w3.banana.sesame.Sesame
import rww.play.rdf._

import scala.concurrent.ExecutionContext

object SesameSparqlQueryIteratee {

  import org.w3.banana.sesame.Sesame._

  implicit def apply(implicit ec: ExecutionContext): RDFIteratee[Sesame#Query,RDF#Query] =
    new SparqlQueryIteratee[Sesame, RDF#Query]

  def sparqlSelector(implicit ec: ExecutionContext) = IterateeSelector[Sesame#Query,RDF#Query]

}

object SesameSparqlUpdateIteratee {

  import org.w3.banana.sesame.Sesame._

  implicit def apply(implicit ec: ExecutionContext): RDFIteratee[Sesame#UpdateQuery,RDF#UpdateQuery] =
    new SparqlUpdateIteratee[Sesame, RDF#UpdateQuery]

  def sparqlSelector(implicit ec: ExecutionContext) =
    IterateeSelector[Sesame#UpdateQuery, RDF#UpdateQuery]
}

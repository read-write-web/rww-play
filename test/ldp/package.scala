package test

import akka.util.Timeout
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import org.w3.banana._
import org.w3.banana.plantain.model.URI
import org.w3.banana.plantain.{PlantainLDPatch, LDPatch, Plantain}
import rww.ldp.{WSClient, WebClient}
import rww.play.rdf.IterateeSelector
import rww.play.rdf.plantain.{PlantainSparqlUpdateIteratee, PlantainSparqlQueryIteratee, PlantainBlockingRDFIteratee}
import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 * Created by hjs on 10/01/2014.
 */
package object ldp {
  type Rdf = Plantain

  implicit val executorService: ExecutionContext = ExecutionContext.Implicits.global

  implicit val ops = Plantain.ops
  implicit val sparqlOps = Plantain.sparqlOps
  val blockingIteratee = new PlantainBlockingRDFIteratee

  implicit val writerSelector : RDFWriterSelector[Rdf] =
    RDFWriterSelector[Rdf, Turtle] combineWith RDFWriterSelector[Rdf, RDFXML]

  implicit val solutionsWriterSelector = Plantain.solutionsWriterSelector
  implicit val patch: LDPatch[Rdf,Try] = PlantainLDPatch
  implicit val timeout = Timeout(1,TimeUnit.SECONDS)

  //we don't have an iteratee selector for Plantain
  implicit val iterateeSelector: IterateeSelector[Rdf#Graph] = blockingIteratee.BlockingIterateeSelector
  implicit val sparqlSelector:  IterateeSelector[Rdf#Query] =  PlantainSparqlQueryIteratee.sparqlSelector
  implicit val sparqlupdateSelector:  IterateeSelector[Rdf#UpdateQuery] =  PlantainSparqlUpdateIteratee.sparqlSelector

  def dir: Path = Files.createTempDirectory("plantain" )
  val baseUri = URI.fromString("http://example.com/foo/")
}

package test.ldp

import java.nio.file.{Files, Path}

import controllers.SesameSetup
import org.w3.banana.io.{RDFReader, RDFWriter, Turtle}
import org.w3.banana.sesame.io.{SesameRDFWriterHelper, SesameTurtleReader}

import scala.util.Try

/**
 * Created by hjs on 10/01/2014.
 */
object TestSetup extends SesameSetup {

//  override implicit val timeout = Timeout(5,TimeUnit.SECONDS)
  val dir: Path = Files.createTempDirectory("plantain" )
  val baseUri: Rdf#URI = ops.URI("http://example.com/foo/")
  implicit val turtleWriter: RDFWriter[Rdf,Try, Turtle] = new SesameRDFWriterHelper().turtleWriter
  implicit val turtleReader: RDFReader[Rdf, Try, Turtle] = new SesameTurtleReader()

}

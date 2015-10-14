package test

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import controllers.RdfSetup
import org.w3.banana.io.{RDFReader, Turtle, RDFWriter}

import org.w3.banana.sesame.io.{SesameTurtleReader, SesameRDFWriterHelper}

import scala.util.Try

/**
 * Created by hjs on 10/01/2014.
 */
package object ldp {
  implicit val timeout = Timeout(1,TimeUnit.SECONDS)
  import RdfSetup._
  def dir: Path = Files.createTempDirectory("plantain" )
  def baseUri: Rdf#URI = RdfSetup.ops.URI("http://example.com/foo/")
  implicit val turtleWriter: RDFWriter[Rdf,Try, Turtle] = new SesameRDFWriterHelper().turtleWriter
  implicit val turtleReader: RDFReader[Rdf, Try, Turtle] = new SesameTurtleReader()
}

package org.www.play.rdf.plantain

import org.w3.banana.sesame.SesameSyntax
import java.io.{ Writer => jWriter }
import org.w3.banana._
import plantain.{Graph, Plantain}
import scalax.io.WriteCharsResource
import util.Try

object PlantainRDFWriter {

  def apply[T](implicit sesameSyntax: SesameSyntax[T], _syntax: Syntax[T]): RDFWriter[Plantain, T] =
    new RDFWriter[Plantain, T] {

      val syntax = _syntax

      def write[R <: jWriter](graph: Graph, wcr: WriteCharsResource[R], base: String): Try[Unit] = {
        Try {
          wcr.acquireAndGet { writer =>
            val sWriter = sesameSyntax.rdfWriter(writer, base)
            sWriter.startRDF()
            val it = graph.getStatements(null,null,null)
            while(it.hasNext)  sWriter.handleStatement(it.next)
            sWriter.endRDF()
          }
        }
      }


    }

  val rdfxmlWriter: RDFWriter[Plantain, RDFXML] = PlantainRDFWriter[RDFXML]

  val turtleWriter: RDFWriter[Plantain, Turtle] = PlantainRDFWriter[Turtle]

  implicit val selector: RDFWriterSelector[Plantain] =
    RDFWriterSelector[Plantain, RDFXML] combineWith RDFWriterSelector[Plantain, Turtle]

}

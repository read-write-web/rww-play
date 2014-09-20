package rww.play.rdf.plantain

import java.io.{Writer => jWriter}

import org.w3.banana._
import org.w3.banana.plantain.Plantain
import org.w3.banana.plantain.model.Graph
import org.w3.banana.sesame.SesameSyntax

import scala.util.Try
import scalax.io.WriteCharsResource

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

  implicit val htmlWriter: RDFWriter[Plantain, RDFaXHTML] = new RDFWriter[Plantain, RDFaXHTML] {
    override def syntax = Syntax.RDFaXHTML

    override def write[R <: jWriter](obj: Plantain#Graph, wcr: WriteCharsResource[R], base: String) =
      Try {
        wcr.acquireAndGet { writer =>
          writer.append(views.html.ldp.rdfToHtml().body)
        }
      }

  }

  implicit val rdfxmlWriter: RDFWriter[Plantain, RDFXML] = PlantainRDFWriter[RDFXML]

  implicit val turtleWriter: RDFWriter[Plantain, Turtle] = PlantainRDFWriter[Turtle]

  implicit val selector: RDFWriterSelector[Plantain] =
     RDFWriterSelector[Plantain,RDFaXHTML] combineWith RDFWriterSelector[Plantain, Turtle] combineWith RDFWriterSelector[Plantain, RDFXML]

}

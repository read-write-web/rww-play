package org.w3.play.rdf.jena

import org.w3.banana.jena.Jena
import org.w3.banana.RDFXML
import java.net.URL
import play.api.libs.iteratee.Iteratee
import play.api.libs.concurrent.Promise
import com.fasterxml.aalto.{AsyncInputFeeder, AsyncXMLStreamReader}
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.hp.hpl.jena.rdf.model.{ModelFactory, Model}
import patch.AsyncJenaParser
import com.hp.hpl.jena.rdf.arp.SAX2Model
import org.w3.play.rdf.RDFIteratee


object JenaRdfXmlAsync extends RDFIteratee[Jena#Graph, RDFXML] {

  def apply(loc: Option[URL]): Iteratee[Array[Byte], Either[Exception, Jena#Graph]] =
    Iteratee.fold2[Array[Byte], RdfXmlFeeder](new RdfXmlFeeder(loc)) {
      (feeder, bytes) =>
        try {
          //all this could be placed into a promise to be run by another actor if parsing takes too long
          if (feeder.feeder.needMoreInput()) {
            feeder.feeder.feedInput(bytes, 0, bytes.length)
            System.out.print("received:");
            System.out.write(bytes);
            System.out.println("-----")
          } else {
            throw new Exception("ERROR: The feeder could not take any  more input for " + loc)
          }
          //should one check if asyncParser needs more input?
          feeder.asyncParser.parse()
          Promise.pure(Pair(feeder, false))
        } catch {
          case e: Exception => {
            feeder.err = Some(e)
            Promise.pure(Pair(feeder, true))
          }
        }
    }.mapDone(_.result)


  protected case class RdfXmlFeeder(base: Option[URL]) {
    var err: Option[Exception] = None

    def result = err match {
      case None => Right(model.getGraph)
      case Some(e) => Left(e)
    }

    lazy val asyncReader: AsyncXMLStreamReader = new InputFactoryImpl().createAsyncXMLStreamReader()
    lazy val feeder: AsyncInputFeeder = asyncReader.getInputFeeder()
    lazy val model: Model = ModelFactory.createDefaultModel()
    lazy val asyncParser = new AsyncJenaParser(SAX2Model.create(base.map(_.toString).orNull, model), asyncReader)
  }

}


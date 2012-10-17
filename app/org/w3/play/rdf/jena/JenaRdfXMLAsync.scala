/*
 * Copyright 2012 Henry Story, http://bblfish.net/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.w3.play.rdf.jena

import org.w3.banana.jena.{BareJenaGraph, Jena}
import org.w3.banana.RDFXML
import java.net.URL
import play.api.libs.iteratee.{Input, Error, Iteratee}
import play.api.libs.concurrent.{Thrown, Promise}
import com.fasterxml.aalto.{AsyncInputFeeder, AsyncXMLStreamReader}
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.hp.hpl.jena.rdf.model.{ModelFactory, Model}
import patch.AsyncJenaParser
import com.hp.hpl.jena.rdf.arp.SAX2Model
import org.w3.play.rdf.RDFIteratee
import org.w3.play.util.IterateePlus
import scalaz.{Failure, Success, Validation}
import concurrent.Future


object JenaRdfXmlAsync extends RDFIteratee[Jena#Graph, RDFXML] {

  def apply(loc: Option[URL]): Iteratee[Array[Byte], Validation[Exception, Jena#Graph]] =  {
    val it : Iteratee[Array[Byte],RdfXmlFeeder]= Iteratee.fold2[Array[Byte], RdfXmlFeeder](new RdfXmlFeeder(loc)) {
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
          Future.successful(Pair(feeder, false))
        } catch {
          case e: Exception => {
            feeder.err = Some(e)
            Future.successful(Pair(feeder, true))
          }
        }
    }
    it.mapDone(_.result)
  }


  protected case class RdfXmlFeeder(base: Option[URL]) {
    var err: Option[Exception] = None

    def result: Validation[Exception,Jena#Graph] = err match {
      case None => Success( BareJenaGraph(model.getGraph) )
      case Some(e) => Failure(e)
    }

    lazy val asyncReader: AsyncXMLStreamReader = new InputFactoryImpl().createAsyncXMLStreamReader()
    lazy val feeder: AsyncInputFeeder = asyncReader.getInputFeeder()
    lazy val model: Model = ModelFactory.createDefaultModel()
    lazy val asyncParser = new AsyncJenaParser(SAX2Model.create(base.map(_.toString).orNull, model), asyncReader)
  }

}


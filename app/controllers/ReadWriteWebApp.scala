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

package controllers

import rww.play._
import org.w3.banana.plantain._
import java.nio.file.Path
import org.w3.banana._
import rww.play.rdf.IterateeSelector
import concurrent.ExecutionContext
import java.io.File
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import rww.play.auth.WebIDAuthN
import akka.actor.Props
import rww.ldp._
import rww.ldp.auth.{WACAuthZ, WebIDVerifier}
import java.net.URL


class ReadWriteWebApp(base: URL, path: Path)(implicit val ops: RDFOps[Plantain],
            sparqlOps: SparqlOps[Plantain],
            graphIterateeSelector: IterateeSelector[Plantain#Graph],
            sparqlIterateeSelector: IterateeSelector[Plantain#Query],
            val sparqlUpdateSelector: IterateeSelector[Plantain#UpdateQuery],
            val wsClient: WebClient[Plantain],
            val graphWriterSelector: WriterSelector[org.w3.banana.plantain.Plantain#Graph],
            val solutionsWriterSelector: WriterSelector[Plantain#Solutions],
            val ec: ExecutionContext) extends ReadWriteWeb[Plantain] {


  //todo: why do the implicit not work? (ie, why do I have to specify the implicit arguements?)
  implicit lazy val rwwBodyParser =  new RwwBodyParser[Plantain]()(ops,sparqlOps,graphIterateeSelector,
    sparqlIterateeSelector,sparqlUpdateSelector,ec)
  val baseUri = ops.URI(base.toString)

  val rww: RWWeb[Plantain] = {
    val w = new RWWeb[Plantain](baseUri)(ops,Timeout(30,TimeUnit.SECONDS))
    //, path,Some(Props(new PlantainWebProxy(base,Plantain.readerSelector))))
    val localActor = w.system.actorOf(Props(new PlantainLDPCActor(w.baseUri, path)),"rootContainer")
    w.setLDPSActor(localActor)
    val webActor = w.system.actorOf(Props(new LDPWebActor[Plantain](baseUri,wsClient)),"webActor")
    w.setWebActor(webActor)
    w
  }

  lazy val rwwActor =  new ResourceMgr[Plantain](base,rww, new WebIDAuthN(new WebIDVerifier(rww)),
    new WACAuthZ[Plantain](new WebResource[Plantain](rww))(ops))

}

import plantain._

object ReadWriteWebApp extends ReadWriteWebApp(plantain.rwwRoot, RwwConfiguration.rootContainerPath)
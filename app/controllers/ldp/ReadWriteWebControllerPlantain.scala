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

package controllers.ldp

import rww.play._
import org.w3.banana.plantain._
import java.nio.file.Path
import org.w3.banana._
import rww.play.rdf.IterateeSelector
import concurrent.ExecutionContext
import rww.play.auth.WebIDAuthN
import rww.ldp._
import rww.ldp.auth.{WACAuthZ, WebIDVerifier}
import java.net.URL
import rww.ldp.actor.RWWActorSystemImpl


class ReadWriteWebControllerPlantain(base: URL, path: Path, rww: RWWActorSystemImpl[Plantain])(implicit val ops: RDFOps[Plantain],
            sparqlOps: SparqlOps[Plantain],
            graphIterateeSelector: IterateeSelector[Plantain#Graph],
            sparqlIterateeSelector: IterateeSelector[Plantain#Query],
            val sparqlUpdateSelector: IterateeSelector[Plantain#UpdateQuery],
            val wsClient: WebClient[Plantain],
            val graphWriterSelector: WriterSelector[org.w3.banana.plantain.Plantain#Graph],
            val solutionsWriterSelector: WriterSelector[Plantain#Solutions],
            val ec: ExecutionContext) extends ReadWriteWebControllerGeneric[Plantain] {


  //todo: why do the implicit not work? (ie, why do I have to specify the implicit arguements?)
  implicit lazy val rwwBodyParser =  new RwwBodyParser[Plantain](base)(ops,sparqlOps,graphIterateeSelector,
    sparqlIterateeSelector,sparqlUpdateSelector,ec)

  lazy val resourceManager =  new ResourceMgr[Plantain](base,rww, new WebIDAuthN(new WebIDVerifier(rww)),
    new WACAuthZ[Plantain](new WebResource[Plantain](rww))(ops))

}

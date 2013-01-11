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

import org.www.readwriteweb.play._
import org.w3.banana.plantain._
import java.nio.file.Path
import org.w3.banana._
import org.www.play.rdf.IterateeSelector
import concurrent.ExecutionContext
import java.io.File


class ReadWriteWebApp(base: URI, path: Path)(implicit ops: RDFOps[Plantain],
            sparqlOps: SparqlOps[Plantain],
            iterateeSelector: IterateeSelector[Plantain#Graph],
            sparqlIterateeSelector: IterateeSelector[Plantain#Query],
            val writerSelector: WriterSelector[org.w3.banana.plantain.Plantain#Graph],
            val ec: ExecutionContext) extends ReadWriteWeb[Plantain] {


  //todo: why do the implicit not work? (ie, why do I have to specify the implicit arguements?)
  implicit lazy val rwwBodyParser =  new RwwBodyParser[Plantain]()(ops,sparqlOps,iterateeSelector,sparqlIterateeSelector)

  val LDPS = PlantainLDPS(base, path)

  val rwwActor =  new ResourceMgr(LDPS)

}

import plantain._
object ReadWriteWebApp extends ReadWriteWebApp(plantain.ops.URI("http://localhost:9000/2012/"),new File("test_www").toPath)
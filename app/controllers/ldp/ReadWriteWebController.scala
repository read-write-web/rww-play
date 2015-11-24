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

import java.net.URL
import java.nio.file.Path

import controllers.{RWWSetup, RdfSetup}
import rww.auth.{HttpAuthentication, WebIDAuthN}
import rww.ldp._
import rww.ldp.auth.{WACAuthZ, WebIDVerifier, WebKeyVerifier}
import rww.play._

object ReadWriteWebController extends ReadWriteWebController (
  RdfSetup.rwwRoot,
  RdfSetup.rootContainerPath
)


class ReadWriteWebController(
  base: URL,
  path: Path
) extends RWWSetup with ReadWriteWebControllerGeneric  {

  //todo: why do the implicit not work? (ie, why do I have to specify the implicit arguements?)
  implicit lazy val rwwBodyParser =  new RwwBodyParser[Rdf](base,tmpDirInRootConainer)(ops,sparqlOps,graphIterateeSelector,
    sparqlSelector,sparqlUpdateSelector,ec)

  def webidAuthN = if (!webIDTLSEnabled) None
  else
    Some(new WebIDAuthN(new WebIDVerifier(new WebResource[Rdf](rwwAgent))))

  def httpSigAuthN = new HttpAuthentication(new WebKeyVerifier(rwwAgent),base)



  lazy val resourceManager =  new ResourceMgr[Rdf](
    base,
    rwwAgent,
    httpSigAuthN,
    webidAuthN,
    new WACAuthZ[Rdf](new WebResource[Rdf](rwwAgent))(ops)
  )

}

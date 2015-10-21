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

import _root_.play.api.mvc.RequestHeader
import controllers.{RWWSetup, RdfSetup}
import rww.auth.{HttpAuthentication, WebIDAuthN}
import rww.ldp.LDPExceptions.ClientAuthDisabled
import rww.ldp._
import rww.ldp.auth.{WACAuthZ, WebIDVerifier, WebKeyVerifier}
import rww.play._
import rww.play.auth.{AuthN, Subject}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ReadWriteWebController extends ReadWriteWebController (
  RdfSetup.rwwRoot,
  RdfSetup.rootContainerPath
)


class ReadWriteWebController(base: URL, path: Path) extends RWWSetup with ReadWriteWebControllerGeneric  {

  //todo: why do the implicit not work? (ie, why do I have to specify the implicit arguements?)
  implicit lazy val rwwBodyParser =  new RwwBodyParser[Rdf](base,tmpDirInRootConainer)(ops,sparqlOps,graphIterateeSelector,
    sparqlSelector,sparqlUpdateSelector,ec)

  val webidAuthN = new WebIDAuthN(new WebIDVerifier(rwwAgent))
  val httpAuthN = new HttpAuthentication(new WebKeyVerifier(rwwAgent),base)

  val authn = new AuthN {
    override
    def apply(req: RequestHeader) = httpAuthN(req) andThen {
      case Success(Subject(List(), failures)) =>
        //todo: also take webid failures into account
        webidAuthN(req).map(s => Subject(s.principals, s.failures:::failures))
      case Success(other) =>
        //should perhaps also try WebID auth? Unlikely for the moment
        Future.successful(other)
      case Failure(x: ClientAuthDisabled) => webidAuthN(req)
      case Failure(other) => Future.failed(other)
    }
  }

  lazy val resourceManager =  new ResourceMgr[Rdf](
    base,
    rwwAgent,
    authn,
    new WACAuthZ[Rdf](new WebResource[Rdf](rwwAgent))(ops)
  )

}

/*
 * Copyright 2012 http://bblfish.net/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.www.play.auth

import java.net.URL
import play.api.mvc.RequestHeader
import play.api.Logger
import org.w3.banana.{RDF, RDFOps}
import concurrent.ExecutionContext

/**
 * A trait to add to to Controllers that need WebID Authentication
 *
 * @tparam Rdf
 */
trait WebIDAuth[Rdf<:RDF] {
  /**
   * How to find a url for a given path
   * @param path
   * @return
   */
  def meta(path: String): URL

  def base: URL = WebIDAuthObj.base
  def verifier: WebIDVerifier[Rdf]

  implicit lazy val idGuard: IdGuard[Rdf] = wacGuard.asInstanceOf[IdGuard[Rdf]]
  def wacGuard: WebAccessControl[Rdf]

  implicit def ops: RDFOps[Rdf]
  implicit def ec: ExecutionContext
  /**
   * function to wrap RequestHeaders with information, about where to
   * find meta data, what the name of the server is, and how to do
   * full WebID Authentication
   */
  def webReq(req: RequestHeader) : WebRequest[Rdf] =
    new PlayWebRequest[Rdf](new WebIDAuthN[Rdf]()(verifier),base,meta _)(req)

  def WebIDAuth: Auth[Rdf] = new Auth[Rdf](idGuard, webReq _)
}

object WebIDAuthObj {

  //Establish the base from system properties
  val base : URL= try {
    val b = Option(System.getProperty("https.defaultBase")) map { str => new URL(str)} getOrElse {
      val port = Option(System.getProperty("https.port")) map { Integer.parseInt(_) } getOrElse( 443 )
      val host = Option(System.getProperty("http.address")) getOrElse( "localhost" )
      new URL("https",host,port,"/")
    }
    controllers.setup.logger.info("base="+b)
    b
  } catch {
    case e : Exception => {
      controllers.setup.logger.warn("Could not establish default base for this server. This is needed for WebIDAuthentication." +
        " Will use https://localhost:8443 as default")
      new URL("https","localhost",8443,"/")
    }
  }
}

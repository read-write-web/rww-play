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
import play.api.mvc.{Request, RequestHeader}
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
  def authN: AuthN

  def base: URL
  def verifier: WebIDVerifier[Rdf]

  implicit lazy val idGuard: IdGuard[Rdf] = wacGuard.asInstanceOf[IdGuard[Rdf]]
  def wacGuard: WebAccessControl[Rdf]

  implicit def ops: RDFOps[Rdf]
  implicit def ec: ExecutionContext


  def WebIDAuth: Auth[Rdf] = new Auth[Rdf](idGuard,authN,meta _)
}


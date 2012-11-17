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

import play.api.mvc._
import org.www.play.auth._
import views._
import java.net.URL
import concurrent.ExecutionContext
import org.w3.banana.RDFOps


/**
 * A Basic Application demonstrating WebID Authentication
 */
class BasicApp extends WebIDAuthController {
  import setup._

  val wacGuard: WebAccessControl[Rdf]  = setup.wacGuard
  val verifier: WebIDVerifier[Rdf] = setup.JenaWebIDVerifier
  val ec: ExecutionContext = setup.executionContext
  val ops: RDFOps[Rdf] = setup.ops

  def meta(path: String) = {
    val i = path.lastIndexOf('/')
    val p = if (i < 0) path else path.substring(1,i+1)
    val metaURL = new URL("file:/"+p+"meta.ttl")
    metaURL
  }

  def webId(path: String) = WebIDAuth() { authFailure =>
    Unauthorized("You are not authorized access to "+ authFailure.request+ " because "+ authFailure.exception )
  }
  { authReq =>
      Ok("You are authorized for " + path + ". Your ids are: " + authReq.user)
  }

  def index = Action {
    Ok(html.index("Read Write Web"));
  }

}

object BasicApp extends BasicApp
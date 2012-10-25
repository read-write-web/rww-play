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
import org.w3.readwriteweb.play.auth._
import concurrent.Future
import org.w3.banana.jena.{JenaGraphSparqlEngine, Jena}
import akka.actor.ActorSystem
import org.w3.play.remote.GraphFetcher
import org.w3.play.rdf.jena.JenaAsync
import org.w3.play.auth.WebIDVerifier
import views._

object Application extends Controller {

  //setup: should be moved to a special init class
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = system.dispatcher
  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
  implicit val JenaGraphFetcher = new GraphFetcher[Jena](JenaAsync.graphIterateeSelector)
  implicit val JenaWebIDVerifier = new WebIDVerifier[Jena]()


  // Authorizes anyone with a valid WebID
  object WebIDAuth extends Auth(new WebIDAuthN[Jena](), _ => Future.successful(WebIDGroup),_=>Unauthorized("no valid webid"))


  def webId(path: String) = WebIDAuth { authReq =>
      Ok("You are authorized for " + path + ". Your ids are: " + authReq.user)
  }

  def index = Action {
    Ok(html.index("Read Write Web"));
  }

}
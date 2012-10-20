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
import org.w3.play.auth.WebIDAuthN
import views._

object Application extends Controller {

  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = system.dispatcher
  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
  implicit val JenaGraphFetcher = new GraphFetcher[Jena](JenaAsync.graphIterateeSelector)
  implicit val JenaWebIDAuthN = new WebIDAuthN[Jena]()

  val AWebIDFinder = new AWebIDFinder[Jena]()

  def index = Action {
    Ok(html.index("Read Write Web"));
  }

  def test(rg: String) =
   AuthZ(r => rg.startsWith("a")) {
        Action {
          Ok("hello "+rg)
        }
  }

  def webId(rg: String) =
     AsyncAuthZ(AGuard(AWebIDFinder, _ => Future.successful(WebIDAgent))) {
       Action {
         Ok("You are authorized. We found a WebID")
       }
     }

//
//    Async {
//      //timeouts should be set as transport specific options as explained in Netty's ChannelFuture
//      //if done that way, then timeouts will break the connection anyway.
//      req.certs.extend1{  
//        case Redeemed(cert) => Ok("your cert is: \n\n "+cert ) 
//        case Thrown(e) => InternalServerError("received error: \n"+e )
//      } 
//    } 
  
}
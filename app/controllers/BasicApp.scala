///*
// * Copyright 2012 Henry Story, http://bblfish.net/
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package controllers
//
//import play.api.mvc._
//import org.www.play.auth._
//import views._
//import java.net.URL
//import concurrent.ExecutionContext
//import org.w3.banana.RDFOps
//import scalax.io.Resource
//import java.io.InputStream
//import org.w3.banana.sesame.SesameRDFWriter
//import util.Success
//import org.w3.banana.jena.JenaRDFWriter
//
//
///**
// * A Basic Application demonstrating WebID Authentication
// */
//class BasicApp extends WebIDAuthController {
//  import setup._
//
//  val wacGuard: WebAccessControl[Rdf]  = setup.wacGuard
//  val verifier: WebIDVerifier[Rdf] = setup.JenaWebIDVerifier
//  val ec: ExecutionContext = setup.executionContext
//  val ops: RDFOps[Rdf] = setup.ops
//
//  def meta(path: String) = {
//    val i = path.lastIndexOf('/')
//    val p = if (i < 0) path else path.substring(1,i+1)
//    val metaURL = new URL("file:/"+p+"meta.ttl")
//    metaURL
//  }
//
//  def rulesFor(path: String) = {
//     import ops.URI
//     val metaURI = URI(meta(path).toString)
//     val fldr = setup.linkedDataCache.get(metaURI)
//     fldr.map { ldr =>
//       val ts = JenaRDFWriter.turtleWriter.asString(ldr.resource.graph,ldr.uri.toString).recover { case err =>
//         Success("could not fetch metadata at "+metaURI+" caught error "+err.toString)
//       }
//       ts.get
//     }
//
//// http://jesseeichar.github.com/scala-io-doc/0.4.1-seq/index.html
////     import scalax.io.JavaConverters._
////    new String(meta(path).asInput.byteArray,"UTF-8")
//  }
//
//  def webId(path: String) = WebIDAuth() { authFailure =>
//    Async {
//      rulesFor(authFailure.request.path).map { rules =>
//        Unauthorized(views.html.rww.wacTest("You are not authorized access to "+ authFailure.request+
//           " because "+ authFailure.exception, false,rules.toString ))
//      }
//    }
//  }
//  { authReq =>
//    Async {
//      rulesFor(authReq.request.path).map { rules =>
//       Ok(views.html.rww.wacTest("You are authorized for " + path + ". Your ids are: " + authReq.user,
//         true, rules.toString))
//      }
//    }
//  }
//
//  def index = Action {
//    Ok(html.index("Read Write Web"));
//  }
//
//}
//
//object BasicApp extends BasicApp
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

package org.www.play.rdf

import org.w3.banana._
import play.api.libs.ws.ResponseHeaders
import akka.actor.Actor

/**
 * Fetch resources on the Web and cache them
 * ( at a later point this would include saving them to an indexed quad store )
 *
 * @param ops the type of the underlying RDF library
 * @params serializers a map from RDF serialisations to the serialisers, synchronous or asynchronous, to transform
 *        input into a graph
 *
 * @author Henry Story
 * @created: 12/10/2011
 *
 */
abstract  class GraphCache[Rdf <: RDF](implicit
                             val ops: RDFOps[Rdf],
                             val iterateeSelector: IterateeSelector[Rdf#Graph]) extends Actor {
//  import dispatch._

  //  implicit val iterateeSelector: IterateeSelector[Jena#Graph] =
//    JenaBlockingRDFIteratee.turtleSelector combineWith JenaRdfXmlAsync.rdfxmlSelector


  //use shellac's rdfa parser
//  new net.rootdev.javardfa.jena.RDFaReader  //import rdfa parser


  //this is a simple but quite stupid web cache so that graphs can stay in memory and be used a little
  // bit across sessions
//  val cache: LoadingCache[URL,Validation[Throwable,Model]] =
//    CacheBuilder.newBuilder()
//      .expireAfterAccess(5, TimeUnit.MINUTES)
//      .softValues()
//      //         .expireAfterWrite(30, TimeUnit.MINUTES)
//      .build(new CacheLoader[URL, Validation[Throwable,Model]] {
//      def load(url: URL) = fetch(url)
//    })
//
//  val http = new Http with thread.Safety {
//    import org.apache.http.params.CoreConnectionPNames
//    client.getParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 3000)
//    client.getParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, 15000)
//  }
//
//  def basePath = null //should be cache dir?
//
//  def sanityCheck() = true  //cache dire exists? But is this needed for functioning?
//
//  def resource(u : URL) = new org.w3.readwriteweb.Resource {
//    import CacheControl._
//    def name() = u
//    def get(cacheControl: CacheControl.Value) = cacheControl match {
//      case CacheOnly => {
//        val res = cache.getIfPresent(u)
//        if (null==res) NoCachEntry.fail
//        else res
//      }
//      case CacheFirst => cache.get(u)
//      case NoCache => {
//        val res = fetch(u)
//        cache.put(u,res) //todo: should this only be done if say the returned value is not an error?
//        res
//      }
//    }
//    // when fetching information from the web creating directories does not make sense
//    //perhaps the resource manager should be split into read/write sections?
//    def save(model: Model) =  throw new MethodNotSupportedException("not implemented")
//
//    def createDirectory(model: Model) =  throw new MethodNotSupportedException("not implemented")
//  }




//  protected def receive = {
//    }


}

case class CORSResponse[Rdf<:RDF](graph: Rdf#Graph, headers: ResponseHeaders)

/**
 * For Jena based projects this is a good graph cacher.
 */
//object JenaGraphCache {
//  implicit val ops = JenaOperations
//  implicit val selector =  JenaAsync.graphIterateeSelector
//  val apply = new GraphCache[Jena]
//}



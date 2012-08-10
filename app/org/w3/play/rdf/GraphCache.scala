package org.w3.play.rdf

import jena.JenaAsync
import org.w3.banana._
import org.w3.banana.jena.{JenaOperations, Jena}
import java.net.URL
import play.api.libs.ws.{ResponseHeaders, WS}
import scala.Some
import akka.actor.Actor
import scalaz.Validation
import play.api.libs.iteratee.{Input, Done}
import org.w3.play.remote.{RemoteException, LocalException, WrappedException}

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
                             val ops: RDFOperations[Rdf],
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



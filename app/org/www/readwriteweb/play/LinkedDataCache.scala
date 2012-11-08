package org.www.readwriteweb.play

import org.w3.banana._
import org.www.play.rdf.IterateeSelector
import org.www.play.remote.{GraphNHeaders, GraphFetcher}
import java.net.{MalformedURLException, URL}
import concurrent.{ExecutionContext, Future}
import org.w3.banana.LinkedDataResource
import util.FutureValidation
import java.io.File
import play.api.libs.iteratee.{Enumerator, Enumeratee, Iteratee}
import scalaz.{Success, Failure}

trait LinkedDataCache[Rdf<:RDF] {
  def get(uri: Rdf#URI): Future[LinkedDataResource[Rdf]]
}

/**
 * fetches and caches linked data resource
 * todo: this implementation does not cache
 */
class IterateeLDCache[Rdf<:RDF](val graphSelector: IterateeSelector[Rdf#Graph])
                               (implicit dsl: Diesel[Rdf], ec: ExecutionContext)
extends LinkedDataCache[Rdf] {
  val graphFetcher = new GraphFetcher[Rdf](graphSelector)
  import dsl._
  import dsl.ops._

  val r = """^(.*)\.(\w{0,4})$""".r

   def get(uri: Rdf#URI): Future[LinkedDataResource[Rdf]] = {
     try {
       val url = new URL(uri.fragmentLess.toString)

       if ("file" equalsIgnoreCase url.getProtocol) {
         //todo: very likely a security hole here since we can get someone to send a URL and explore the file system
         val file = new File(url.getPath)
         val tp = url.getPath match {
           case r(_, suffix) => Some(suffix)
           case _ if url.getPath endsWith "/" => Some("/")
           case _ => None
         }
         val fileTp = tp.map {
           case "ttl" => MimeType("text/turtle")
           case "rdf" => MimeType("application/rdf+xml")
           case _ => MimeType("broken/mime")
         }
         val selector = fileTp.flatMap { mime => graphSelector(mime) }
         selector.map { rdfIt =>
           val enum = Enumerator.fromFile(file )
           val result = enum(rdfIt(Some(url)))
           val res2 = result.flatMap(_.run)
           res2.flatMap {
             case Success(graph) => Future.successful(LinkedDataResource(uri.fragmentLess,PointedGraph(uri,graph)))
             case Failure(e) => Future.failed(e)
           }
         }.getOrElse(Future.failed(new Exception("could not deduce mime type from extension for "+url) ))

       } else {
         val fvIteratee: Future[GraphNHeaders[Rdf]] =
           graphFetcher.fetch(url).flatMap { validation =>
             validation.fold(fail=>Future.failed(fail),succ=>Future.successful(succ))
           }
         fvIteratee.map { gh =>
            val resURI = URI(url.toString)
            LinkedDataResource(resURI,new PointedGraph(uri,gh.graph))
         }
       }
     } catch {
       case e: MalformedURLException =>
         Future.failed(WrappedThrowable(e))
     }

   }
}

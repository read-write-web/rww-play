package org.www.readwriteweb.play

import org.w3.banana._
import org.www.play.rdf.IterateeSelector
import org.www.play.remote.GraphFetcher
import java.net.{URISyntaxException, MalformedURLException, URL}
import concurrent.{ExecutionContext, Future}
import org.w3.banana.LinkedDataResource
import java.io.File
import play.api.libs.iteratee.Enumerator
import util.{Failure, Success}
import play.api.Logger

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

  //todo: this should be found elsewhere, in the graphFetcher for example
  val documentRoot = {
    val r = new File(Option(System.getProperty("document.root")).getOrElse("test_www"))
    Logger("rww").info("document root="+r.getAbsolutePath)
    r
  }

  //todo: This is very fragile: won't work if the server is running on non secure port.
  //todo: Yet, how should one know which one to resolve this to?
  //todo: needs refactoring ( this was done quickly at TPAC2012 )
  val base = controllers.setup.secureHost

  val r = """^(.*)\.(\w{0,4})$""".r

   def get(uri: Rdf#URI): Future[LinkedDataResource[Rdf]] = {
     try {
       val url = new URL(uri.fragmentLess.toString)

       if ("file" equalsIgnoreCase url.getProtocol) {
         //todo: very likely a security hole here since we can get someone to send a URL and explore the file system
         val file = new File(documentRoot,url.getPath)
         Logger("rww").info("getting metadata file: "+file + " for "+uri)
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
           val result = enum(rdfIt(Some(new URL(base,url.getPath))))
           val res2 = result.flatMap(_.run)
           res2.flatMap {
             case Success(graph) => Future.successful(LinkedDataResource(uri.fragmentLess,PointedGraph(uri,graph)))
             case Failure(e) =>     Future.failed(e)
           }
         }.getOrElse(Future.failed(new Exception("could not deduce mime type from extension for "+url) ))

       } else {
         graphFetcher.fetch(url).map { gh =>
            val resURI = URI(url.toString)
            LinkedDataResource(resURI,new PointedGraph(uri,gh.graph))
         }
       }
     } catch {
       case e: URISyntaxException => {
         Logger("rww").warn( "could not parse uri <"+uri+">");
         Future.failed(WrappedThrowable(e)) }
       case e: MalformedURLException => {
         Logger("rww").warn( "could not parse url <"+uri+">");
         Future.failed(WrappedThrowable(e))
       }
     }

   }
}

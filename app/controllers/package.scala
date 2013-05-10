package controllers

import akka.actor.ActorSystem
import concurrent.ExecutionContext
import org.w3.banana.jena.Jena
import org.www.play.auth._
import org.www.play.rdf.IterateeSelector
import org.www.play.rdf.jena.JenaAsync
import org.w3.banana.ldp.{WSClient, WebClient}

//import org.www.readwriteweb.play.{IterateeLDCache, LinkedDataCache}
//import org.www.play.auth.WebAccessControl
import play.api.Logger
import java.net.URL
import org.w3.banana.plantain.{Plantain}
import org.www.play.rdf.plantain.{PlantainSparqlQueryIteratee, PlantainBlockingRDFIteratee}
import org.www.play.rdf.sesame.SesameSparqlQueryIteratee

/**
 * gather some common setup values
 **/
trait Setup {
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext: ExecutionContext = system.dispatcher

  //Play setup: needed for WebID info
  //todo: the code below should be adapted to finding the real default of Play.
  lazy val securePort: Option[Int] = Option(System.getProperty("https.port")).map(
    Integer.parseInt(_)
  )

  lazy val port: Int = Option(System.getProperty("http.port")).map(p=> Integer.parseInt(p)).orElse(securePort).get

  lazy val host: String = {
    Option(System.getProperty("http.hostname")).getOrElse("localhost")
  }

  lazy val hostRoot: URL = {
    val protocol = if (securePort==None) "http" else "https"
    new URL(protocol,host,port,"")
  }

  lazy val rwwRoot: URL =  {
    val path = Option(System.getProperty("rww.root")).orElse(Some("/2013/")).get
    new URL(hostRoot,path)
  }


  val logger = Logger("rww")

}

/**
 * sets the useful objects needed for running a web server.
 * This is the place to set to choose if running Jena or Sesame.
 * ( since the plantain library is not yet generalised ... )
 * todo: in fact one has to do a bit more, because we also have to
 *       select the best iteratees, etc... and that is Jena/Sesame specific
 */
//object setup extends Setup {
//  //
//  //when changing from Jena to Sesame the following group of variables would need to be changed
//  //
//
//  type Rdf = Jena
//  import Jena._
//  val jenaAsync = new JenaAsync
//  implicit val bestIterateeSlector: IterateeSelector[Rdf#Graph] = jenaAsync.graphIterateeSelector
//  implicit val JenaGraphFetcher: GraphFetcher[Jena] = new GraphFetcher[Jena](jenaAsync.graphIterateeSelector)
//  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
//  implicit val ops = Jena.ops
//
//  //
//  // the following variables should be set correctly automatically when changing the value of Rdf above
//  //
//
//  implicit val wac: WebACL[Rdf] = WebACL[Rdf]
//  implicit val linkedDataCache: LinkedDataCache[Jena] = new IterateeLDCache[Jena](bestIterateeSlector)
//
//  //for WebAccessControl
//  implicit val JenaWebIDVerifier = new WebIDVerifier[Rdf]()
//  implicit val wacGuard: WebAccessControl[Rdf] = WebAccessControl[Rdf](linkedDataCache)
//}

object plantain extends Setup {
  type Rdf = Plantain

  implicit val ops = Plantain.ops
  import ops._
  implicit val sparqlOps = Plantain.sparqlOps
  val blockingIteratee = new PlantainBlockingRDFIteratee
  implicit val writerSelector = Plantain.rdfWriterSelector
  implicit val solutionsWriterSelector = Plantain.solutionsWriterSelector

  //we don't have an iteratee selector for Plantain
  implicit val iterateeSelector: IterateeSelector[Plantain#Graph] = blockingIteratee.BlockingIterateeSelector
  implicit val sparqlSelector:  IterateeSelector[Plantain#Query] =  PlantainSparqlQueryIteratee.sparqlSelector
  implicit val webClient: WebClient[Plantain] = new WSClient(Plantain.readerSelector,Plantain.turtleWriter)


}


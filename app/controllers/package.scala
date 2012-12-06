package controllers

import akka.actor.ActorSystem
import concurrent.ExecutionContext
import org.w3.banana.jena.{JenaGraphSparqlEngine, Jena}
import org.www.play.auth._
import org.www.play.rdf.IterateeSelector
import org.www.play.rdf.jena.JenaAsync
import org.www.play.remote.GraphFetcher
import org.www.readwriteweb.play.{IterateeLDCache, LinkedDataCache}
import play.api.mvc.{Request, Controller}
import org.www.play.auth.WebAccessControl
import play.api.Logger
import java.net.{URI, URL}

/**
 * sets the useful objects needed for running a web server.
 * This is the place to set to choose if running Jena or Sesame.
 *
 * todo: in fact one has to do a bit more, because we also have to
 *       select the best iteratees, etc... and that is Jena/Sesame specific
 */
object setup {
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext: ExecutionContext = system.dispatcher

  //Play setup: needed for WebID info
  //todo: need to refactor this so that it uses Play's setup so that there is no possibility code shift
  lazy val securePort: Int = Option(System.getProperty("https.port")).orElse(Some("8443")).map { port =>
    Integer.parseInt(port)
  }.get

  lazy val secureHost: URL = {
    Option(System.getProperty("http.hostname")).orElse(Some("localhost")).map { host =>
      new URL("https",host,securePort,"/")
    }.get
  }

  val logger = Logger("rww")
  //
  //when changing from Jena to Sesame the following group of variables would need to be changed
  //

  type Rdf = Jena
  import Jena._
  val jenaAsync = new JenaAsync
  implicit val bestIterateeSlector: IterateeSelector[Rdf#Graph] = jenaAsync.graphIterateeSelector
  implicit val JenaGraphFetcher: GraphFetcher[Jena] = new GraphFetcher[Jena](jenaAsync.graphIterateeSelector)
  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
  implicit val ops = Jena.ops

  //
  // the following variables should be set correctly automatically when changing the value of Rdf above
  //

  implicit val wac: WebACL[Rdf] = WebACL[Rdf]
  implicit val linkedDataCache: LinkedDataCache[Jena] = new IterateeLDCache[Jena](bestIterateeSlector)

  //for WebAccessControl
  implicit val JenaWebIDVerifier = new WebIDVerifier[Rdf]()
  implicit val wacGuard: WebAccessControl[Rdf] = WebAccessControl[Rdf](linkedDataCache)
}


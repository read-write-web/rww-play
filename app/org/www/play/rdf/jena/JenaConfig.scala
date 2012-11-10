package org.www.play.rdf.jena

import org.w3.banana.jena.{JenaRDFWriter, Jena}
import org.www.readwriteweb.play.{IterateeLDCache, LinkedDataCache, CORSProxy}
import org.www.play.remote.GraphFetcher
import akka.actor.ActorSystem
import org.www.play.rdf.IterateeSelector
import concurrent.ExecutionContext
import org.www.readwriteweb.play.auth.WebACL


object JenaConfig {
//  implicit val executionContext = ExecutionContext.Implicits.global
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext: ExecutionContext = system.dispatcher
  import Jena._

  val jenaAsync = new JenaAsync
  implicit val wac: WebACL[Jena] = WebACL[Jena]
  implicit val bestIterateeSlector: IterateeSelector[Jena#Graph] = jenaAsync.graphIterateeSelector

  implicit val JenaGraphFetcher: GraphFetcher[Jena] = new GraphFetcher[Jena](jenaAsync.graphIterateeSelector)
  implicit val linkedDataCache: LinkedDataCache[Jena] = new IterateeLDCache[Jena](bestIterateeSlector)
}

object CORSProxy extends CORSProxy[Jena](JenaConfig.jenaAsync.graphIterateeSelector,JenaRDFWriter.selector)
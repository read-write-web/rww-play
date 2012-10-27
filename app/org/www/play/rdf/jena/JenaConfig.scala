package org.www.play.rdf.jena

import org.w3.banana.jena.{JenaRDFWriter, Jena}
import org.www.readwriteweb.play.CORSProxy
import org.www.play.remote.GraphFetcher
import akka.actor.ActorSystem


object JenaConfig {
//  implicit val executionContext = ExecutionContext.Implicits.global
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext = system.dispatcher

  val jenaAsync = new JenaAsync
  implicit val jenaCORSProxy = new CORSProxy[Jena](jenaAsync.graphIterateeSelector,JenaRDFWriter.selector)
  implicit val JenaGraphFetcher = new GraphFetcher[Jena](jenaAsync.graphIterateeSelector)

}

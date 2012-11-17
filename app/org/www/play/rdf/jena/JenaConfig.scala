package org.www.play.rdf.jena

import org.w3.banana.jena.{JenaRDFWriter, Jena}
import controllers.setup._

object CORSProxy extends org.www.readwriteweb.play.CORSProxy[Jena](jenaAsync.graphIterateeSelector,JenaRDFWriter.selector)
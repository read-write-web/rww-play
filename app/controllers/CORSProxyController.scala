package controllers

import org.w3.banana.io.WriterSelector
import org.w3.banana.{RDF, RDFOps}
import play.api.mvc.Action
import play.api.mvc.Results._
import rww.ldp.WebClient
import rww.play.CORSProxy

import scala.util.Try


class CORSProxyController[Rdf<:RDF](webclient: WebClient[Rdf])
                                   (implicit
                                    ops: RDFOps[Rdf],
                                    selector: WriterSelector[Rdf#Graph,Try]) {

  private val proxy = new CORSProxy(webclient)


  // TODO it would be cool for the client to be able to specify a timeout
  def action(url: Option[String]) = url match {
    case Some(url) => proxy.get(url)
    case None => about
  }

  def about = Action {
    Ok( views.html.rww.corsProxy() )
  }

}

import controllers.RdfSetup._

object CORSProxyController extends CORSProxyController(RdfSetup.webClient)
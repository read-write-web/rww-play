package controllers

import play.api.mvc.Action
import play.api.mvc.Results._
import org.w3.banana.plantain.Plantain
import rww.play.CORSProxy


object CORSProxyController {

  private val proxy = {
    import controllers.plantain._
    new CORSProxy[Plantain](webClient)
  }

  // TODO it would be cool for the client to be able to specify a timeout
  def action(url: Option[String]) = url match {
    case Some(url) => proxy.get(url)
    case None => about
  }

  def about = Action {
    Ok( views.html.rww.corsProxy() )
  }

}
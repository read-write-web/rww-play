package controllers

import play.api.mvc.Action
import org.w3.banana.plantain.Plantain
import controllers.plantain._

object CORSProxy extends _root_.rww.play.CORSProxy[Plantain](webClient) {

  def action(url: Option[String]) = url match {
    case Some(url) => get(url)
    case None => about
  }

  def about = Action {
    Ok( views.html.rww.corsProxy() )
  }

}
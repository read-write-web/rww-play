package controllers

import play.api.mvc.Action

object CORSProxy extends org.www.readwriteweb.play.CORSProxy {

  def action(url: Option[String]) = url match {
    case Some(url) => get(url)
    case None => about
  }

  def about = Action {
    Ok( views.html.rww.corsProxy() )
  }

}
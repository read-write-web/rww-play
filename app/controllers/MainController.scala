package controllers

import play.api.mvc.Results._
import play.api.mvc.Action

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object MainController {


  def index = Action {
    Ok(views.html.index())
  }

  def about = Action {
    Ok(views.html.rww.ldp())
  }


}

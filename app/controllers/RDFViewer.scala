package controllers

import play.api.mvc.Action
import play.api.mvc.Results._

/**
 * Created by hjs on 18/11/2013.
 */
object RDFViewer {

  // TODO it would probably be more elegant to use a real template key instead of "window.location.href"
  def htmlFor(url: String) = Action { request =>
    val template = RwwConfiguration.rdfViewerHtmlTemplate
    val response = template.replace("window.location.href",s"'https://localhost:8443$url'")
    Ok(response).as("text/html")
  }

}

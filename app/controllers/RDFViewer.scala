package controllers

import play.api.mvc.Action
import play.api.mvc.Results._

/**
 * Created by hjs on 18/11/2013.
 */
object RDFViewer {

  def template = {
    import scalax.io.{Resource => xResource}
    val file = plantain.rdfViewerHtmlTemplate
    require(file.isFile, s"The RDF viewer template file ($file) is not a file")
    xResource.fromFile(file).string
  }

  // TODO it would probably be more elegant to use a real template key instead of "window.location.href"
   def htmlFor(url: String) = Action { request =>
    val response = template.replace("window.location.href",s"'$url'")
    Ok(response).as("text/html")
  }

}

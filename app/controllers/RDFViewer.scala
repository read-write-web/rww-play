package controllers

import play.api.mvc.Action
import scalax.io.{Resource, Input}
import play.api.mvc.Results._
import play.api.Logger
import play.api.Play
import java.io.File

/**
 * Created by hjs on 18/11/2013.
 */
object RDFViewer {

  val HtmlTemplatePathKey = "rdf.html.viewer.template.path"

  def htmlTemplate: String = {
    import scalax.io.{Resource=>xResource}
    val path = Play.current.configuration.getString(HtmlTemplatePathKey)
    require(path.isDefined,s"Missing configuration for key $HtmlTemplatePathKey")
    val file = new File(path.get)
    require(file.exists() && file.isFile && file.canRead,s"Unable to read file: $file")
    // we have checked the existence of the file before because this method xResource.fromFile creates the file if it doesn't exist
    // (the doc says it raises an exception but it's not the case)
    xResource.fromFile(file).string
  }

  // TODO it would probably be more elegant to use a real template key instead of "window.location.href"
  def htmlFor(url: String) = Action { request =>
    val template = htmlTemplate
    val response = template.replace("window.location.href",s"'https://localhost:8443$url'")
    Ok(response).as("text/html")
  }

}

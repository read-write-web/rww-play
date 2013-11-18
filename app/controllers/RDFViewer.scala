package controllers

import play.api.mvc.Action
import scalax.io.Input
import play.api.mvc.Results._

/**
 * Created by hjs on 18/11/2013.
 */
object RDFViewer {

  def htmlFor(url: String) = Action { request =>
    import scalax.io.{Resource=>xResource}
    val input:Input = xResource.fromFile("public/ldp/index.html")
    val response = input.string.replace("window.location.href",s"'https://localhost:8443$url'")
    Ok(response).as("text/html")
  }

}

package rww

import _root_.play.{api => PlayApi}

import controllers.routes


/**
 * @author ouertani@gmail.com
 * Date: 23/11/2013
 */
package object play {
  def buildRootURI(implicit request: PlayApi.mvc.Request[_])  : java.net.URI = {
    val path = routes.ReadWriteWebApp.get("")
    new java.net.URI( path.absoluteURL(true).replace( path.url, request.path))

  }

  implicit def toBananaURI (uri : java.net.URI): org.w3.banana.plantain.model.URI =
    org.w3.banana.plantain.model.URI.fromString(uri.toString)

  implicit class EnhancedRequest(val request: PlayApi.mvc.Request[_]) extends AnyVal {   def getAbsoluteURI = buildRootURI(request) }

}

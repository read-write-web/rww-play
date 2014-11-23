package rww

import _root_.play.api.mvc.Call
import _root_.play.{api => PlayApi}

import controllers.routes
import org.w3.banana.plantain.Plantain


/**
 * @author ouertani@gmail.com
 * Date: 23/11/2013
 */
package object play {




  implicit class EnhancedRequest(val request: PlayApi.mvc.Request[_]) extends AnyVal {
    private def buildRootURI(implicit request: PlayApi.mvc.Request[_])  : java.net.URI = {
      val path : Call = controllers.ldp.routes.ReadWriteWebController.get("")
      new java.net.URI( path.absoluteURL(true).replace( path.url, request.path))

    }
    def getAbsoluteURI = buildRootURI(request)
  }


  implicit class EnhancedRequestHeader(val requestHeader: PlayApi.mvc.RequestHeader) extends AnyVal {
    private def buildRootURI(requestHeader: PlayApi.mvc.RequestHeader)  : java.net.URI = {
      val path : Call = controllers.ldp.routes.ReadWriteWebController.get("")
      val absUrl = path.absoluteURL(true) (requestHeader)
      new java.net.URI( absUrl.replace( path.url, requestHeader.path))

    }
    def getAbsoluteURI = buildRootURI(requestHeader)
  }


}

/**
 * @author ouertani@gmail.com
 * Date: 24/11/2013
 */

import controllers.routes
import play.api._
import mvc.RequestHeader

object Global extends GlobalSettings {
  override def onRouteRequest(request: RequestHeader) = {
    import rww.play.EnhancedRequestHeader
    println("**********************using MyGlobal!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" + request.getAbsoluteURI)
    //println(request.path +"----"+  routes.ReadWriteWebApp.get("").absoluteURL(true))
    super.onRouteRequest(request)
  }
}

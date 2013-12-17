/**
 * @author ouertani@gmail.com
 * Date: 24/11/2013
 */

import controllers.{RwwConfiguration, routes}
import play.api._
import mvc.RequestHeader
import java.net._

object Global extends GlobalSettings {
  override def onRouteRequest(req: RequestHeader) = {
    import rww.play.EnhancedRequestHeader

    val uri = req.getAbsoluteURI

    if (uri.getPath.startsWith("/assets/") ||
        uri.getPath.startsWith("/srv/")) {
      super.onRouteRequest(req)
    } else if (uri.getHost != controllers.plantain.host) {
      req.method match {
        case "GET" => Some(controllers.ReadWriteWebController.get(req.path))
        case "POST" => Some(controllers.ReadWriteWebController.post(req.path))
        case "PATCH" => Some(controllers.ReadWriteWebController.patch(req.path))
        case "MKCOL" => Some(controllers.ReadWriteWebController.mkcol(req.path))
        case "HEAD" => Some(controllers.ReadWriteWebController.head(req.path))
        case "PUT" =>  Some(controllers.ReadWriteWebController.put(req.path))
        case "DELETE" => Some(controllers.ReadWriteWebController.delete(req.path))
      }
    } else  super.onRouteRequest(req)
  }
}

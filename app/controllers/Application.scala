package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent._
import org.w3.readwriteweb.play.auth._

object Application extends Controller {
  
  def index(rg: String) = //Ok("this should be an authz app. Please fix")
   AuthZ(r => rg.startsWith("a")) {
        Action {
          Ok("hello "+rg)
  }
      }
//
//    Async {
//      //timeouts should be set as transport specific options as explained in Netty's ChannelFuture
//      //if done that way, then timeouts will break the connection anyway.
//      req.certs.extend1{  
//        case Redeemed(cert) => Ok("your cert is: \n\n "+cert ) 
//        case Thrown(e) => InternalServerError("received error: \n"+e )
//      } 
//    } 
  
}
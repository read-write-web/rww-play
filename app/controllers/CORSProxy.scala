//package controllers
//
//import org.w3.banana.jena.{JenaRDFWriter, Jena}
//import controllers.setup._
//import play.api.mvc.Action
//
//object CORSProxy
//  extends org.www.readwriteweb.play.CORSProxy[Jena](
//    jenaAsync.graphIterateeSelector,
//    JenaRDFWriter.selector) {
//
//  def about = Action {
//    Ok( views.html.rww.corsProxy() )
//  }
//
//}
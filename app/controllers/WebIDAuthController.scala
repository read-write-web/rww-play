//package controllers
//
//import rww.play.auth.{WebIDVerifier, WebIDAuthN, WebIDAuth}
//import play.api.mvc.Controller
//import org.w3.banana.jena.Jena
//import org.w3.banana.plantain.Plantain
//
///**
// * A simple way of putting together a Controller that supports WebIDAuth
// */
//abstract class WebIDAuthController
//  extends Controller with WebIDAuth[Plantain] {
//  import plantain._
//
//  def base = plantain.secureHost
//  val authN = new WebIDAuthN(new WebIDAuthN(new WebIDVerifier(rww)))
//}
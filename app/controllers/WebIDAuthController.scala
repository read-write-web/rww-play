package controllers

import org.www.play.auth.{WebIDAuthN, WebIDAuth}
import play.api.mvc.Controller
import org.w3.banana.jena.Jena

/**
 * A simple way of putting together a Controller that supports WebIDAuth
 */
abstract class WebIDAuthController
  extends Controller with WebIDAuth[Jena] {
  import setup._

  def base = setup.secureHost
  val authN = new WebIDAuthN
}
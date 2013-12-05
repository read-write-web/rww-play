package controllers

/**
 * Useful to create a focus, on the navigation bar, on the current active tab
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object NavigationBar extends Enumeration {
  type NavigationBar = Value
  val Home, CreateSubdomain, CreateCertificate, NoFocus = Value
}


package org.w3.readwriteweb.util

/**
 * @author bblfish
 * @created 23/03/2012
 */

object Util {

  def cleanly[A,B](resource: => A)(cleanup: A => Unit)(code: A => B): Option[B] = {
    try {
      val r = resource
      try { Some(code(r)) }
      finally { cleanup(r) }
    } catch {
      case e: Exception => None
    }
  }
}

package controllers.ldp

import _root_.play.api.mvc._
import rww.play._

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
trait ReadWriteWebControllerTrait {


  def get(path: String): Action[AnyContent]

  def head(path: String): Action[AnyContent]

  def put(path: String): Action[RwwContent]

  def patch(path: String): Action[RwwContent]

  def post(path: String): Action[RwwContent]

  def delete(path: String): Action[AnyContent]

  def search(path: String): Action[RwwContent]

}

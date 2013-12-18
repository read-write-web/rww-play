package rww.ldp

import scala.language.reflectiveCalls

import org.w3.banana._
import java.nio.file._
import scala.concurrent._
import java.util.Date
import _root_.play.api.libs.iteratee._
import scala.util.Try
import scala.Some
import utils.Iteratees
import rww.ldp.model._


class LocalSetup {
  def aclPath(path: String) = {
    val p = path+".acl"
    p
  }

  def isAclPath(path: String) = {
    val a =path.endsWith(".acl")
    a
  }
}












case class OperationNotSupported(msg: String) extends Exception(msg)







package rww.play.auth

import java.net.{URI => jURI}
import java.security.Principal

import org.w3.banana.RDF
import play.api.Logger
import play.api.mvc.RequestHeader
import rww.ldp.LDPExceptions.{AuthNException, OtherAuthNException}
import rww.ldp.auth.{WebIDPrincipal, WebKeyPrincipal}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


/**
  * Close to java's notion of a Subject: it groups together a number
  * of verified credentials ( for the moment this is simplified to Principals )
  * and a number of failed attempts at authentication ( perhaps should be outside this class )
  * There are different principals, for example a public key principl, a WebID principal, etc...
  *
  * To think about:
  * One may want to tie each principal to the credentials that verified it: eg, Certificate or HTTP Auth.
  *
  * @param principals
  * @param failures keep track of failed authentications too
  *                 //todo: apply this to WebID auth too. the type is too restrictive at present
  *
  *
  * note: one cannot use a scalaz.Validation[AuthException,Subject] with
  *       case class Subject(principals: List[Principal)
  *       as it is quite possible that some authentications fail and other succeed
  */
case class Subject(principals: Set[Principal],failures: List[AuthNException]=List()) {
  def merge(other: Subject): Subject = Subject(
    principals ++ other.principals,
    failures ::: other.failures
  )

  lazy val webIds = principals.collect{
      case wp: WebIDPrincipal => wp
    }

  def toSession: String =
    principals.collect {
      case WebIDPrincipal(wid) => s"webid=$wid"
      case WebKeyPrincipal(key) => s"webkey=$key"
    }.mkString("\n")

}

/** The Anonymous subject */
object Anonymous extends Subject(Set())

//does this make sense? The SuperUser is the one who has a proof that he is everyone
//not used yet
object SuperUser extends Subject(Set(rww.ldp.auth.Agent))

object Subject {

  /** parse a string for storing in session into a principal */
  def parse(ops: Option[String]): Subject = {
    val l: List[List[Principal]] = for {
      principals <- ops.toList
      principalAV <- principals.lines.toList
      (typ, id) = principalAV.splitAt(principalAV.indexOf('=')+1)
    } yield {
      typ match {
        case "webid=" => List(WebIDPrincipal(new jURI(id)))
        case "webkey=" => List(WebKeyPrincipal(new jURI(id)))
        case other => {
          Logger.warn(s"ignoring principal: [$principalAV]")
          List()
        }
      }
    }
    Subject(l.flatten.toSet)
  }

}

object AuthN {
  def futureToFutureTry[T](f: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] =
    f.map(Success(_)).recover { case x: AuthNException => Failure(x) }

  def toSubject(seqOfFuturePrincipals: Seq[Future[Principal]])(
    implicit ec: ExecutionContext
  ): Future[Subject] = {

    val seqOfFutureTrys = seqOfFuturePrincipals.map(futureToFutureTry)

    Future.sequence(seqOfFutureTrys).map { seqTryPrincipals =>
      val grouped = seqTryPrincipals.groupBy {
        case Success(x) => "success"
        case Failure(e) => "failure"
      }
      Subject(
        grouped.get("success").toSeq.flatten.collect { case Success(p) => p }.toSet,
        grouped.get("failure").toSeq.flatten.collect {
          case Failure(e: AuthNException) => e
          case Failure(NonFatal(e)) =>  OtherAuthNException(e)
        }.toList
      )
    }
  }



}

/**
  * Authentication function
  *
  * The apply method returns a Future which is either
  * - an rww.ldp.LDPExceptions.AuthNotEnabled failure
  * - or a Subject
  */
trait AuthN extends (RequestHeader => Future[Subject])









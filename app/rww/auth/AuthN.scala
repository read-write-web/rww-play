package rww.play.auth

import java.security.Principal

import play.api.mvc.RequestHeader
import rww.ldp.LDPExceptions.{AuthException, HttpAuthException, OtherAuthException}
import rww.ldp.auth.WebIDPrincipal

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
case class Subject(principals: List[Principal],failures: List[AuthException]=List()) {
  lazy val webIds = principals.collect{
      case wp: WebIDPrincipal => wp
    }

}

object AuthN {
  def futureToFutureTry[T](f: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] =
    f.map(Success(_)).recover { case x: HttpAuthException => Failure(x) }

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
        grouped.get("success").toSeq.flatten.collect { case Success(p) => p }.toList,
        grouped.get("failure").toSeq.flatten.collect {
          case Failure(e: AuthException) => e
          case Failure(NonFatal(e)) =>  OtherAuthException(e)
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









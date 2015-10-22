package utils


import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success, Try}

object ScalaUtils {

  //todo: remove in scala 2.12 in favor of standard Future.transformWith
  implicit final class FTransform[T](val fu: Future[T]) extends AnyVal {
    def transformWith[U](f: Try[T] => Future[U])(implicit ec: ExecutionContext): Future[U] = {
      val s = fu.map[Try[T]](Success[T](_))
      val x = s.recover[Try[T]]({ case t: Throwable => Failure[T](t) })
        x.flatMap(f)
    }

    def transform[U](f: Try[T] => Try[U])(implicit ec: ExecutionContext): Future[U] =
      fu.map(Success(_)).recover({ case t: Throwable => Failure[T](t) }).map(x => f(x).get)
  }

}
package utils

import play.api.libs.iteratee.{Input, Iteratee, Enumerator}
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object Iteratees {

  /**
   * Permits to create an enumerator that will emit a single element, the result of a future
   * @param futureItem
   * @tparam T
   * @return
   */
  // TODO maybe it should send an Ending element in the stream or something?
  def singleElementEnumerator[T](futureItem: Future[T])(implicit ec: ExecutionContext): Enumerator[T] = {
    new Enumerator[T] {
      def apply[A](i: Iteratee[T, A]): Future[Iteratee[T, A]] =
        futureItem.flatMap { item =>
          i.feed(Input.El(item))
        }
    }
  }

  /**
   * Transforms an Enumerator[T] into a Future[List[T]]
   * @param enumerator
   * @tparam T
   * @return
   */
  def enumeratorAsList[T](enumerator: Enumerator[T])(implicit ec: ExecutionContext): Future[List[T]] = {
    val iteratee = Iteratee.fold[T,List[T]](Nil) { (list,elem) =>
      elem :: list
    }
    enumerator |>>> iteratee
  }

}

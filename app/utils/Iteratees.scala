package utils

import play.api.libs.iteratee._
import scala.concurrent.{ExecutionContext, Future}
import java.io.{IOException, OutputStream}
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object Iteratees extends Logging {

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
  def enumeratorAsList[T](enumerator: Enumerator[T]): Future[List[T]] = enumerator |>>> Iteratee.getChunks[T]

  /**
   * Permits to receive bytes abd forward them to an [[java.io.OutputStream]]
   * @param out
   * @param ec
   * @return
   */
  def toOutputStream(out: OutputStream)(implicit ec: ExecutionContext): Iteratee[Array[Byte],Unit] = Iteratee.foreach { bytes: Array[Byte] =>
    out.write(bytes)
  }

}

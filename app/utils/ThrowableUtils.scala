package utils

import com.google.common.base.Throwables
import scala.reflect.ClassTag

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object ThrowableUtils {

  /**
   * Permit to extract (unapply) the root cause of an exception to be able to match against
   * the exception root cause in pattern matching (future/try recovering for exemple)
   * @tparam T
   */
  trait RootCauseExtractor[T <: Throwable] {
    val classTag: ClassTag[T]
    def unapply(originalThrowable: Throwable): Option[T]
  }

  object RootCauseExtractor {
    /**
     * This is an extractor builder to extract the root cause of an exception
     */
    def of[T <: Throwable](implicit throwableClassTag: ClassTag[T]) = new RootCauseExtractor[T]{
      val classTag = throwableClassTag

      private def throwableAsInstanceOf(throwable: Throwable): Option[T] = {
        if ( classTag.runtimeClass.isInstance(throwable) ) Some(throwable.asInstanceOf[T])
        else None
      }

      def unapply(originalThrowable: Throwable): Option[T] = for {
        rootCause <-Some(Throwables.getRootCause(originalThrowable))
        maybeGoodTypeRootCause <- throwableAsInstanceOf(rootCause)
      } yield maybeGoodTypeRootCause
    }
  }


}

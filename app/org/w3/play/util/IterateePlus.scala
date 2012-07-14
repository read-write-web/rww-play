package org.w3.play.util

import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.{Cont, Done, Iteratee, Input}

/**
 * Extras needed for Iteratees
 */

object IterateePlus {

  /**
   * Fold2 function (take from Play 2.1 library) for backward compatibility
   *
   * This fold operation allows the fold to stop before having consumed all the input.
   *
   * @param state initial state
   * @param f function transforming each chunk of input
   * @tparam E
   * @tparam A
   * @return
   */
  def fold2[E, A](state: A)(f: (A, E) => Promise[(A,Boolean)]): Iteratee[E, A] = {
    def step(s: A)(i: Input[E]): Iteratee[E, A] = i match {

      case Input.EOF => Done(s, Input.EOF)
      case Input.Empty => Cont[E, A](i => step(s)(i))
      case Input.El(e) => { val newS = f(s, e); Iteratee.flatten(newS.map { case (s1, done) => if (!done) Cont[E, A](i => step(s1)(i)) else Done(s1, Input.Empty) }) }
    }
    (Cont[E, A](i => step(state)(i)))
  }

}

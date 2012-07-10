package org.w3.play.rdf

import org.w3.banana.{Syntax, MimeType}


/**
 * function from mime types to RDFIteratees that return Result
 * @tparam Result
 */
trait IterateeSelector[Result] extends (MimeType => Option[RDFIteratee[Result,Any]]) {
  def unapply(mime: MimeType) = apply(mime)
  def combineWith(other: IterateeSelector[Result]): IterateeSelector[Result] = IterateeSelector.combine(this, other)

}

object IterateeSelector {


  def apply[Result, T](implicit syntax: Syntax[T], reader: RDFIteratee[Result,T]): IterateeSelector[Result] =
    new IterateeSelector[Result] {
      def apply(mime: MimeType): Option[RDFIteratee[Result,Any]] =
        if (syntax.mimeTypes.list contains mime)
          Some(reader)
        else
          None
    }

  def combine[Result](selector1: IterateeSelector[Result],
                      selector2: IterateeSelector[Result]): IterateeSelector[Result] =
    new IterateeSelector[Result] {
      def apply(mime: MimeType): Option[RDFIteratee[Result,Any]] =
        selector1(mime) orElse selector2(mime)
    }

}


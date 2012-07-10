package org.w3.readwriteweb.play

import play.api.mvc.{ResponseHeader, SimpleResult, RequestHeader}
import org.w3.banana.jena.RDFWriterSelector
import org.w3.banana.{MediaRange, BlockingWriter}
import java.io.ByteArrayOutputStream
import play.api.libs.iteratee.Enumerator

/**
 * Helps building Play Writers using RDFWriterSelectors
 */
object PlayWriterBuilder {

  //return writer from request header
  def writerFor[Obj](req: RequestHeader)
                    (implicit writerSelector: RDFWriterSelector[Obj])
  :  Option[BlockingWriter[Obj, Any]] = {
    //these two lines do more work than needed, optimise to get the first
    val ranges = req.accept.map{ range => MediaRange(range) }
    val writer = ranges.flatMap(range => writerSelector(range)).headOption
    writer
  }


  /**
   * The Play Result object for a HTTP code and a blockingWriter
   * @param code
   * @param writer
   * @param obj
   * @tparam Obj
   * @return
   */
  def result[Obj](code: Int, writer: BlockingWriter[Obj,_])(obj: Obj) = {
    SimpleResult(
      header = ResponseHeader(200, Map("Content-Type" -> writer.syntax.mime)),  //todo
      body = toEnum(writer)(obj)
    )
  }

  /**
   * turn a blocking writer into an enumerator
   * todo: perhaps save some memory by not doing this operation in one chunk
   * todo: the base url
   *
   * @param writer
   * @tparam Obj
   * @return
   */
  def toEnum[Obj](writer: BlockingWriter[Obj,_]) =
    (obj: Obj) => {
      val res = new ByteArrayOutputStream()
      val tw = writer.write(obj, res, "http://localhost:8888/")
      Enumerator(res.toByteArray)
    }



}

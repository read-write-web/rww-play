/*
 * Copyright 2012 Henry Story, http://bblfish.net/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rww.play

import java.io.ByteArrayOutputStream

import org.w3.banana.io.{MediaRange, Writer, WriterSelector}
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{RequestHeader, Result, ResponseHeader}

import scala.util.Try

/**
 * Helps building Play Writers using RDFWriterSelectors
 */
object PlayWriterBuilder {
  import play.api.http.{MediaRange => PlayMediaRange}
  //return writer from request header
  def writerFor[Obj](req: RequestHeader)(implicit writerSelector: WriterSelector[Obj,Try]): Option[Writer[Obj,Try, Any]] = {
    //todo: the range selection should really be done completely within play
    def toBananaRange(range: PlayMediaRange): MediaRange = {
      MediaRange(range.mediaType+"/"+range.mediaSubType)
    }
    val writer = req.acceptedTypes.flatMap(range => writerSelector(toBananaRange(range))).headOption
    writer
  }


  /**
   * The Play Result object for a HTTP code and a blockingWriter
   * @param code
   * @param writer
   * @param obj the object that is to be published
   * @tparam Obj The type of the object to publish
   * @return A simple result
   */
  def result[Obj](code: Int, writer: Writer[Obj,Try,_], headers: Map[String,String]=Map.empty)(obj: Obj): Result = {
    Result(
      header = ResponseHeader(200, headers + ("Content-Type" -> writer.transformsTo.defaultMimeType.mime)),  //todo
      body   = toEnum(writer)(obj)
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
  def toEnum[Obj](writer: Writer[Obj,Try,_]) =
    (obj: Obj) => {
      utils.FileUtils.using(new ByteArrayOutputStream()) { out =>
        val tw = writer.write(obj, out, "http://localhost:8888/") // TODO ???????
        Enumerator(out.toByteArray)
      }
    }



}

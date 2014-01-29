package utils

import scala.collection.JavaConversions
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import java.lang.RuntimeException

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object ContentNegociationHelper {

  /**
   * The server knows which mime types it can produce, and the client sends with Accept http header its preferred mimeTypes.
   * Thus based on that, this method tries to find the best supported mime type that can be handled by the client.
   * It is the mime type that should be used in the client's response (and thus in the ContentType http header too)
   * @param supportedMimeTypes
   * @param acceptHeader
   * @return
   */
  def findAppropriateContentType(supportedMimeTypes: Set[String],acceptHeader: String): Try[String] = {
    Try {
      require(supportedMimeTypes != null && supportedMimeTypes.size > 0,s"No supported mime types provided: [$supportedMimeTypes]")
      require(acceptHeader != null && !acceptHeader.isEmpty,s"No acceptHeader provided: [$supportedMimeTypes]")
      val javaSupported = JavaConversions.asJavaCollection(supportedMimeTypes)
      val appropriateContentType = MIMEParse.bestMatch(javaSupported,acceptHeader)
      require(appropriateContentType != null && !appropriateContentType.isEmpty,s"Bad returned appropriateContentType=[$appropriateContentType]")
      appropriateContentType
    } transform (
      bestMatch => Success(bestMatch),
      matchError => Failure(new RuntimeException(s"Can't find appropriate ContentType for Accept=[$acceptHeader] while supportedMimeTypes=[$supportedMimeTypes]",matchError))
      )
  }

}

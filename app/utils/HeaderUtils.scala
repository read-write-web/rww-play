package utils

import play.api.mvc.{AnyContent, Headers,Request}
import net.sf.uadetector.UserAgentType
import net.sf.uadetector.service.UADetectorServiceFactory
import scala.util.Try

class HeaderUtils(headers: Headers) {
  import headers.get

  /**
   * Returns the UserAgentType found for a given request
   * @return
   */
  def getUserAgentType: UserAgentType = get("User-Agent").map(HeaderUtils.getUserAgentType(_)).getOrElse(UserAgentType.UNKNOWN)

  /**
   * Tells if the request is done by a given UserAgentType
   * @param userAgentType
   * @return
   */
  def hasUserAgentType(userAgentType: UserAgentType): Boolean = getUserAgentType == userAgentType

  /**
   * Tells if the request is done by a browser
   * @return
   */
  def isBrowser: Boolean = hasUserAgentType(UserAgentType.BROWSER)

  /**
   * Given a list of supported mime types that can be produced as an anwser by the server, will try to find the appropriate one to use.
   * It is possible that content negociation fails if the client does not accept any of the server's supported mime types
   * @param supportedMimeTypes
   * @return
   */
  def findReplyContentType(supportedMimeTypes: Set[String]): Try[String] = ContentNegociationHelper.findAppropriateContentType( supportedMimeTypes, get("Accept").orNull )

}

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object HeaderUtils {

  implicit def implicitConvertionFromRequest(request: Request[AnyContent]): HeaderUtils = new HeaderUtils(request.headers)


  private lazy val agentParser = UADetectorServiceFactory.getResourceModuleParser

  /**
   * Get the user agent type for a given user agent
   * @param userAgent
   * @return
   */
  def getUserAgentType(userAgent: String): UserAgentType = agentParser.parse(userAgent).getType


}

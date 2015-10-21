package rww.auth

import net.sf.uadetector.UserAgentFamily
import net.sf.uadetector.service.UADetectorServiceFactory
import play.api.mvc.RequestHeader

/**
 *  Object that collects methods on how different browsers differ with regard to Certificate
 *  requests
 *
 */
object BrowserCertBehavior {
  lazy val agentParser =  UADetectorServiceFactory.getResourceModuleParser

  /**
   *  Some agents do not send client certificates unless required by a NEED.
   *
   *  This is a problem for them, as it ends up breaking the SSL connection for agents that do not send a cert.
   *  For human agents this is a bigger problem, as one would rather send them a message of explanation for what
   *  went wrong, with perhaps pointers to other methods  of authentication, rather than showing an ugly connection
   *  broken/disallowed window ) For Opera and AppleWebKit browsers (Safari) authentication requests should
   *  therefore be done over javascript, on resources that NEED a cert, and which can fail cleanly with a
   *  javascript exception.  )
   *
   *  It would be useful if this could be updated by server from time to  time from a file on the internet,
   *  so that changes to browsers could update server behavior.
   *
   *  Note the library we use is based on information from http://user-agent-string.info/parse
   *
   * bertails: could be an implicit class + value class
   */
  def browserDoesNotSupportsTLSWantMode(req: RequestHeader): Boolean =  {
    req.headers.get("User-Agent").map { userAgentString =>
      val userAgent = agentParser.parse(userAgentString)

      FamiliesNotSupportingTLSWantMode.contains(userAgent.getFamily) || ajaxRequest(req) || userAgent.getName == "Android browser"
    }.getOrElse(false)
  }

  def browserMayNotSupportsClientCerts(req: RequestHeader): Boolean =  {
    req.headers.get("User-Agent").map { userAgentString =>
      val autz =req.headers.getAll("Authorization")
      val userAgent = agentParser.parse(userAgentString)
      FamiliesMayNotSupportTLSAuth.contains(userAgent.getFamily)  &&
        !req.headers.getAll("Authorization").exists(_.startsWith("ClientCertificate"))
      //this last one is not standard, but allows me to work with curl to test the server

    }.getOrElse(false)
  }

  private val FamiliesNotSupportingTLSWantMode = {
    import UserAgentFamily._
    Set(
      CURL,
      JAVA,
      SAFARI,
      OPERA,
      MOBILE_SAFARI,
      // see https://github.com/stample/rww-play/issues/74, it seems to work half the time on Chrome with Want mode :(
      CHROME
    )
  }

  private val FamiliesMayNotSupportTLSAuth = {
    import UserAgentFamily._
    Set( CURL  )
  }


  private def ajaxRequest(req: RequestHeader): Boolean =
    req.headers.get("X-Requested-With").map(_.trim.equalsIgnoreCase("XMLHttpRequest")).getOrElse(false)

}

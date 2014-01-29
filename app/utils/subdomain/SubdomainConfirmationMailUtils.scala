package utils.subdomain

import play.api.mvc.Request
import play.api.mvc.AnyContent
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import utils.HttpUtils.QueryStrings


/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object SubdomainConfirmationMailUtils {

  case class SubdomainConfirmationLinkData(subdomain: String, email: String,password: String)

  private val SubdomainQueryParam = "subdomain"
  private val EmailQueryParam = "email"
  private val PasswordQueryParam = "password"

  /**
   * Creates an unmodifiable link that will permit an user to validate its subdomain creation (and also its email)
   * @param baseUrl
   * @param linkData
   * @return
   */
  def createSignedSubdomainConfirmationLinkPath(baseUrl: String, linkData: SubdomainConfirmationLinkData): String = {
    val queryString = QueryStrings.toQueryString(Map(
      SubdomainQueryParam -> linkData.subdomain,
      EmailQueryParam -> linkData.email,
      PasswordQueryParam -> linkData.password
    ))
    val signedQueryString = QueryStrings.sign(queryString)
    QueryStrings.append(baseUrl,signedQueryString)
  }


  /**
   * Permits to try to extract the SubdomainConfirmationLinkData from a given request QueryString
   * @param request
   * @return
   */
  def getSubdomainConfirmationLinkData(request: Request[AnyContent]): Try[SubdomainConfirmationLinkData] = {
    val simpleQueryString = QueryStrings.toSimpleQueryString(request.queryString)
    (for {
      subdomain <- simpleQueryString.get(SubdomainQueryParam)
      email <- simpleQueryString.get(EmailQueryParam)
      password <- simpleQueryString.get(PasswordQueryParam)
    } yield SubdomainConfirmationLinkData(subdomain,email,password)) match {
      case Some(linkData) => Success(linkData)
      case None => Failure(new IllegalStateException(s"Can't find valid SubdomainConfirmationLinkData in request queryString: $simpleQueryString"))
    }
  }

}

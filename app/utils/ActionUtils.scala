package utils

import play.api.mvc._
import play.api.mvc.Results._
import play.api.Logger
import scala.util.Try
import scala.util.Success
import scala.util.Failure

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object ActionUtils {

  def LoggedAction(f: Request[AnyContent] => Result): Action[AnyContent] = {
    Action { request =>
      Logger.info("Calling action")
      f(request)
    }
  }


  def SignedQueryStringAction(f: Request[AnyContent] => Result): Action[AnyContent] = {
    Action { request =>
      tryUnsignRequest(request) match {
        case Success(newReq) =>  f(newReq)
        case Failure(e) => {
          Logger.error("Exception during check of QueryString signature",e)
          BadRequest("Bad QueryString signature")
        }
      }
    }
  }

  /**
   * This will try to check the signature in the given request QueryString.
   * The resulting request will not contain the QueryString param which
   * @see utils.HttpUtils.QueryStrings$#sign
   * @param request
   * @return
   */
  private
  def tryUnsignRequest[T](request: Request[T]): Try[Request[T]] = {
    HttpUtils.QueryStrings.unsign(request.queryString) map { verifiedAndCleanedQueryString =>
      new WrappedRequest[T](request) {
        override def queryString = verifiedAndCleanedQueryString
      }
    }
  }

}

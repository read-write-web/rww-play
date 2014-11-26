package utils

import play.api.mvc._
import play.api.mvc.Results._
import play.api.Logger
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future

/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object ActionUtils {

  object LoggingAction extends ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]):
    Future[Result] = {
      Logger.info("Calling action")
      block(request)
    }
  }

  object SignedQueryStringAction extends ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A],
                                block: (Request[A]) => Future[Result]): Future[Result] = {
      tryUnsignRequest(request) match {
        case Success(newReq) =>  block(newReq)
        case Failure(e) => {
          Logger.error("Exception during check of QueryString signature",e)
          Future.successful(BadRequest("Bad QueryString signature"))
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

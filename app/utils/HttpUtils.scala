package utils

import scala.util.Try
import scala.util.Success
import scala.util.Failure
import play.api.Logger


/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object HttpUtils {




  /**
   * Some utils to manipulate the QueryString of an URL
   */
  object QueryStrings {

    private val defaultSignatureQueryStringParamName = "signature"

    /**
     * A "real" QueryString has multiple parameters which all can have multiple values
     */
    type QueryString = Map[String, Seq[String]]

    /**
     * A "simple" QueryString will consider that each parameter has only a single value
     * This is very often the case so it may be easier to manipulate a SimpleQueryString
     */
    type SimpleQueryString = Map[String,String]

    /**
     * A simple utility that permits to create a map to a QueryString
     * The QueryString is more complex because it is possible to have multiple values for the same param name.
     * But in most cases we only have one param
     * @param simpleQueryString
     * @return
     */
    def toQueryString(simpleQueryString: SimpleQueryString): QueryString = simpleQueryString.mapValues(Seq(_))

    /**
     * Transform a QueryString to a SimpleQueryString.
     * If a query parameter has multiple values, only one will be kept
     * @param queryString
     * @return
     */
    def toSimpleQueryString(queryString: QueryString): SimpleQueryString = queryString.filter(!_._2.isEmpty).mapValues(_.head)

    /**
     * Code inspired by Play
     * This code can append to an url which already has QueryString parameters
     * @see play.api.mvc.Results#Redirect(play.api.mvc.Call)
     * @param url
     * @param queryString
     * @return the url with the given query string appended at the end
     */
    def append(url: String,queryString: QueryString): String = {
      import java.net.URLEncoder
      val fullUrl = url + Option(queryString).filterNot(_.isEmpty).map { params =>
        val firstChar = if (url.contains("?")) "&" else "?"
        firstChar + params.toSeq.flatMap { pair =>
          pair._2.map(value => (pair._1 + "=" + URLEncoder.encode(value, "utf-8")))
        }.mkString("&")
      }.getOrElse("")
      fullUrl
    }

    /**
     * Will add a signature to the current QueryString to ensure that this QueryString will not be modified by a malicious user
     * Be sure you don't add more QueryString parameter after adding the signature because that signature won't be valid anymore
     * @param queryString
     * @param signatureParamName
     * @return
     */
    def sign(queryString: QueryString,signatureParamName: String = defaultSignatureQueryStringParamName): QueryString = {
      val signature = createQueryStringSignature(queryString)
      queryString + (signatureParamName -> Seq(signature))
    }


    /**
     * Will verify that the queryString signature is ok, and will remove the signature parameter from the queryString
     * If you unsign a queryString you just signed, you should find back the original queryString without any error
     * @param queryString
     * @param signatureParamName
     * @return
     */
    def unsign(queryString: QueryString,signatureParamName: String = defaultSignatureQueryStringParamName): Try[QueryString] = {
      checkQueryStringSignature(queryString,signatureParamName) map { signatureError =>
        Failure(new IllegalArgumentException(s"Can't verify queryString signature. $signatureError"))
      } getOrElse {
        val unsignedQueryString = filterSignature(queryString)
        Success(unsignedQueryString)
      }
    }

    /**
     * Remove the signature from the QueryString
     * @param queryString
     * @param signatureParamName
     * @return
     */
    def filterSignature(queryString: QueryString,signatureParamName: String = defaultSignatureQueryStringParamName) = queryString.filterKeys(_ != signatureParamName)

    /**
     * Permits to verify that a query string is correctly signed
     * This permits to ensure an user has not modified any QueryString parameter
     * @param queryString
     * @param signatureParamName (must be the same parameter used during signature
     * @return the error
     */
    private
    def checkQueryStringSignature(queryString: QueryString,signatureParamName: String = defaultSignatureQueryStringParamName): Option[String] = {
      queryString.get(signatureParamName) match {
        case Some(Seq(expectedSignature)) => {
          val queryStringWithoutSignature = filterSignature(queryString)
          val queryStringSignature = createQueryStringSignature(queryStringWithoutSignature)
          if ( queryStringSignature == expectedSignature ) None
          else {
            Some(s"The expected query string signature is $expectedSignature while the query params produced signature $queryStringSignature. " +
              s"It is a bug or someone tries to modify a signed url. QueryString is $queryStringWithoutSignature")
          }
        }
        case None | Some(Seq()) => Some(s"The provided queryString does not contain any signature with param name $signatureParamName")
        case Some(multipleValueSeq) => Some(s"The provided queryString contains more than 1 signature with param name $signatureParamName. Values found = $multipleValueSeq")
      }
    }

    /**
     * Create a signature for the query string. The parameter orders will produce the same signature.
     * @param queryString
     * @return
     */
    private
    def createQueryStringSignature(queryString: QueryString): String = {
      import play.api.libs.Crypto
      val source = createSignatureSource(queryString)
      val signature = Crypto.sign(source)
      Logger.debug(s"Created signature is $signature for queryString = $queryString with signatureSource = $source")
      signature
    }

    /**
     * This will create the string representation of the QueryString that will serve as a source to produce the hash value
     * Notice that it doesn't have to be an URL queryString but can be any string representation of a QueryString,
     * as far as 2 equal queryStrings will produce the same representation is it ok...
     * However, we can't use toString because it can produce different string sources like:
     * - Map(email -> List(lorber.sebastien@gmail.com), password -> List(dfbf370d), subdomain -> List(dsstgst))
     * - Map(email -> ArrayBuffer(lorber.sebastien@gmail.com), password -> ArrayBuffer(42616d65), subdomain -> ArrayBuffer(dsstgst))
     * @param queryString
     * @return
     */
    private
    def createSignatureSource(queryString: QueryString): String = {
      val sorted = sortQueryString(queryString)
      Logger.debug("sorted " + sorted)
      append("",sorted)
    }

    /**
     * If we don't want to take care of query string ordering of parameters, it's not a bad idea to sort it.
     * This is useful for signing/verifying signature because 2 same maps with different param orders will produce the same signature
     * @param queryString
     * @return
     */
    private
    def sortQueryString(queryString: QueryString): QueryString = {
      queryString.toList.sortBy(_._1).toMap.mapValues(_.sorted)
    }
  }


}

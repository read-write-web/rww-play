package rww.ldp

import java.io.StringReader
import java.util.concurrent.atomic.AtomicReference

import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.ning.http.util.DateUtil
import org.slf4j.LoggerFactory
import org.w3.banana._
import org.w3.banana.io._
import org.w3.play.api.libs.ws.{Response, ResponseHeaders, WS}
import rww.ldp.model.{RemoteLDPR, _}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A Web Client interacts directly with http resources on the web.
 * It has a higher level API to deal with the types of requests an LDP server wants to do, in order to
 * make it easy to replace different implementations ( such as a testing api ).
 *
 * @tparam Rdf
 */
trait WebClient[Rdf <: RDF] {

  def get(url: Rdf#URI): Future[NamedResource[Rdf]]

  def post[S](url: Rdf#URI, slug: Option[String], graph: Rdf#Graph, syntax: Syntax[S])
             (implicit writer: Writer[Rdf#Graph, Try, S]): Future[Rdf#URI]

  def delete(url: Rdf#URI): Future[Unit]

  def put[S](url: Rdf#URI, graph: Rdf#Graph, syntax: Syntax[S])(implicit writer: Writer[Rdf#Graph,Try, S]): Future[Unit]

  def patch(uri: Rdf#URI, remove: Iterable[TripleMatch[Rdf]], add: Iterable[Rdf#Triple]): Future[Void] = ???
}

object WebClient {
  val log = LoggerFactory.getLogger(this.getClass)
}


/**
 * This WebClient uses Play's WS
 *
 * @param readerSelector
 * @tparam Rdf
 */
class WSClient[Rdf <: RDF](readerSelector: ReaderSelector[Rdf, Try], rdfWriter: RDFWriter[Rdf, Try, Turtle])
                          (implicit ops: RDFOps[Rdf], ec: ExecutionContext) extends WebClient[Rdf] {

  import ops._

  val parser = new LinkHeaderParser[Rdf]

  /**
   * following RFC5988 http://tools.ietf.org/html/rfc5988
   * todo: map all the other headers to RDF graphs where it makes sense
   */
  def parseHeaders(base: Rdf#URI, headers: FluentCaseInsensitiveStringsMap): Try[PointedGraph[Rdf]] = {
    import scala.collection.convert.wrapAsScala._
    WebClient.log.info(s"Link headers (temporary disabled) are: ${headers.get("Link")}")
    val linkHeaders = headers.get("Link")
    parser.parse(linkHeaders: _*).map { graph =>
      PointedGraph(base, graph.resolveAgainst(base))
    }
  }

  //todo: the HashMap does not give enough information on how the failure occurred. It should contain metadata on the
  //todo: response, so that decisions can be taken to fetch anew
  //cache does not need to be strongly synchronised, as losses are permissible
  val cache = new AtomicReference(immutable.HashMap[Rdf#URI, Future[NamedResource[Rdf]]]())

  protected def fetch(url: Rdf#URI): Future[NamedResource[Rdf]] = {
    WebClient.log.info(s"WebClient: fetching $url")
    /**
     * note we prefer rdf/xml and turtle over html, as html does not always contain rdfa, and we prefer those over n3,
     * as we don't have a full n3 parser. Better would be to have a list of available parsers for whatever rdf framework is
     * installed (some claim to do n3 when they only really do turtle)
     * we can't currently accept as we don't have GRDDL implemented
     */
    //todo, add binary support
    //todo: deal with redirects...
    val response = WS.url(url.toString)
      .withFollowRedirects(true)
      .withHeaders("Accept" -> "application/rdf+xml,text/turtle,application/xhtml+xml;q=0.8,text/html;q=0.7,text/n3;q=0.2")
      .get
    response.flatMap { response =>
      response.status match {
        case okStatus if okStatus >= 200 && okStatus < 300 => {
          WebClient.log.info(s"WebClient fetched content successfully for $url -> [${response.status}][${response.statusText}]")
          val maybeContentType = response.header("Content-Type")
          val responseBody: String = response.body
          tryToReadAsGraph(maybeContentType, responseBody, url.toString) match {
            case Success(graph) => {
              val headers: FluentCaseInsensitiveStringsMap = response.ahcResponse.getHeaders
              val meta = parseHeaders(URI(url.toString), headers)
              val updated = Try {
                DateUtil.parseDate(headers.getFirstValue("Last-Modified"))
              }
              Future.successful(RemoteLDPR(URI(url.toString), graph, meta, updated))
            }
            case Failure(e) => {
              val msg = s"WebClient for $url -> ContentType=[${maybeContentType}] -> Can't parse body as an RDF graph" +
                s"\nBody=\n################################BODY_BEGIN\n${response.body}\n################################BODY_END"
              WebClient.log.warn(msg)
              Future.failed(ParserException(msg, e))
            }
          }
        }
        case badStatus => {
          val msg = s"WebClient fetch error for $url -> Status=[${response.status}][${response.statusText}]" +
            s"\nBody=\n################################BODY_BEGIN\n${response.body}\n################################BODY_END"
          WebClient.log.warn(msg)
          Future.failed(BadStatusException(msg, badStatus))
        }
      }
    }
  }


  /**
   * Try to read the given body as an RDF graph.
   * If the contentType is not provided left to a default value like text/plain,
   * this will try some fallback parsers as best effort.
   * @param maybeContentType
   * @param body
   * @param url
   * @return
   */
  private def tryToReadAsGraph(maybeContentType: Option[String], body: String, url: String): Try[Rdf#Graph] = {
    val mimeTypes = getParsingMimeTypesToTry(maybeContentType)
    tryToReadWithMimeTypes(body, url)(mimeTypes) recoverWith {
      case e: Exception => Failure(ParserException(s"Can't parse $url with any mime types of $mimeTypes. Original contentType was $maybeContentType", e))
    }
  }

  private val AllRdfMimeTypes: Set[MimeType] = Set(
     MimeType("text","turtle"),
    MimeType("application","rdf+xml"),
    MimeType("text","n3")
  )


  /**
   * On some answers the content type header is not appropriately set while the body contains valid RDF.
   * This code permits to compute some mime types to try if this is the case.
   * This generally happens when the ContentType header is left to text/plain or not provided for example
   * @param maybeContentType
   * @return
   */
  private def getParsingMimeTypesToTry(maybeContentType: Option[String]): Set[MimeType] = {
    val maybeMimeType = maybeContentType.flatMap(ct => MimeType.parse(ct))
    maybeMimeType.map {
      _ match {
        case MimeType("text", "plain", _) => AllRdfMimeTypes
        case MimeType("application", "xml", _) => Set(MimeType("application", "xml"), MimeType("application", "rdf+xml"))
        case MimeType("text", "xml", _) => Set(MimeType("text", "xml"), MimeType("application", "rdf+xml"))
        case mimeT => Set(mimeT)
      }
    }.getOrElse(AllRdfMimeTypes)
  }


  private def readGraph(mimeType: MimeType, body: String, url: String): Try[Rdf#Graph] = {
    readerSelector(mimeType) match {
      case Some(reader) => {
        reader.read(new StringReader(body), url) recoverWith {
          case e: Exception => Failure(ParserException(s"Can't read graph $url as mimeType=$mimeType with reader $reader", e))
        }
      }
      case None => Failure(MissingParserException(s"no RDF Reader found for mime type ${mimeType} for reading $url"))
    }
  }

  private def tryToReadWithMimeTypes(body: String, url: String)(mimeTypes: Set[MimeType]): Try[Rdf#Graph] = {
    val baseTry: Try[Rdf#Graph] = Failure(new IllegalStateException(s"No mime type provided: $mimeTypes"))
    val foldFunction: (Try[Rdf#Graph], MimeType) => Try[Rdf#Graph] = (baseTry, mimeType) => baseTry recoverWith {
      case e: Exception => readGraph(mimeType, body, url)
    }
    mimeTypes.foldLeft(baseTry)(foldFunction)
  }


  /** This caches results */
  //todo: it's important that if the future is a failure of the right type ( say a timeout execption ) that it retry fetching the resource
  def get(url: Rdf#URI): Future[NamedResource[Rdf]] = {
    val c = cache.get()
    c.get(url).getOrElse {
      val result = fetch(url)
      cache.set(c + (url -> result))
      result.onFailure { case _ =>
        val futureCache = cache.get
        futureCache.get(url).foreach { futNamedRes =>
          if (futNamedRes == result) cache.set(futureCache - url) //todo: slight possibility that we loose a cache
        }
      }
      result
    }
  }

  /**
   * Post a graph to the given URL which should be a collection. Graph is posted in Turtle.
   * @param url the LDPC URL
   * @param slug a name the client prefers
   * @param graph the graph to post
   * @return The Future URL of the created resource
   */
  def post[S](url: Rdf#URI, slug: Option[String], graph: Rdf#Graph, syntax: Syntax[S])
             (implicit writer: Writer[Rdf#Graph, Try, S]): Future[Rdf#URI] = {
    val headers = ("Content-Type" -> syntax.defaultMimeType.mime) :: slug.toList.map(slug => ("Slug" -> slug))
    val futureResp = WS.url(url.toString).withHeaders(headers: _*).post(graph, syntax)
    futureResp.flatMap { resp =>
      if (resp.status == 201) {
        resp.header("Location").map {
          loc => Future.successful(URI(loc))
        } getOrElse {
          Future.failed(RemoteException.netty("No location URL", resp))
        }
      } else {
        Future.failed(RemoteException.netty("Resource creation failed", resp))
      }
    }
  }

  def delete(url: Rdf#URI): Future[Unit] = {
    val futureResp = WS.url(url.toString).delete()
    futureResp.flatMap { resp =>
      if (resp.status == 200 || resp.status == 202 || resp.status == 204) {
        cache.set(cache.get() - url.fragmentLess)
        Future.successful(())
      } else {
        Future.failed(RemoteException.netty("resource deletion failed", resp))
      }
    }
  }

  override
  def put[S](url: Rdf#URI, graph: Rdf#Graph, syntax: Syntax[S])
            (implicit writer: Writer[Rdf#Graph, Try, S]): Future[Unit] = {
    val headers = ("Content-Type" -> syntax.defaultMimeType.mime) :: Nil
    val futureResp = WS.url(url.toString).withHeaders(headers: _*).put(graph, syntax)
    futureResp.flatMap { resp =>
      if (resp.status == 200) {
        Future.successful(())
      } else {
        Future.failed(RemoteException.netty("Resource creation failed", resp))
      }
    }

  }
}


//case class GraphNHeaders[Rdf<:RDF](graph: Rdf#Graph, remote: ResponseHeaders)

trait FetchException extends BananaException

case class RemoteException(msg: String, remote: ResponseHeaders) extends Exception(msg) with FetchException

object RemoteException {
  def netty(msg: String, resp: Response) = {
    RemoteException(msg, ResponseHeaders(resp.status, WS.ningHeadersToMap(resp.ahcResponse.getHeaders)))
  }
}


case class BadStatusException(msg: String, status: Int) extends Exception(msg) with FetchException

case class LocalException(msg: String) extends Exception(msg) with FetchException

case class WrappedException(msg: String, e: Throwable) extends Exception(msg) with FetchException

case class WrongTypeException(msg: String) extends Exception(msg) with FetchException

case class MissingParserException(msg: String) extends Exception(msg) with FetchException

case class ParserException(msg: String, e: Throwable) extends Exception(msg, e) with FetchException
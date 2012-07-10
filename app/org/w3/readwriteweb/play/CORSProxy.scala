package org.w3.readwriteweb.play

import play.api.mvc.{Controller, SimpleResult, Action}
import java.net.URL
import org.w3.banana.{RDF, MimeType}
import org.w3.banana.jena.{JenaRDFBlockingWriter, Jena}
import org.w3.readwriteweb.play.PlayWriterBuilder._
import akka.actor._
import play.api.libs.ws.WS
import org.w3.play.rdf.IterateeSelector
import play.api.libs.iteratee.{Input, Iteratee, Done}
import scala.None
import play.api.libs.concurrent.Promise
import scala.Left
import scala.Some
import akka.actor.InvalidActorNameException
import play.api.libs.ws.ResponseHeaders
import akka.dispatch.Await
import akka.util.{Timeout, Duration}
import org.w3.play.rdf.jena.JenaAsync
import java.util.concurrent.TimeUnit

/**
 * A <a href="http://www.w3.org/TR/cors/">CORS</a> proxy that allows a client to fetch remote RDF
 * resources that do not have the required CORS headers.
 *
 * Currently this only permits GET operations. It is unclear what PUT, POST, DELETE operations would
 * look like for a CORS proxy.
 *
 */
object CORSProxy extends Controller {

  val system = ActorSystem("MySystem")
  lazy val fetcher: ActorRef = system.actorOf(Props[ResourceFetcher], name = "fetcher")
  import akka.pattern.ask

  import JenaRDFBlockingWriter.{WriterSelector=>RDFWriterSelector}

  // turn a header map into an (att,val) sequence
  private implicit def sequentialise(headers: Map[String,Seq[String]]) = headers.toSeq.flatMap(pair=>pair._2.map(v=>(pair._1,v)))

  def get(url: String) = Action {
    request =>
      System.out.println("in CORSProxy.get("+url+")")
      val iri = new URL(url)
      implicit val timeout = Timeout(10 * 1000)
      val futurePromiseResult = for (promise <- fetcher ask CORSFetch(iri, request.headers.toMap) mapTo manifest[Promise[Either[CORSException, CORSResponse[Jena]]]])
      yield {
        promise.map {
          case Left(RemoteException(msg, headers)) =>
          case Left(LocalException(msg)) =>  ExpectationFailed(msg)
          case Right(CORSResponse(graph, head)) => writerFor[Jena#Graph](request)(RDFWriterSelector).map { wr =>
            val hdrs = head.headers - "ContentType"
            //todo: this needs to be refined a lot, and thought through quite a lot more carefully
            val code = if (head.status == 200) 203
                       else head.status

            val corsHeaders = if (!hdrs.contains("Access-Control-Allow-Origin")) {
               val origin = request.headers.get("Origin")
               hdrs + ("Access-Control-Allow-Origin" -> Seq(origin.getOrElse("*")))
            } else {
              hdrs
            }
            result(code, wr)(graph).withHeaders(corsHeaders: _*)
          } getOrElse {
            UnsupportedMediaType("could not find serialiserfor Accept types"+request.headers.get(play.api.http.HeaderNames.ACCEPT))
          }
        }
     }
     val promiseResult = Await.result(futurePromiseResult,Duration.create(1L,TimeUnit.SECONDS)).asInstanceOf[Promise[SimpleResult[_]]]
     Async { promiseResult }
  }
}


/**
 * manages connections to domains, by delegating requests to given domains,
 * by throttling connections for example, or also by specialising requests and interpretation
 * of information when needed on domains.
 */

class ResourceFetcher extends Actor {

  def getOrCreateResourceActor(url: URL): ActorRef = {
    val service = url.getHost+":"+url.getPort
    try {
      context.children.find(service == _.path.name) getOrElse {
        context.actorOf(Props[JenaServiceConnection]  , name = service)
      }
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / service)
    }
  }

  protected def receive = {
    case msg@ CORSFetch(url, _) => {
      System.out.println("ResourceFetcher recevied CORSFetch("+url+")")
      val domainFetcher = getOrCreateResourceActor(url)
      domainFetcher.forward(msg)
    }

  }
}

/**
 * todo: build a system to re-use connections to web site for optimisation and
 * to limit the number of simultaneous connections to web sites
 *
 * @param graphSelector
 * @tparam Rdf
 */
class ServiceConnection[Rdf<:RDF](val graphSelector: IterateeSelector[Rdf#Graph]) extends Actor {

  def filterHeaders(headers: Map[String, Seq[String]]) = headers - "ACCEPT"


  protected def receive = {
    case CORSFetch(url, headers) => {
      System.out.println("in ServiceConnection Fetching "+url)
      val hdrs = for (key <- (headers - "Accept").keys;
                     value <- (headers(key))) yield (key,value)
      val promiseIteratee: Promise[Iteratee[Array[Byte], Either[CORSException, CORSResponse[Rdf]]]] =
        WS.url(url.toExternalForm).
          withHeaders(hdrs.toSeq:_*).
          withHeaders("Accept" -> "application/rdf+xml,text/turtle,application/xhtml+xml;q=0.8,text/html;q=0.7,text/n3;q=0.2").
          get {
          response: ResponseHeaders =>
            import MimeType._

            // get the new location - in case of 301 only
            // an unintuitive twist in HTTP, see the discussion on the WebID mailing list
            // http://lists.w3.org/Archives/Public/public-webid/2012Apr/0004.html
            // and the bug report on the httpbis mailing list
            // http://trac.tools.ietf.org/wg/httpbis/trac/ticket/154
            // and the definition of httpbis
            val newLocation = if (response.status == 301)
              response.headers.get("Content-Location").flatMap(_.headOption).map {
                loc => new URL(url, loc)
              } else None
            val loc = newLocation orElse Some(new URL(url.getProtocol, url.getAuthority, url.getPort, url.getPath))

            //an improvement could be to guess the content type by looking at the first array of bytes
            response.headers.get("Content-Type") match {
              case Some(headers) =>
                headers.headOption.map {
                  value => MimeType(normalize(extract(value)))
                } match {
                  case Some(graphSelector(iteratee)) => iteratee(loc).mapDone{
                    case Left(e) =>  Left(WrappedException("had problems parsing document returned by server",e))
                    case Right(g) => Right(CORSResponse(g,response))
                  }
                  case None => Done(Left(LocalException("no Iteratee/parser for Content-Type " + headers)), Input.Empty)
                }
              case None => Done(Left(new RemoteException("no Content-Type header specified in response returned by server ", response)), Input.Empty)
            }
        }
      //question: should I send back a Validation[Graph] or a Promise[Graph] or the complex Promise[Itera...] that I do
       val promiseResult:  Promise[Either[CORSException, CORSResponse[Rdf]]] = promiseIteratee.flatMap(_.run)
       sender ! promiseResult
    }
  }
}

case class CORSFetch(url: URL, headers: Map[String, Seq[String]])
case class CORSResponse[Rdf<:RDF](graph: Rdf#Graph, remote: ResponseHeaders)

class JenaServiceConnection extends ServiceConnection[Jena](JenaAsync.graphIterateeSelector)

trait CORSException
case class RemoteException(msg: String, remote: ResponseHeaders) extends CORSException
case class LocalException(msg: String) extends CORSException
case class WrappedException(msg: String, e: Exception) extends CORSException
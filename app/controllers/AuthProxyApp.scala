package controllers

import java.io.{File, InputStream, PrintWriter, StringWriter}
import java.net.URL
import play.api.libs.ws.WS
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
import org.w3.banana.jena.{Jena, JenaGraphSparqlEngine}
import org.www.play.auth._
import play.api
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import controllers.setup._

/**
 * A authorization proxy that can be used to demonstrate what existing web sites could look like
 * if protected by WebID. It helps if these web sites only use relative URIs to reference their
 * content.
 */
object AuthProxyApp extends Controller {

  val log = api.Logger("AuthProxyApp")

  //setup: should be moved to a special init class
  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
  implicit val JenaWebIDVerifier = new WebIDVerifier[Jena]()

  val aclBase: URL = {
    val baseFile = new File(".").toURL
    val base = System.getProperty("rww.proxy.acl","test_www/")
    new URL(baseFile,base)
  }

  val hostToProxy: URL = new URL(System.getProperty("rww.proxy.for","http://www.w3.org/"))

  log.info("Proxying host: "+hostToProxy+" with acls found at "+aclBase)

  def meta(path: String) = {
    val metaURL = new URL(aclBase,"meta.ttl")
    log.info("looking up acls in "+metaURL)
    metaURL
  }

  implicit val idGuard: IdGuard[Jena] = WebAccessControl[Jena](linkedDataCache)


  // Authorizes anyone with a valid WebID
  val WebIDAuth = new Auth[Jena](idGuard,new WebIDAuthN[Jena],meta _)

  def proxy(path: String) = WebIDAuth() { authFailure =>
    val sw =new StringWriter()
    val psw = new PrintWriter(sw)
    authFailure.exception.printStackTrace(psw)
    Unauthorized("You are not WebID authorized. Exception: "+ sw +
      "\n\n\nRequest was:"+authFailure.request )
  }{ authReq =>

  // see: https://github.com/playframework/Play20/blob/master/samples/workinprogress/twitterstream/app/controllers/Application.scala
  //    val nothing = Enumeratee.map[Array[Byte]](bytes => bytes)
  //    Ok.stream { socket: Socket.Out[Array[Byte]] =>
  //      val x=newReq.get(res => nothing(socket) )
  //      x
  //    }

    Async {
      val other = new URL(hostToProxy,path)
      log.info("Fetching "+other)
      val newReq = WS.url(other.toString)
      for (value <- authReq.request.headers.get("Accept")) newReq.withHeaders(("Accept",value))
      //found here: http://stackoverflow.com/questions/12992354/play-binary-webservice-response/12992690#12992690
      //but it looks to me like this is not as async as it could be. I have the feeling that everything gets loaded
      //into memory first
      newReq.get().map(response => {
        val asStream: InputStream = response.ahcResponse.getResponseBodyAsStream
        import scala.collection.JavaConversions._
        val headers = for {header <- response.getAHCResponse.getHeaders.keySet().toList
                           hvalue <- response.getAHCResponse.getHeaders(header).toList
        } yield {
          (header,hvalue)
        }

        SimpleResult(ResponseHeader(response.status, headers.toMap),
          Enumerator.fromStream(asStream)
        )
      })
    }
  }


}

package controllers.ldp

import _root_.play.{api => PlayApi}
import PlayApi.Logger
import PlayApi.mvc.Results._
import PlayApi.http.Status._
import PlayApi.libs.iteratee.Enumerator
import PlayApi.mvc._
import org.w3.banana._
import scala.concurrent.{Future, ExecutionContext}
import java.net.{URI=>jURI, URLDecoder}
import rww.play.rdf.IterateeSelector
import org.w3.banana.plantain.Plantain
import com.google.common.base.Throwables
import scala.util.Try
import rww.play._
import rww.ldp.model._
import rww.play.QueryRwwContent
import rww.play.AuthResult
import rww.ldp.LDPExceptions.ResourceDoesNotExist
import scala.util.Failure
import rww.ldp.LDPExceptions.AccessDeniedAuthModes
import rww.play.GraphRwwContent
import scala.Some
import rww.play.auth.AuthenticationError
import play.api.mvc.SimpleResult
import rww.ldp.LDPExceptions.ParentDoesNotExist
import rww.ldp.auth.WebIDPrincipal
import play.api.mvc.ResponseHeader
import scala.util.Success
import rww.play.BinaryRwwContent
import rww.ldp.LDPExceptions.AccessDenied
import rww.ldp.WrongTypeException
import rww.ldp.model.LocalLDPC


/**
 * ReadWriteWeb Controller for Play
 */
trait ReadWriteWebControllerGeneric[Rdf <: RDF] extends ReadWriteWebControllerTrait {

  def resourceManager: ResourceMgr[Rdf]

  implicit def rwwBodyParser: RwwBodyParser[Rdf]
  implicit def ec: ExecutionContext
  implicit val ops: RDFOps[Rdf]
  implicit def graphWriterSelector: WriterSelector[Rdf#Graph]
  implicit def solutionsWriterSelector: WriterSelector[Rdf#Solutions]
  implicit val boolWriterSelector: WriterSelector[Boolean] = BooleanWriter.selector
  implicit val sparqlUpdateSelector: IterateeSelector[Plantain#UpdateQuery]

  import rww.play.PlayWriterBuilder._
  import ops._


  private def stackTrace(e: Throwable) = Throwables.getStackTraceAsString(e)

  /**
   * The user header is used to transmit the WebId URI to the client, because he can't know it before
   * the authentication takes place with the server.
   * @param principals
   * @return a one element List or an empty one
   * todo: what happens
   */
  private def userHeader(principals: List[WebIDPrincipal]): List[(String,String)] =  {
    principals match {
      case Nil => Nil
      case webidp::_ => List("User" -> webidp.webid.toString)
    }
  }

  private def allowHeader(authResult: AuthResult[NamedResource[Rdf]]): Pair[String, String] = {
    allowHeader(authResult.authInfo.modesAllowed, authResult.result.isInstanceOf[LocalLDPC[_]])
  }


  private def allowHeader(modesAllowed: Set[Method.Value], isLDPC: Boolean): (String, String) = {
    "Allow" -> {
      val headerStr = modesAllowed.collect {
        case Method.Append => {
          if (isLDPC) "POST" else ""
        }
        case Method.Read => "GET, HEAD"
        case Method.Write => "PUT, PATCH, DELETE" + {
          if (isLDPC) ", POST" else ""
        }
      }
      ("OPTIONS"::headerStr.toList).filter(_ != "").mkString(", ")
    }
  }

  def get(path: String) = Action.async { request =>
    getAsync(request)
  }

  def options(file: String) = head(file)



  /**
   * Returns the content type to use to answer to the given request
   * @param request
   * @return
   */
  def findReplyContentType(request: PlayApi.mvc.Request[AnyContent]): Try[SupportedRdfMimeType.Value] = {
    import utils.HeaderUtils._
    request.findReplyContentType(SupportedRdfMimeType.StringSet).map( SupportedRdfMimeType.withName(_) )
  }


  private def getAsync(implicit request: PlayApi.mvc.Request[AnyContent]): Future[SimpleResult] = {
    findReplyContentType(request) match {
      case Failure(t) => {
        Future.successful {
          PlayApi.mvc.Results.UnsupportedMediaType(stackTrace(t))
        }
      }
      case Success(replyContentType) => {
        Logger.info(s"It seems the best content type to produce for this client is: $replyContentType")
        getAsync(replyContentType)
      }
    }
  }

  /**
   * Will try to get the resource and return it to the client in the given mimeType
   * Note that it does not apply to binary resources but only for RDF resources
   * @param bestReplyContentType
   * @param request
   * @return
   */
  private def getAsync(bestReplyContentType: SupportedRdfMimeType.Value)(implicit request: PlayApi.mvc.Request[AnyContent]): Future[SimpleResult] = {
    Logger.info(s"getAsync($bestReplyContentType)")
    val getResult = for {
      authResult <- resourceManager.get(request, request.getAbsoluteURI)
    } yield writeGetResult(bestReplyContentType,authResult)

    getResult recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case rse: ResourceDoesNotExist => NotFound(rse.getMessage + stackTrace(rse))
      case err @ AccessDeniedAuthModes(authinfo) => {
        Logger.warn("Access denied exception"+authinfo)
        bestReplyContentType match {
          case SupportedRdfMimeType.Html => {
            Unauthorized(
              views.html.ldp.accessDenied( request.getAbsoluteURI.toString, stackTrace(err) )
              //todo a better implementation would have this on the actor itself, which WOULD know the type
            ).withHeaders(allowHeader(authinfo.modesAllowed,false)) // we don't know if this is an LDPC
          }
          case SupportedRdfMimeType.Turtle | SupportedRdfMimeType.RdfXml => {
            Unauthorized(err.getMessage).withHeaders(allowHeader(authinfo.modesAllowed,false)) // we don't know if this is an LDPC
          }
        }
      }
      //todo: 401 Unauthorizes requires some WWW-Authenticate header. Can we really use it this way?
      case AuthenticationError(e) => Unauthorized("Could not authenticate user with TLS cert:"+stackTrace(e))
      case e => {
        Logger.error("Unknown InternalServerError",e)
        InternalServerError(e.getMessage + "\n" + stackTrace(e))
      }
    }
  }

  val wac = WebACLPrefix[Rdf]

  /**
   * return a number of Link headers for each element of the graph that can be thus tranformed
   * @param ldpr: the LDPR with the metadata
   * @return a Link header #Stupid play only allows one header type
   * //todo: move to a util library
   */
  private def linkHeaders(ldpr: NamedResource[Rdf]): (String,String) = {
    import syntax.URISyntax._
    val ldprUri: Rdf#URI = ldpr.location
    val l = ldprUri.underlying
    val path = l.getPath.substring(0,l.getPath.lastIndexOf('/'))
    val hostUri = new jURI(l.getScheme,l.getUserInfo,l.getHost,l.getPort,path,"","")
    val res = for {
      pg <- ldpr.meta.toOption.toList
      rel <- graphToIterable(pg.graph).toList
      (subject, relation, obj) = ops.fromTriple(rel)
      if (isURI(subject) && isURI(obj))
      objURI = obj.asInstanceOf[Rdf#URI]
    } yield {
      val rel = relation match {
        case rdf.typ => "type"
        case wac.accessTo => "acl"
        case URI(uristr) => s"<$uristr>"
      }
      val relativeObject = hostUri.relativize(objURI.underlying)
      s"<${relativeObject}>; rel=$rel"
    }
    "Link" -> res.mkString(", ")
  }

  private def writeGetResult(bestReplyContentType: SupportedRdfMimeType.Value, authResult: AuthResult[NamedResource[Rdf]])
                            (implicit request: PlayApi.mvc.Request[AnyContent]): SimpleResult = {
    def commonHeaders: List[(String, String)] =
      allowHeader(authResult) :: userHeader(authResult.authInfo.user).toList

    authResult.result match {
      case ldpr: LDPR[Rdf] =>  {
        bestReplyContentType match {
          case SupportedRdfMimeType.Html => Ok(views.html.ldp.rdfToHtml())
          case SupportedRdfMimeType.Turtle | SupportedRdfMimeType.RdfXml => {
            writerFor[Rdf#Graph](request).map { wr =>
              val headers =
                "Access-Control-Allow-Origin" -> "*" ::
                "Accept-Patch" -> Syntax.SparqlUpdate.mimeTypes.head.mime :: //todo: something that is more flexible
                 linkHeaders(ldpr)::
                commonHeaders
              result(200, wr, Map(headers:_*))(ldpr.relativeGraph)
            } getOrElse { throw new RuntimeException("Unexpected: no writer found")}
          }
        }
      }
      case bin: BinaryResource[Rdf] => {
        val contentType = bin.mime.mime
        Logger.info(s"Getting binary resource, no [$bestReplyContentType] representation available, so the content type will be [${contentType}}]")
        val headers =  "Content-Type" -> contentType :: linkHeaders(bin)::commonHeaders

        SimpleResult(
          header = ResponseHeader(200, Map(headers:_*)),
          body = bin.readerEnumerator(1024 * 8)
        )
      }
    }
  }



  def head(path: String) = Action.async { request =>
    getAsync(request).transform(res =>
    //Todo: this returns a Content-Length of 0, when it should either return none or the exact same as the original
    //see: http://stackoverflow.com/questions/3854842/content-length-header-with-head-requests
      SimpleResult(res.header, Enumerator(Array[Byte]())),
      e => e
    )
  }

  /**
   * http://tools.ietf.org/html/rfc4918#section-9.3
   * @param path
   * @return
   */
  def mkcol(path: String) = Action.async(rwwBodyParser) { implicit request =>
    val correctedPath = if (!request.path.endsWith("/")) request.path else request.path.substring(0, request.path.length - 1)
    val pathUri = new java.net.URI(correctedPath)
    val coll = pathUri.resolve(".")

    def mk(graph: Option[Rdf#Graph]): Future[SimpleResult] = {
      val path = correctedPath.toString.substring(coll.toString.length)
      for (answer <- resourceManager.makeCollection(coll.toString, Some(path), graph))
      yield {
        val res = Created("Created Collection at " + answer).withHeaders(userHeader(answer.id).toList:_*)
        if (request.path == correctedPath) res
        else res.withHeaders(("Location" -> answer.toString)::userHeader(answer.id).toList:_*)
      }
    }
    val resultFuture = request.body match {
      case rww: GraphRwwContent[Rdf] => mk(Some(rww.graph))
      case rww.play.emptyContent => mk(None)
      case _ => Future.successful(PlayApi.mvc.Results.UnsupportedMediaType("We only support RDF media types, for appending to collection."))
    }
    resultFuture recover {
      //case ResourceExists(e) => MethodNotAllowed(e) //no longer happens
      case ParentDoesNotExist(e) => Conflict(e)
      case AccessDenied(e) => Forbidden(e)
      case e => InternalServerError(e.toString + "\n" + stackTrace(e))
    }
  }

  def put(path: String) = Action.async(rwwBodyParser) { implicit request =>
    val future = for {
      answer <- resourceManager.put(request.body)
    } yield {
      Ok("Succeeded").withHeaders(userHeader(answer.id):_*)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case e => InternalServerError(e.getMessage + "\n" + stackTrace(e))
    }
  }

  def patch(path: String) = Action.async(rwwBodyParser) {implicit request =>
    val future = for {
      answer <- resourceManager.patch( request.body)
    } yield {
      Ok("Succeeded").withHeaders(userHeader(answer.id):_*)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case e => InternalServerError(e.getMessage + "\n" + stackTrace(e))
    }
  }


  def post(path: String) = Action.async(rwwBodyParser) { implicit request =>
    Logger.debug(s"Post on $path some body of type ${request.body.getClass}")
    val future = request.body match {
      case rwwGraph: GraphRwwContent[Rdf] => postGraph(Some(rwwGraph.graph))
      case rwwQuery: QueryRwwContent[Rdf] => postRwwQuery(rwwQuery)
      case rwwBinaryContent: BinaryRwwContent => postBinaryContent(rwwBinaryContent)
      case emptyContent => postGraph(None)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case e: WrongTypeException =>
        //todo: the Allow methods should not be hardcoded.
        SimpleResult(
          ResponseHeader(METHOD_NOT_ALLOWED),
          Enumerator(e.msg.getBytes("UTF-8"))
        )
      case e => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }

  private def slug(implicit request: PlayApi.mvc.Request[RwwContent]) = request.headers.get("Slug").map(t => URLDecoder.decode(t, "UTF-8"))

  private def postGraph(rwwGraph: Option[Rdf#Graph])(implicit request: PlayApi.mvc.Request[RwwContent]): Future[SimpleResult] = {
    for {
      location <- resourceManager.postGraph(slug, rwwGraph)
    } yield {
      Created.withHeaders("Location" -> location.result.toString::userHeader(location.id):_*)
    }
  }

  private def postBinaryContent(binaryContent: BinaryRwwContent)(implicit request: PlayApi.mvc.Request[RwwContent]) = {
    for {
      answer <- resourceManager.postBinary(request.path, slug, binaryContent.file, MimeType(binaryContent.mime) )
    } yield {
      Created.withHeaders("Location" -> answer.result.toString::userHeader(answer.id):_*)
    }
  }

  private def postRwwQuery(query: QueryRwwContent[Rdf])(implicit request: PlayApi.mvc.Request[RwwContent]) = {
    for {
      answer <- resourceManager.postQuery(request.path, query)
    } yield {
      answer.result.fold(
        graph =>
          writerFor[Rdf#Graph](request).map {
            wr => result(200, wr,Map(userHeader(answer.id):_*))(graph)
          },
        sol =>
          writerFor[Rdf#Solutions](request).map {
            wr => result(200, wr,Map(userHeader(answer.id):_*))(sol)
          },
        bool =>
          writerFor[Boolean](request).map {
            wr => result(200, wr,Map(userHeader(answer.id):_*))(bool)
          }
      ).getOrElse(PlayApi.mvc.Results.UnsupportedMediaType(s"Cannot publish answer of type ${answer.getClass} as" +
        s"one of the mime types given ${request.headers.get("Accept")}"))
    }
  }


  def delete(path: String) = Action.async { implicit request =>
    val future = for {
      answer <- resourceManager.delete(request)
    } yield {
      Ok.withHeaders(userHeader(answer.id):_*)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case e => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }

}



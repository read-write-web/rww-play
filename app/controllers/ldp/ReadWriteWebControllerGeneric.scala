package controllers.ldp

import java.net.{URLDecoder, URI => jURI}

import _root_.play.{api => PlayApi}
import akka.http.model.headers._
import akka.http.util.DateTime
import com.google.common.base.Throwables
import org.w3.banana._
import org.w3.banana.io.{BooleanWriter, Syntax, WriterSelector}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.iteratee.Enumerator
import play.api.mvc.Results._
import play.api.mvc._
import rww.ldp.LDPExceptions._
import rww.ldp.auth.WebIDPrincipal
import rww.ldp.model.{LocalLDPC, _}
import rww.ldp.{SupportedBinaryMimeExtensions, WrongTypeException}
import rww.play.auth.AuthenticationError
import rww.play.rdf.IterateeSelector
import rww.play.{AuthResult, BinaryRwwContent, GraphRwwContent, QueryRwwContent, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


/**
 * ReadWriteWeb Controller for Play
 */
trait ReadWriteWebControllerGeneric extends ReadWriteWebControllerTrait {
  type Rdf <: RDF
  def resourceManager: ResourceMgr[Rdf]

  //missing in Play
  val PRECONDITON_REQUIRED = 428
  val PreconditionRequired = new Status(PRECONDITON_REQUIRED)

  implicit def rwwBodyParser: RwwBodyParser[Rdf]
  implicit def ec: ExecutionContext
  implicit val ops: RDFOps[Rdf]
  implicit def graphWriterSelector: WriterSelector[Rdf#Graph,Try]
  implicit def solutionsWriterSelector: WriterSelector[Rdf#Solutions,Try]
  implicit val boolWriterSelector: WriterSelector[Boolean,Try] = BooleanWriter.selector
  implicit val sparqlUpdateSelector: IterateeSelector[Rdf#UpdateQuery]

  import ops._
  import rww.play.PlayWriterBuilder._


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


  private def etagHeader(authResult: AuthResult[NamedResource[Rdf]]): List[(String, String)] = {
     authResult.result.etag.toOption.map(et=>("ETag",et.value)).toList
  }

  private def updatedHeader(authResult: AuthResult[NamedResource[Rdf]]): List[(String, String)] = {
    authResult.result.updated.toOption.map{ u =>
      val lm = `Last-Modified`(DateTime(u.getTime))
      (lm.name(),lm.value())
    }.toList
  }

  private def allowHeaders(authResult: AuthResult[NamedResource[Rdf]]): List[(String, String)] = {
    val modesAllowed = authResult.authInfo.modesAllowed
    val isLDPC =  authResult.result.isInstanceOf[LocalLDPC[_]]
    val allow = "Allow" -> {
      val headerStr = modesAllowed.collect {
        case Method.Append =>
          authResult.result match {
            case l: LocalLDPC[Rdf] => "POST"
            case _ => ""
          }
          case Method.Read => "GET, HEAD, SEARCH"
          case Method.Write => "PUT, DELETE" + {
            authResult.result match {
              case ldpc: LocalLDPC[Rdf] => ", POST, PATCH"
              case ldpr: LocalLDPR[Rdf] => ", PUT, PATCH"
              case bin: LocalBinaryResource[Rdf] => ", PUT"
              case _ => ""
            }
          }
        }
      ("OPTIONS"::headerStr.toList).filter(_ != "").mkString(", ")
    }
    val acceptPost= if (isLDPC) {
      List("Accept-Post"->{SupportedRdfMimeType.StringSet.mkString(",")+","+SupportedBinaryMimeExtensions.mimeExt.keys.map(_.mime).mkString(",")})
    } else Nil
    allow::acceptPost
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



  /**
   * Will try to get the resource and return it to the client in the given mimeType
   * @param request
   * @return
   */
  private def getAsync(implicit request: PlayApi.mvc.Request[AnyContent]): Future[Result] = {
    val getResult = for {
      authResult <- resourceManager.get(request, request.getAbsoluteURI)
    } yield writeGetResult(authResult)

    getResult recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case rse: ResourceDoesNotExist => NotFound(rse.getMessage + stackTrace(rse))
      case umt: UnsupportedMediaType => Results.UnsupportedMediaType(umt.getMessage + stackTrace(umt))
      case err @ AccessDeniedAuthModes(authinfo) => {
        Logger.warn("Access denied exception"+authinfo)
           //todo: automatically create html versions if needed of error messages
            Unauthorized(
              views.html.ldp.accessDenied( request.getAbsoluteURI.toString, stackTrace(err) )
              //todo a better implementation would have this on the actor itself, which WOULD know the type
            )
//          }
//          case _ => {
//            Unauthorized(err.getMessage).withHeaders(allowHeaders(authinfo.modesAllowed,false):_*) // we don't know if this is an LDPC
//          }
//        }
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
   * return a number of Link headers for each element of the graph that can be thus transformed
   * @param ldpr: the LDPR with the metadata
   * @return a Link header #Stupid play only allows one header type
   * //todo: move to a util library
   */
  private def linkHeaders(ldpr: NamedResource[Rdf]): (String,String) = {
    val ldprUri: Rdf#URI = ldpr.location
    val l = new jURI(ldprUri.getString)
    val path = l.getPath.substring(0,l.getPath.lastIndexOf('/'))
    val hostUri = new jURI(l.getScheme,l.getUserInfo,l.getHost,l.getPort,path,"","")
    val hostRDFURI = URI(hostUri.toString)
    val res = for {
      pg <- ldpr.meta.toOption.toList
      rel <- pg.graph.triples.toList
      (subject, relation, obj) = ops.fromTriple(rel)
      if (isURI(subject) && isURI(obj))
      objURI = obj.asInstanceOf[Rdf#URI]
    } yield {
      val rel = relation match {
        case rdf.typ => "type"
        case wac.accessTo => "acl"
        case URI(uristr) => s"<$uristr>"
      }
      val relativeObject = hostRDFURI.relativize(objURI)
      s"<${relativeObject}>; rel=$rel"
    }
    "Link" -> res.mkString(", ")
  }

  private def writeGetResult(authResult: AuthResult[NamedResource[Rdf]])
                            (implicit request: PlayApi.mvc.Request[AnyContent]): Result = {
    def commonHeaders: List[(String, String)] =
      allowHeaders(authResult) :::
        etagHeader(authResult) :::
        updatedHeader(authResult) :::
        userHeader(authResult.authInfo.user).toList

    authResult.result match {
      case ldpr: LDPR[Rdf] => {
        writerFor[Rdf#Graph](request).map { wr =>
          val headers =
            "Access-Control-Allow-Origin" -> "*" ::
              "Accept-Patch" -> Syntax.SparqlUpdate.mimeTypes.head.mime :: //todo: something that is more flexible
              linkHeaders(ldpr) ::
              commonHeaders
          result(200, wr, Map(headers: _*))(ldpr.graph)
        } getOrElse {
          play.api.mvc.Results.UnsupportedMediaType("could not find serialiser for Accept types " +
            request.headers.get(play.api.http.HeaderNames.ACCEPT))
        }
      }
      case bin: BinaryResource[Rdf] => {
        val contentType = bin.mime.mime
        val headers =  "Content-Type" -> contentType :: linkHeaders(bin)::commonHeaders

        Result(
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
      Result(res.header, Enumerator(Array[Byte]())),
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

    def mk(graph: Option[Rdf#Graph]): Future[Result] = {
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
      case PropertiesConflict(msg) => Conflict(msg)
      case MissingEtag(me) => PreconditionRequired(me)
      case ETagsDoNotMatch(msg) => Results.PreconditionFailed("Etag preconditions failed:"+msg)
      case e => {
        InternalServerError(e.getMessage + "\n" + stackTrace(e))
      }
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
      case MissingEtag(me) => PreconditionRequired(me)
      case ETagsDoNotMatch(msg) => Results.PreconditionFailed("Etag preconditions failed:"+msg)
      case e => InternalServerError(e.getMessage + "\n" + stackTrace(e))
    }
  }


  def post(path: String) = Action.async(rwwBodyParser) { implicit request =>
    Logger.debug(s"POST on $path some body of type ${request.body.getClass}")
    val future = request.body match {
      case rwwGraph: GraphRwwContent[Rdf] => postGraph(Some(rwwGraph.graph))
      case rwwBinaryContent: BinaryRwwContent => postBinaryContent(rwwBinaryContent)
      case emptyContent => postGraph(None)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case umt: UnsupportedMediaType => Results.UnsupportedMediaType(umt.getMessage + stackTrace(umt))
      case OperationNotSupportedException(msg) => NotImplemented(msg)
      case e: WrongTypeException =>
        //todo: the Allow methods should not be hardcoded.
        Result(
          ResponseHeader(METHOD_NOT_ALLOWED),
          Enumerator(e.msg.getBytes("UTF-8"))
        )
      case e => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }

  def search(path: String) = Action.async(rwwBodyParser) { implicit request =>
    Logger.debug(s"SEARCH on $path some body of type ${request.body.getClass}")
    val future = request.body match {
      case rwwQuery: QueryRwwContent[Rdf] => postRwwQuery(rwwQuery)
      case other => Future.failed(
        rww.ldp.LDPExceptions.UnsupportedMediaType("We only support SPARQL Queries at present")
      )
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case umt: UnsupportedMediaType => Results.UnsupportedMediaType(umt.getMessage + stackTrace(umt))
      case e: WrongTypeException =>
        //todo: the Allow methods should not be hardcoded.
        Result(
          ResponseHeader(METHOD_NOT_ALLOWED),
          Enumerator(e.msg.getBytes("UTF-8"))
        )
      case e => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }


  private def slug(implicit request: PlayApi.mvc.Request[RwwContent]) = request.headers.get("Slug").map(t => URLDecoder.decode(t, "UTF-8"))

  private def postGraph(rwwGraph: Option[Rdf#Graph])
                       (implicit request: PlayApi.mvc.Request[RwwContent]): Future[Result] = {
    for {
      location <- resourceManager.postGraph(slug, rwwGraph)
    } yield {
      Created.withHeaders("Location" -> location.result.toString::userHeader(location.id):_*)
    }
  }

  private def postBinaryContent(binaryContent: BinaryRwwContent)(implicit request: PlayApi.mvc.Request[RwwContent]) = {
    for {
      answer <- resourceManager.postBinary(request.path, slug, binaryContent.file, binaryContent.mime )
    } yield {
      Created.withHeaders("Location" -> answer.result.toString::userHeader(answer.id):_*)
    }
  }

  private def postRwwQuery(query: QueryRwwContent[Rdf])
                          (implicit request: PlayApi.mvc.Request[RwwContent]): Future[Result] = {
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
      case MissingEtag(me) => PreconditionRequired(me)
      case ETagsDoNotMatch(msg) => Results.PreconditionFailed("Etag preconditions failed:"+msg)
      case e => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }

}



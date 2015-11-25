package controllers.ldp

import java.net.{URI => jURI, URLDecoder}
import java.security.Principal

import _root_.play.{api => PlayApi}
import akka.http.scaladsl.model.DateTime
import akka.http.scaladsl.model.headers._
import com.google.common.base.Throwables
import org.w3.banana._
import org.w3.banana.io.{BooleanWriter, Syntax, WriterSelector}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.iteratee.Enumerator
import play.api.mvc.Results._
import play.api.mvc._
import rww.ldp.LDPExceptions._
import rww.ldp.auth.{Method, WebIDPrincipal}
import rww.ldp.model.{LocalLDPC, _}
import rww.ldp.{SupportedBinaryMimeExtensions, WrongTypeException}
import rww.play.auth.Subject
import rww.play.rdf.IterateeSelector
import rww.play.{ BinaryRwwContent, GraphRwwContent, QueryRwwContent, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal


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
   * @param subject the authenticated subject
   * @return a one element List or an empty one
   * todo: what happens
   */
  private def userHeader(subject: Subject): List[(String,String)] =  {
    subject.principals.toList collect {
      case WebIDPrincipal(webidp) => ("User" -> webidp.toString)
    }
  }


  private def etagHeader(authResult: IdResult[NamedResource[Rdf]]): List[(String, String)] = {
     authResult.result.etag.toOption.map(et=>("ETag",et.value)).toList
  }

  private def updatedHeader(authResult: IdResult[NamedResource[Rdf]]): List[(String, String)] = {
    authResult.result.updated.toOption.map{ u =>
      val lm = `Last-Modified`(DateTime(u.getTime))
      (lm.name(),lm.value())
    }.toList
  }

  private def allowHeaders(authResult: IdResult[NamedResource[Rdf]]): List[(String, String)] = {
    val isLDPC =  authResult.result.isInstanceOf[LocalLDPC[_]]
    val allow = "Allow" -> {
      //todo: if we can get the authorized modes for the user efficiently should use just those
      val headerStr = List(Method.Read,Method.Write,Method.Append).collect {
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

  def options(file: String) = Action.async { request =>
    getAsync(request).transform(res =>
      //Todo: this returns a Content-Length of 0, when it should either return none or the exact same as the original
      //see: http://stackoverflow.com/questions/3854842/content-length-header-with-head-requests
      //note: for CORS we need to return a 200, even if the resource is access controlled
      Result(res.header.copy(status=200), Enumerator(Array[Byte]())),
      e => e
    )
  }

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
      case ETagsMatch(_) => NotModified
      case ClientAuthNDisabled(msg,optT) => {
        //todo: this should be tied into the error below
        //todo: add all the normal headers here too
        Unauthorized(
          views.html.ldp.accessDenied( request.getAbsoluteURI.toString, msg )
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate"->"""Signature realm="/""""::
          "Access-Control-Expose-Headers"-> "WWW-Authenticate"::
          corsHeaders :_*
        )
      }
      case e: AuthNException => {
        //todo: this should be tied into the error below
        Unauthorized(
          views.html.ldp.accessDenied( request.getAbsoluteURI.toString, stackTrace(e) )
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate"->"""Signature realm="/""""::
          "Access-Control-Expose-Headers"-> "WWW-Authenticate"::
           corsHeaders :_*
          )
      }
      case err @ NoAuthorization(_,_,_) => {
           //todo: automatically create html versions if needed of error messages
            Unauthorized(
              views.html.ldp.accessDenied( request.getAbsoluteURI.toString, stackTrace(err) )
              //todo a better implementation would have this on the actor itself, which WOULD know the type
            ).withHeaders("WWW-Authenticate" -> """Signature realm="/""""::
              "Access-Control-Expose-Headers" -> "WWW-Authenticate"::
              corsHeaders :_*)
        //          }
//          case _ => {
//            Unauthorized(err.getMessage).withHeaders(allowHeaders(authinfo.modesAllowed,false):_*) // we don't know if this is an LDPC
//          }
//        }
      }
      case NonFatal(e) => {
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

  def corsHeaders(implicit request: PlayApi.mvc.Request[AnyContent]): List[(String,String)] = {
    val origin = request.headers.get("Origin").getOrElse("*")
    "Access-Control-Allow-Origin" -> origin ::
      "Access-Control-Allow-Credentials"->"true"::
      "Access-Control-Allow-Headers" -> "Authorization,Host,User,Signature-Date,*"::Nil
  }

  private def writeGetResult(authResult: IdResult[NamedResource[Rdf]])
                            (implicit request: PlayApi.mvc.Request[AnyContent]): Result = {
    def commonHeaders: List[(String, String)] =
      allowHeaders(authResult) :::
        etagHeader(authResult) :::
        updatedHeader(authResult) :::
        userHeader(authResult.subject)


    authResult.result match {
      case ldpr: LDPR[Rdf] => {
        writerFor[Rdf#Graph](request).map { wr =>
          val headers =
               corsHeaders:::
              "Accept-Patch" -> Syntax.SparqlUpdate.mimeTypes.head.mime :: //todo: something that is more flexible
              linkHeaders(ldpr) ::
              commonHeaders
          result(200, wr, Map(headers: _*))(ldpr.graph).addingToSession("subject"->authResult.subject.toSession)
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
        ).addingToSession("subject"->authResult.subject.toSession)
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

  def put(path: String) = Action.async(rwwBodyParser) { implicit request =>
    val future = for {
      answer <- resourceManager.put(request.body)
    } yield {
      Ok("Succeeded").withHeaders(userHeader(answer.subject):_*)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case PropertiesConflict(msg) => Conflict(msg)
      case MissingEtag(me) => PreconditionRequired(me)
      case ETagsDoNotMatch(msg) => Results.PreconditionFailed("Etag preconditions failed:"+msg)
      case err @ AccessDenied(authinfo) => {
        Logger.warn("Access denied exception"+authinfo)
        //todo: automatically create html versions if needed of error messages
        Unauthorized(
          views.html.ldp.accessDenied( request.getAbsoluteURI.toString, stackTrace(err) )
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate"->"""Signature realm="/"""")
      }

      case e => {
        InternalServerError(e.getMessage + "\n" + stackTrace(e))
      }
    }
  }

  def patch(path: String) = Action.async(rwwBodyParser) {implicit request =>
    val future = for {
      answer <- resourceManager.patch( request.body)
    } yield {
      Ok("Succeeded").withHeaders(userHeader(answer.subject):_*)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case MissingEtag(me) => PreconditionRequired(me)
      case ETagsDoNotMatch(msg) => Results.PreconditionFailed("Etag preconditions failed:"+msg)
      case err @ AccessDenied(authinfo) => {
        Logger.warn("Access denied exception" + authinfo)
        //todo: automatically create html versions if needed of error messages
        Unauthorized(
          views.html.ldp.accessDenied(request.getAbsoluteURI.toString, stackTrace(err))
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate" -> """Signature realm="/"""")
      }
      case NonFatal(e) => InternalServerError(e.getMessage + "\n" + stackTrace(e))
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
      case err @ AccessDenied(authinfo) => {
        Logger.warn("Access denied exception" + authinfo)
        //todo: automatically create html versions if needed of error messages
        Unauthorized(
          views.html.ldp.accessDenied(request.getAbsoluteURI.toString, stackTrace(err))
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate" -> """Signature realm="/"""")
      }
      case e: WrongTypeException =>
        //todo: the Allow methods should not be hardcoded.
        Result(
          ResponseHeader(METHOD_NOT_ALLOWED),
          Enumerator(e.msg.getBytes("UTF-8"))
        )
      case NonFatal(e) => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
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
      case err @ AccessDenied(authinfo) => {
        Logger.warn("Access denied exception" + authinfo)
        //todo: automatically create html versions if needed of error messages
        Unauthorized(
          views.html.ldp.accessDenied(request.getAbsoluteURI.toString, stackTrace(err))
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate" -> """Signature realm="/"""")
      }
      case NonFatal(e) => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }


  private def slug(implicit request: PlayApi.mvc.Request[RwwContent]) = request.headers.get("Slug").map(t => URLDecoder.decode(t, "UTF-8"))

  private def postGraph(rwwGraph: Option[Rdf#Graph])
                       (implicit request: PlayApi.mvc.Request[RwwContent]): Future[Result] = {
    for {
      location <- resourceManager.postGraph(slug, rwwGraph)
    } yield {
      Created.withHeaders("Location" -> location.result.toString::userHeader(location.subject):_*)
    }
  }

  private def postBinaryContent(binaryContent: BinaryRwwContent)(implicit request: PlayApi.mvc.Request[RwwContent]) = {
    for {
      answer <- resourceManager.postBinary(request.path, slug, binaryContent.file, binaryContent.mime )
    } yield {
      Created.withHeaders("Location" -> answer.result.toString::userHeader(answer.subject):_*)
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
            wr => result(200, wr,Map(userHeader(answer.subject):_*))(graph)
          },
        sol =>
          writerFor[Rdf#Solutions](request).map {
            wr => result(200, wr,Map(userHeader(answer.subject):_*))(sol)
          },
        bool =>
          writerFor[Boolean](request).map {
            wr => result(200, wr,Map(userHeader(answer.subject):_*))(bool)
          }
      ).getOrElse(PlayApi.mvc.Results.UnsupportedMediaType(s"Cannot publish answer of type ${answer.getClass} as" +
        s"one of the mime types given ${request.headers.get("Accept")}"))
    }
  }


  def delete(path: String) = Action.async { implicit request =>
    val future = for {
      answer <- resourceManager.delete(request)
    } yield {
      Ok.withHeaders(userHeader(answer.subject):_*)
    }
    future recover {
      case nse: NoSuchElementException => NotFound(nse.getMessage + stackTrace(nse))
      case MissingEtag(me) => PreconditionRequired(me)
      case ETagsDoNotMatch(msg) => Results.PreconditionFailed("Etag preconditions failed:"+msg)
      case err @ AccessDenied(authinfo) => {
        Logger.warn("Access denied exception" + authinfo)
        //todo: automatically create html versions if needed of error messages
        Unauthorized(
          views.html.ldp.accessDenied(request.getAbsoluteURI.toString, stackTrace(err))
          //todo a better implementation would have this on the actor itself, which WOULD know the type
        ).withHeaders("WWW-Authenticate" -> """Signature realm="/"""")
      }
      case NonFatal(e) => ExpectationFailed(e.getMessage + "\n" + stackTrace(e))
    }
  }

}



package controllers

import java.io.{OutputStream, File}
import java.net.URL
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit

import _root_.play.api.{Logger, Play}
import akka.actor.ActorSystem
import akka.util.Timeout
import org.openrdf.rio.helpers.JSONLDMode
import org.w3.banana.io._
import org.w3.banana._
import org.w3.banana.sesame.io.{SesameRDFWriter, SesameSyntax}
import rww.ldp.actor.{RWWActorSystem, RWWActorSystemImpl}
import rww.ldp.{WSClient, WebClient}
import rww.play.rdf.IterateeSelector
import rww.play.rdf.sesame.{SesameBlockingRDFIteratee, SesameSparqlQueryIteratee, SesameSparqlUpdateIteratee}

import scala.concurrent.ExecutionContext
import scala.util.Try


/**
 * gather some common setup values
 **/
trait Setup {


  val logger = Logger("rww")
  val httpsPortKey = "https.port"
  val httpsExternalPortKey = "https.external.port"
  val httpHostnameKey = "http.hostname"
  val RootContainerPathKey = "rww.root.container.path"
  val rwwSubDomainsEnabledKey = "rww.subdomains"
  val baseHostnameKey = "http.hostname"


  // We usually run https on port 8443 and use a tcp redirect (using iptables) of 443 to 8443
  // to avoid launching app as root (required for opening 443 default https port).
  // The externalSecurePort provided (if any) is probably 443 which is the one that is supposed
  // to be used by http clients.
  lazy val externalSecurePort: Option[Int] = Play.current.configuration.getInt(httpsExternalPortKey)


  //Play setup: needed for WebID info
  //todo: the code below should be adapted to finding the real default of Play.
  lazy val securePort: Option[Int] = Play.current.configuration.getInt(httpsPortKey)

  // TODO ! this seems useless: duplicate of securePort value not wrapped in an option right?
  lazy val port: Int = externalSecurePort.orElse(securePort).get

  lazy val host: String =
    Play.current.configuration.getString(httpHostnameKey).getOrElse("localhost")

  // This permits to remove the default http 80 / https 443 port from the String
  def normalizeUri(uri: String): String = akka.http.model.Uri(uri).toString()

  def normalizeURL(url: URL): URL = new URL(normalizeUri(url.toString))

  lazy val hostRoot: URL = {
    val protocol = if (securePort==None) "http" else "https"
    val url = new URL(protocol,host,port,"")
    val normalized = normalizeURL(url)
    normalized
  }

  def hostRootSubdomain(subdomain: String): URL = {
    val subdomainHost = subdomain + "." + hostRoot.getHost
    new URL(hostRoot.getProtocol,subdomainHost,hostRoot.getPort,hostRoot.getFile)
  }

  lazy val rwwRoot: URL =  {
    val path = controllers.routes.MainController.about().url+"/" // TODO: not the appropriate way to get this url!
    new URL(hostRoot,path)
  }

  lazy val rwwSubdomainsEnabled: Boolean = Play.current.configuration.getBoolean(rwwSubDomainsEnabledKey).getOrElse(false)

  /**
   * we check the existence of the file because Resource.fromFile creates the file if it doesn't exist
   * (the doc says it raises an exception but it's not the case)
   * @param key property key
   * @return
   */
  def getFileForConfigurationKey(key: String): File = {
    val path = Play.current.configuration.getString(key)
    require(path.isDefined,s"Missing configuration for key $key")
    val file = new File(path.get)
    require(file.exists() && file.canRead,s"Unable to find or read file/directory: $file")
    file
  }


  val rootContainerPath: Path = {
    val file = getFileForConfigurationKey(RootContainerPathKey)
    require(file.isDirectory,s"The root container ($file) is not a directory")
    file.toPath.toAbsolutePath
  }

  lazy val tmpDirInRootConainer: Path =
    Files.createDirectories(rootContainerPath.resolve("tmp"))

  logger.info(s""""
    secure port=$securePort
    externalSecurePort=$externalSecurePort
    port = $port
    host = $host
    hostRoot = $hostRoot
    rwwRoot = $rwwRoot
    rww root LDPC path = $rootContainerPath
    with subdomain support = $rwwSubdomainsEnabled
    Some properties:
       - jdk.tls.disabledAlgorithms= ${java.security.Security.getProperty("jdk.tls.disabledAlgorithms")}
       - jdk.certpath.disabledAlgorithms= ${java.security.Security.getProperty("jdk.certpath.disabledAlgorithms")}
    """)
}

trait RdfSetup  {
  type Rdf <: RDF
  implicit val system = ActorSystem("MySystem")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout = Timeout(30,TimeUnit.SECONDS)

  implicit val ops: RDFOps[Rdf]
  implicit val sparqlOps: SparqlOps[Rdf]
  implicit val sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph]

  implicit val graphIterateeSelector: IterateeSelector[Rdf#Graph]
  implicit val sparqlSelector:  IterateeSelector[Rdf#Query]
  implicit val sparqlUpdateSelector:  IterateeSelector[Rdf#UpdateQuery]
  implicit val graphWriterSelector: WriterSelector[Rdf#Graph,Try]
  implicit val solutionsWriterSelector: SparqlSolutionsWriterSelector[Rdf]
  

  //  implicit val solutionsWriterSelector = Plantain.solutionsWriterSelector
  //  implicit val patch: LDPatch[Rdf,Try] = PlantainLDPatch

  val webClient: WebClient[Rdf]

  //constants
}

trait SesameSetup extends RdfSetup with Setup {
  import org.w3.banana.sesame.Sesame

  type Rdf = Sesame
  implicit val ops: RDFOps[Rdf] = Sesame.ops
  implicit val sparqlOps: SparqlOps[Rdf] = Sesame.sparqlOps
  implicit val sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph]
    with SparqlUpdate[Rdf,Try, Rdf#Graph] = Sesame.sparqlGraph


  val blockingIteratee = new SesameBlockingRDFIteratee
  implicit val graphIterateeSelector: IterateeSelector[Rdf#Graph] = blockingIteratee.BlockingIterateeSelector
  implicit val sparqlUpdateSelector: IterateeSelector[Rdf#UpdateQuery] = SesameSparqlUpdateIteratee.sparqlSelector
  implicit val sparqlSelector: IterateeSelector[Rdf#Query] = SesameSparqlQueryIteratee.sparqlSelector
  implicit val graphWriterSelector: WriterSelector[Rdf#Graph,Try]  = {
    import Sesame._
    implicit val jsonLd: SesameSyntax[JsonLd] = SesameSyntax.jsonldSyntax(JSONLDMode.COMPACT)
    implicit val jsonldWriter: SesameRDFWriter[JsonLd] = new SesameRDFWriter[JsonLd]

    //note this writer selector also contains a writer for html that knows how to return an html full of JS
    //todo: this is done in too hidden a manner.
    implicit val htmlWriter: RDFWriter[Sesame, Try, RDFaXHTML] = new RDFWriter[Sesame, Try, RDFaXHTML] {
      override val transformsTo: Syntax[RDFaXHTML] = Syntax.RDFaXHTML

      override def write(obj: Sesame#Graph, out: OutputStream, base: String) =
        Try {
          out.write(views.html.ldp.rdfToHtml().body.getBytes("UTF-8"))
        }

      override def asString(obj: Sesame#Graph, base: String) =
        Try {
          views.html.ldp.rdfToHtml().body
        }


    }

    WriterSelector[Sesame#Graph, Try, Turtle] combineWith
      WriterSelector[Sesame#Graph, Try, JsonLd] combineWith
      WriterSelector[Sesame#Graph, Try, JsonLdCompacted] combineWith
      WriterSelector[Sesame#Graph, Try, JsonLdExpanded] combineWith
      WriterSelector[Sesame#Graph, Try, JsonLdFlattened] combineWith
      WriterSelector[Sesame#Graph, Try, RDFXML] combineWith
      WriterSelector[Sesame#Graph, Try, RDFaXHTML]
  }


  implicit val solutionsWriterSelector: SparqlSolutionsWriterSelector[Rdf] = Sesame.sparqlSolutionsWriterSelector

  val webClient: WebClient[Rdf] =  new WSClient(Sesame.readerSelector,Sesame.turtleWriter)

  val rww: RWWActorSystem[Rdf] = {
    val rootURI = ops.URI(rwwRoot.toString)
    if (RdfSetup.rwwSubdomainsEnabled) RWWActorSystemImpl.withSubdomains[Rdf](rootURI, rootContainerPath, webClient)
    else RWWActorSystemImpl.plain[Rdf](rootURI, rootContainerPath, webClient)
  }

}


//class PlantainSetup extends RdfSetup {
//    type Rdf = Plantain
//
//    implicit val ops = Plantain.ops
//    implicit val sparqlOps = Plantain.sparqlOps
//    val blockingIteratee = new PlantainBlockingRDFIteratee
//    //note this writer selector also contains a writer for html that knows how to return an html full of JS
//    //todo: this is done in too hidden a manner. The writers should be rewritten using the Play request objects
//    // in order to allow more flexibility. Eg: one should be able to only server html if the client is a web browser
//    // (that accepts JS for example - if it were possible to determine that)
//    implicit val writerSelector = org.w3.banana.plantain.Plantain.writerSelector
//
//
//
//    //we don't have an iteratee selector for Plantain
//    implicit val iterateeSelector: IterateeSelector[Rdf#Graph] = blockingIteratee.BlockingIterateeSelector
//    implicit val sparqlSelector:  IterateeSelector[Rdf#Query] =  PlantainSparqlQueryIteratee.sparqlSelector
//    implicit val sparqlupdateSelector:  IterateeSelector[Rdf#UpdateQuery] =  PlantainSparqlUpdateIteratee.sparqlSelector
//    implicit val webClient: WebClient[Plantain] = new WSClient(Plantain.readerSelector,Plantain.turtleWriter)
//
//
//}


object RdfSetup extends SesameSetup with Setup

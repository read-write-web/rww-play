package controllers

import _root_.play.api.Logger
import _root_.play.api.Play
import akka.actor.{Props, ActorSystem}
import concurrent.ExecutionContext
import rww.play.rdf.IterateeSelector
import rww.ldp._
import org.w3.banana._
import scala.Some
import java.io.File
import java.nio.file.Path
import akka.util.Timeout
import java.util.concurrent.TimeUnit

//import rww.play.{IterateeLDCache, LinkedDataCache}
//import rww.play.auth.WebAccessControl
import play.api.{Play, Logger}
import java.net.URL
import org.w3.banana.plantain.Plantain
import rww.play.rdf.plantain.{PlantainSparqlUpdateIteratee, PlantainSparqlQueryIteratee, PlantainBlockingRDFIteratee}

/**
 * gather some common setup values
 **/
trait Setup {
  implicit val system = ActorSystem("MySystem")
  implicit val executionContext: ExecutionContext = system.dispatcher

  val logger = Logger("rww")
  val httpsPortKey = "https.port"
  val httpHostnameKey = "http.hostname"
  val RdfViewerHtmlTemplatePathKey = "rww.rdf.html.viewer.template.path"
  val RootContainerPathKey = "rww.root.container.path"
  val rwwSubDomainsEnabledKey = "rww.subdomains"
  val baseHostnameKey = "http.hostname"

  //Play setup: needed for WebID info
  //todo: the code below should be adapted to finding the real default of Play.
  lazy val securePort: Option[Int] = Play.current.configuration.getInt(httpsPortKey)

  lazy val port: Int = Play.current.configuration.getInt(httpsPortKey).orElse(securePort).get

  lazy val host: String =
    Play.current.configuration.getString(httpHostnameKey).getOrElse("localhost")


  lazy val hostRoot: URL = {
    val protocol = if (securePort==None) "http" else "https"
    new URL(protocol,host,port,"")
  }

  lazy val rwwRoot: URL =  {
    val path = controllers.routes.ReadWriteWebApp.about.url+"/"
    new URL(hostRoot,path)
  }

  lazy val rwwSubdomainsEnabled: Boolean = Play.current.configuration.getBoolean(rwwSubDomainsEnabledKey).getOrElse(false)

  /**
   * we check the existence of the file because Resource.fromFile creates the file if it doesn't exist
   * (the doc says it raises an exception but it's not the case)
   * @param key
   * @return
   */
  def getFileForConfigurationKey(key: String): File = {
    val path = Play.current.configuration.getString(key)
    require(path.isDefined,s"Missing configuration for key $key")
    val file = new File(path.get)
    require(file.exists() && file.canRead,s"Unable to find or read file/directory: $file")
    file
  }


  val rdfViewerHtmlTemplate: File = getFileForConfigurationKey(RdfViewerHtmlTemplatePathKey)



  val rootContainerPath: Path = {
    val file = getFileForConfigurationKey(RootContainerPathKey)
    require(file.isDirectory,s"The root container ($file) is not a directory")
    file.toPath.toAbsolutePath
  }


  logger.info(s""""
    secure port=$securePort
    port = $port
    host = $host
    hostRoot = $hostRoot
    rwwRoot = $rwwRoot
    rdfViewerHtmlTemplate = $rdfViewerHtmlTemplate
    rww root LDPC path = $rootContainerPath
    with subdomain support = $rwwSubdomainsEnabled
    """)
}

/**
 * sets the useful objects needed for running a web server.
 * This is the place to set to choose if running Jena or Sesame.
 * ( since the plantain library is not yet generalised ... )
 * todo: in fact one has to do a bit more, because we also have to
 *       select the best iteratees, etc... and that is Jena/Sesame specific
 */
//object setup extends Setup {
//  //
//  //when changing from Jena to Sesame the following group of variables would need to be changed
//  //
//
//  type Rdf = Jena
//  import Jena._
//  val jenaAsync = new JenaAsync
//  implicit val bestIterateeSlector: IterateeSelector[Rdf#Graph] = jenaAsync.graphIterateeSelector
//  implicit val JenaGraphFetcher: GraphFetcher[Jena] = new GraphFetcher[Jena](jenaAsync.graphIterateeSelector)
//  implicit def mkSparqlEngine = JenaGraphSparqlEngine.makeSparqlEngine _
//  implicit val ops = Jena.ops
//
//  //
//  // the following variables should be set correctly automatically when changing the value of Rdf above
//  //
//
//  implicit val wac: WebACL[Rdf] = WebACL[Rdf]
//  implicit val linkedDataCache: LinkedDataCache[Jena] = new IterateeLDCache[Jena](bestIterateeSlector)
//
//  //for WebAccessControl
//  implicit val JenaWebIDVerifier = new WebIDVerifier[Rdf]()
//  implicit val wacGuard: WebAccessControl[Rdf] = WebAccessControl[Rdf](linkedDataCache)
//}

object plantain extends Setup {
  type Rdf = Plantain

  implicit val ops = Plantain.ops
  implicit val sparqlOps = Plantain.sparqlOps
  val blockingIteratee = new PlantainBlockingRDFIteratee
  implicit val writerSelector : RDFWriterSelector[Plantain] =
     RDFWriterSelector[Plantain, Turtle] combineWith RDFWriterSelector[Plantain, RDFXML]

  implicit val solutionsWriterSelector = Plantain.solutionsWriterSelector

  //we don't have an iteratee selector for Plantain
  implicit val iterateeSelector: IterateeSelector[Plantain#Graph] = blockingIteratee.BlockingIterateeSelector
  implicit val sparqlSelector:  IterateeSelector[Plantain#Query] =  PlantainSparqlQueryIteratee.sparqlSelector
  implicit val sparqlupdateSelector:  IterateeSelector[Plantain#UpdateQuery] =  PlantainSparqlUpdateIteratee.sparqlSelector
  implicit val webClient: WebClient[Plantain] = new WSClient(Plantain.readerSelector,Plantain.turtleWriter)

  val rww: RWWeb[Plantain] = {
    val w = new RWWeb[Plantain](ops.URI(rwwRoot.toString))(ops,Timeout(30,TimeUnit.SECONDS))
    val rootActor = if (plantain.rwwSubdomainsEnabled)
      Props(new PlantainLDPCSubdomainActor(w.baseUri, rootContainerPath))
    else Props(new PlantainLDPCActor(w.baseUri, rootContainerPath))
    //, path,Some(Props(new PlantainWebProxy(base,Plantain.readerSelector))))
    val localActor = w.system.actorOf(rootActor,"rootContainer")
    w.setLDPSActor(localActor)
    val baseUri = ops.URI(rwwRoot.toString)

    val webActor = w.system.actorOf(Props(new LDPWebActor[Plantain](baseUri,webClient)),"webActor")
    w.setWebActor(webActor)
    w
  }


}


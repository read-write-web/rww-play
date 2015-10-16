package rww.ldp.actor.local

//import org.w3.banana.plantain.{LDPatch, PlantainLDPatch, Plantain}
import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.DirectoryStream.Filter
import java.nio.file.{Files, Path}

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
import com.google.common.base.Throwables
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import org.w3.banana.io.{RDFReader, RDFWriter, Turtle}
import org.w3.banana.{StoreProblem, _}
import rww.ldp.LDPExceptions._
import rww.ldp.actor.common.CommonActorMessages._
import rww.ldp.actor.common.RWWBaseActor
import rww.ldp.model._
import rww.ldp.{DeleteResource, GetMeta, GetResource, UpdateLDPR, _}
import rww.rdf.util.StatPrefix
import utils.FileUtils._

import scala.collection.convert.decorateAsScala._
import scala.io.Codec
import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/-}


class LDPRActor[Rdf<:RDF](val baseUri: Rdf#URI,path: Path)
                       (implicit ops: RDFOps[Rdf],
                         sparqlOps: SparqlOps[Rdf],
                         sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
                         reader: RDFReader[Rdf, Try, Turtle],
                         writer: RDFWriter[Rdf, Try, Turtle]
//                        adviceSelector: AdviceSelector[Rdf]= new EmptyAdviceSelector
                         ) extends RWWBaseActor {
  var ext = ".ttl"
  val acl = ".acl"

  import ops._

  val ldp = rww.rdf.util.LDPPrefix[Rdf]
  val rdfs = RDFSPrefix[Rdf]
  val rdf = RDFPrefix[Rdf]
  val stat = StatPrefix[Rdf]

  lazy val baseSprayUri = Uri(baseUri.toString)

  //google cache with soft values: at least it will remove the simplest failures
  val resourceCache: LoadingCache[String, Try[LocalNamedResource[Rdf]]] = CacheBuilder.newBuilder()
    .softValues()
    .build(new CacheLoader[String, Try[LocalNamedResource[Rdf]]]() {

   // import scalax.io.{Resource => xResource}

    def load(key: String) = {
      //at this point it is still very easy - only two cases! but won't stay like this...
      val (file, iri) = fileAndURIFor(key)

      if (file.exists()) {

        if (file.toString.endsWith(ext)) {
          using(new FileInputStream(file)) { in =>

            reader.read(in, iri.toString).map { g =>
              LocalLDPR[Rdf](iri, g, path)
            } recover {
              case RDFParseExceptionMatcher(e) => throw UnparsableSource(s"Can't parse resource $iri as an RDF file", e)
            }
          }
        } else Success(LocalBinaryResource[Rdf](file.toPath, iri))

      } else Failure(ResourceDoesNotExist(s"no resource for '$key'"))
    }
  })


  def filter = new Filter[Path]() {
    val fileName = path.getFileName.toString

    def accept(entry: Path) = {
      val ename = entry.getFileName.toString
      val result = matches(fileName, ename)
      result
    }

    def matches(fileName: String, entryName: String): Boolean = {
      entryName.startsWith(fileName) && (
        entryName.length == fileName.length ||
          entryName.charAt(fileName.length) == '.' ||
          (entryName.length >= fileName.length + 4 && entryName.substring(fileName.length, fileName.length + 4) == ".acl"
            && (entryName.length == fileName.length + 4 ||
            entryName.charAt(fileName.length + 4) == '.')
            )
        )
    }
  }

  override def preStart() {
    log.debug(s"creating PlantainLDPRActor($baseUri,$path)")
  }

  def fileName = path.getFileName.toString

  /**
   *
   * @param name the name of the file - with extensions.
   * @return
   */
  @throws[ResourceDoesNotExist]
  def getResource(name: String): Try[LocalNamedResource[Rdf]] = {
    //import scalax.io.{Resource => xResource}
    //todo: the file should be verified to see if it is up to date.
    val resourceGet = resourceCache.get(name) match {
      case Success(lnr) if (lnr.updated.filter(t=>path.toFile.lastModified()>t.getTime).isSuccess) => {
        resourceCache.invalidate(name)
        resourceCache.get(name)
      }
      case failure@Failure(exception: UnparsableSource) => {
        resourceCache.invalidate(name)
        failure
      }
      case otherResult => otherResult
    }
    log.debug(s"getResource with name=$name , found success?=${resourceGet.isSuccess}")
    if (resourceGet.isFailure) {
      log.error(resourceGet.failed.get, s"getResource with name=$name failure")
    }
    resourceGet
  }

  /**
   *
   * @param name
   * @return
   */
  private def fileAndURIFor(name: String): (File, Rdf#URI) = {
    //note: this is really simple at present, but is bound to get more complex,...
    val file = fileFrom(name)
    val iri = baseUri resolve URI(name)
    (file, iri)
  }

  /**
   * //todo: this feels like it is missing a parameter for mime types...
   * @param name the name of the requested resource
   * @return the file name on disk for it
   */
  def fileFrom(name: String): File = {
    if (name.endsWith(acl)) path.resolveSibling(name + ext).toFile
    else if (name.endsWith(acl + ext)) path.resolveSibling(name).toFile
    else if (Files.isSymbolicLink(path)) path.resolveSibling(Files.readSymbolicLink(path)).toFile
    else path.resolveSibling(name + ext).toFile
  }

  def setResource(name: String, graph: Rdf#Graph) {
    implicit val codec = Codec.UTF8
    val (file, iri) = fileAndURIFor(name)
    file.createNewFile()
    val cleanGr = rww.rdf.util.GraphUtil.normalise(baseSprayUri, graph)
    val dirUri = baseUri.toString.substring(0, baseUri.toString.length - baseUri.lastPathSegment.length)
    using(new FileOutputStream(file)) { out =>
      writer.write(cleanGr, out, dirUri) match {
        case scala.util.Failure(t) => throw new StoreProblem(t)
        case x => x
      }
    }
    resourceCache.put(name, Success(LocalLDPR[Rdf](iri, graph, file.toPath)))
  }


  def localName(uri: Rdf#URI): String = uri.lastPathSegment

  /*
     * Runs a command that can be evaluated on this container.
     * @param cmd the command to evaluate
     * @tparam A The final return type of the script
     * @return a script for further evaluation
     */
  def runLocalCmd[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf, A]]) {
    log.debug(s"received $cmd")
    cmd match {
      case GetResource(uri, agent, k) => {
        getResource(localName(uri)) match {
          case Success(res) => rwwRouterActor.tell(ScriptMessage(k(res)), context.sender)
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case GetMeta(uri, k) => {
        //todo: GetMeta here is very close to GetResource, as currently there is no big work difference between the two
        //The point of GetMeta is mostly to remove work if there were work that was very time
        //consuming ( such as serialising a graph )
        getResource(localName(uri)) match {
          case Success(res) => rwwRouterActor.tell(ScriptMessage(k(res)), context.sender)
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }

      }
      case DeleteResource(uri, a) => {
        val pathStream = Files.newDirectoryStream(path.getParent, filter)
        try {
          for (p <- pathStream.asScala) {
            Files.delete(p)
          }
        } finally {
          pathStream.close()
        }
        context.stop(self)
        rwwRouterActor.tell(ScriptMessage(a), context.sender)
      }
      case UpdateLDPR(uri, remove, add, a) => {
        val nme = localName(uri)
        getResource(nme) match {
          case Success(ldpr: LocalLDPR[Rdf]) => {
            if (remove.size > 0) throw LocalException("need to upgrade to a later version of banana that supports diffs")
            //            val temp = remove.foldLeft(graph) {
            //              (graph, tripleMatch) => graph - tripleMatch.resolveAgainst(uriW[Plantain](uri).resolveAgainst(baseUri))
            //            }
            val graphName = baseUri.resolve(URI(nme))
            val resultGraph = add.foldLeft(ldpr.graph) {
              (graph, triple) => graph union Graph(triple.resolveAgainst(graphName))
            }
            setResource(nme, resultGraph)
            rwwRouterActor.tell(ScriptMessage(a), context.sender)
          }
          // TODO to verify for @bblfish: code duplicated from the LocalLDPR case! see #116
          case Success(ldpc: LocalLDPC[Rdf]) => {
            if (remove.size > 0) throw LocalException("need to upgrade to a later version of banana that supports diffs")
            //            val temp = remove.foldLeft(graph) {
            //              (graph, tripleMatch) => graph - tripleMatch.resolveAgainst(uriW[Plantain](uri).resolveAgainst(baseUri))
            //            }
            val graphName = URI(nme).resolve(baseUri)
            val resultGraph = add.foldLeft(ldpc.graph) {
              (graph, triple) => graph union Graph(triple.resolveAgainst(graphName))
            }
            setResource(nme, resultGraph)
            rwwRouterActor.tell(ScriptMessage(a), context.sender)
          }
          case Success(_) => throw RequestNotAcceptable(s"$uri does not contain a GRAPH, cannot Update")
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case PutLDPR(uri, graph, a) => {
        val nme = localName(uri)
        setResource(nme, graph)
        rwwRouterActor.tell(ScriptMessage(a), context.sender)
      }
// this would allow one to change a resource to a binary in one atomic operation
//      case PutBinary(uri, mimetype, tempFile, a) = {
//         1. delete the previous resource, and all the attached files
//      }
      case PatchLDPR(uri, update, bindings, k) => {
        val nme = localName(uri)
        getResource(nme) match {
          case Success(ldpr: LocalLDPR[Rdf]) => {
            sparqlGraph.executeUpdate(ldpr.graph, update, bindings) match {
              case Success(gr) => {
                setResource(nme, gr)
                rwwRouterActor.tell(ScriptMessage(k(true)), context.sender)
              }
              case Failure(e) => throw e
            }
          }
          case Success(_) => context.sender ! RequestNotAcceptable(s"$uri does not contain a GRAPH - PATCH is not possible")
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case SelectLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)) match {
          case Success(ldpr: LocalLDPR[Rdf]) => {
            sparqlGraph.executeSelect( ldpr.graph, query, bindings).map { solutions =>
              rwwRouterActor.tell(ScriptMessage(k(solutions)), context.sender)
            }.failed.map { e =>
              context.sender ! akka.actor.Status.Failure(e)
            }
          }
          case Success(_) => context.sender ! RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case ConstructLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)).get match {
          case ldpr: LocalLDPR[Rdf] =>
            sparqlGraph.executeConstruct(ldpr.graph, query, bindings).map { result =>
              rwwRouterActor.tell(ScriptMessage(k(result)), context.sender)
            }.failed.map { e =>
              context.sender ! akka.actor.Status.Failure(e)
            }
          case _ => throw RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
        }

      }
      case AskLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)).get match {
          case ldpr: LocalLDPR[Rdf] => {
            sparqlGraph.executeAsk(ldpr.graph,query, bindings).map { result =>
              rwwRouterActor.tell(ScriptMessage(k(result)), context.sender)
            }.failed.map { e =>
              context.sender ! akka.actor.Status.Failure(e)
            }
          }
          case _ => throw RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
        }
      }
      case cmd => throw RequestNotAcceptable(s"Cannot run ${cmd.getClass} on an LDPR that is not an LDPC ")
    }
  }

  /**
   *
   * @param script
   * @tparam A
   * @throws NoSuchElementException if the resource does not exist
   * @return
   */
  final def run[A](sender: ActorRef, script: LDPCommand.Script[Rdf,A]) {
    script.resume match {
      case -\/(cmd) => {
        if(cmd.uri == baseUri) {
          adviceCmd(cmd)
        }
        else {
          log.info(s"sending to $rwwRouterActor")
          rwwRouterActor.tell(CmdMessage(cmd),context.sender)
        }
      }
      case \/-(a) => {
        log.info(s"returning to $sender $a")
        sender ! a
      }
    }
  }


  def adviceCmd[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]]) {
    //todo: improve for issues of extensions ( eg. .n3, ... )
    //    val advices = adviceSelector(getResource(fileName))
    //    advices foreach ( _.pre(cmd) map { throw _ } )
    runLocalCmd(cmd)
    //    advices foreach (_.post(cmd))
  }


  def receive = returnErrors {
    case s: ScriptMessage[Rdf,_]  => {
      run(sender, s.script)
    }
    case cmd: CmdMessage[Rdf,_] => {
      adviceCmd(cmd.command)
    }
  }
}


// maybe this can be removed soon, just a workaround until banana is improved.
// see https://github.com/w3c/banana-rdf/issues/80
object RDFParseExceptionMatcher {
  private val ExceptionClass = classOf[org.openrdf.rio.RDFParseException]

  def unapply(t: Throwable): Option[Throwable] = {
    val list = Throwables.getCausalChain(t).asScala.filter(ex => ExceptionClass.isAssignableFrom(ex.getClass))
    // this may be strange that we return t and not list.headOption but we want to have the whole stacktrace...
    if (!list.isEmpty) Some(t)
    else None
  }
}




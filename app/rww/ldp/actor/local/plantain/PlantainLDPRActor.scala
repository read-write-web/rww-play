package rww.ldp.actor.plantain

import org.w3.banana.plantain.{PlantainLDPatch, Plantain}
import java.nio.file.{Files, Path}
import org.w3.banana._
import com.google.common.cache.{CacheLoader, CacheBuilder, LoadingCache}
import scala.util.Try
import java.util.Date
import java.nio.file.DirectoryStream.Filter
import java.io.{FileOutputStream, File}
import scala.io.Codec
import scala._
import akka.actor.ActorRef
import rww.ldp._
import com.google.common.base.Throwables
import scala.collection.convert.decorateAsScala._
import rww.ldp.DeleteResource
import rww.ldp.GetMeta
import rww.ldp.PatchLDPR
import scala.util.Failure
import scala.Some
import org.w3.banana.StoreProblem
import scala.util.Success
import scalaz.\/-
import rww.ldp.AskLDPR
import rww.ldp.ConstructLDPR
import scalaz.-\/
import rww.ldp.UpdateLDPR
import rww.ldp.LDPExceptions._
import rww.ldp.GetResource
import rww.ldp.SelectLDPR
import rww.ldp.actor.common.CommonActorMessages
import CommonActorMessages._
import rww.ldp.actor.common.RWWBaseActor
import rww.ldp.model._


class PlantainLDPRActor(val baseUri: Plantain#URI,path: Path)
                       (implicit ops: RDFOps[Plantain],
                        sparqlGraph: SparqlGraph[Plantain],
                        reader: RDFReader[Plantain,Turtle],
                        writer: RDFWriter[Plantain,Turtle],
                        adviceSelector: AdviceSelector[Plantain]= new EmptyAdviceSelector
                         ) extends RWWBaseActor {
  var ext = ".ttl"
  val acl = ".acl"

  val ldp = LDPPrefix[Plantain]
  val rdfs = RDFSPrefix[Plantain]
  val rdf = RDFPrefix[Plantain]
  val stat = STATPrefix[Plantain]

  val mimeExt = WellKnownMimeExtensions

  import org.w3.banana.syntax._

  //google cache with soft values: at least it will remove the simplest failures
  val resourceCache: LoadingCache[String,Try[LocalNamedResource[Plantain]]] = CacheBuilder.newBuilder()
    .softValues()
    .build(new CacheLoader[String,Try[LocalNamedResource[Plantain]]]() {
    import scalax.io.{Resource=>xResource}

    def load(key: String) = {
      //at this point it is still very easy - only two cases! but won't stay like this...
      val (file, iri) = fileAndURIFor(key)

      if (file.exists()) {

        if (file.toString.endsWith(ext)) {
          val res = xResource.fromFile(file)
          reader.read(res, iri.toString).map { g =>
            LocalLDPR[Plantain](iri, g, path, Option(new Date(path.toFile.lastModified())))
          } recover {
            case RDFParseExceptionMatcher(e) => throw UnparsableSource(s"Can't parse resource $iri as an RDF file",e)
          }
        } else Success(LocalBinaryResource[Plantain](file.toPath, iri))

      } else Failure(ResourceDoesNotExist(s"no resource for '$key'"))
    }
  })



  def filter = new Filter[Path]() {
    val fileName = path.getFileName.toString
    def accept(entry: Path) = {
      val ename = entry.getFileName.toString
      val result = matches(fileName,ename)
      result
    }

    def matches(fileName: String, entryName: String): Boolean = {
      entryName.startsWith(fileName) && (
        entryName.length == fileName.length ||
          entryName.charAt(fileName.length) == '.' ||
          (entryName.length >= fileName.length+4 && entryName.substring(fileName.length, fileName.length + 4) == ".acl"
            && ( entryName.length == fileName.length+4 ||
            entryName.charAt(fileName.length+4) == '.' )
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
   * @throws ResourceDoesNotExist
   * @return
   */
  @throws[ResourceDoesNotExist]
  def getResource(name: String): Try[LocalNamedResource[Plantain]] = {
    import scalax.io.{Resource=>xResource}
    //todo: the file should be verified to see if it is up to date.
    val resourceGet = resourceCache.get(name) match {
      case success @ Success(LocalLDPR(_,_,path,updated)) if (path.toFile.lastModified() > updated.get.getTime) => {
        resourceCache.invalidate(name)
        success
      }
      case failure @ Failure(exception: UnparsableSource) => {
        resourceCache.invalidate(name)
        failure
      }
      case otherResult => otherResult
    }
    log.debug(s"getResource with name=$name , found success?=${resourceGet.isSuccess}")
    if ( resourceGet.isFailure ) {
      log.error(resourceGet.failed.get,s"getResource with name=$name failure")
    }
    resourceGet
  }

  /**
   *
   * @param name
   * @return
   */
  private def fileAndURIFor(name: String): (File,Plantain#URI) = {
    //note: this is really simple at present, but is bound to get more complex,...
    val file = fileFrom(name)
    val uriw =uriW[Plantain](baseUri)
    val iri = uriw.resolve(name)
    (file,iri)
  }

  /**
   * //todo: this feels like it is missing a parameter for mime types...
   * @param name the name of the requested resource
   * @return the file name on disk for it
   */
  def fileFrom(name: String): File = {
    if (name.endsWith(acl)) path.resolveSibling(name + ext).toFile
    else if (name.endsWith(acl+ext)) path.resolveSibling(name).toFile
    else if (Files.isSymbolicLink(path)) path.resolveSibling(Files.readSymbolicLink(path)).toFile
    else path.resolveSibling(name + ext).toFile
  }

  def setResource(name: String, graph: Plantain#Graph) {
    import scalax.io.{Resource=>xResource}
    implicit val codec = Codec.UTF8
    val (file,iri) = fileAndURIFor(name)
    file.createNewFile()
    writer.write(graphW[Plantain](graph).relativize(baseUri),xResource.fromOutputStream(new FileOutputStream(file)),"") match {
      case scala.util.Failure(t) => throw new StoreProblem(t)
      case x => x
    }
    resourceCache.put(name,Success(LocalLDPR[Plantain](iri,graph, file.toPath, Some(new Date(file.lastModified())))))
  }


  def localName(uri: Plantain#URI): String = uriW[Plantain](uri).lastPathSegment

  /*
     * Runs a command that can be evaluated on this container.
     * @param cmd the command to evaluate
     * @tparam A The final return type of the script
     * @return a script for further evaluation
     */
  def runLocalCmd[A](cmd: LDPCommand[Plantain, LDPCommand.Script[Plantain,A]]) {
    log.debug(s"received $cmd")
    cmd match {
      case GetResource(uri, agent, k) => {
        getResource(localName(uri)) match {
          case Success(res) => rwwRouterActor.tell(ScriptMessage(k(res)),context.sender)
          case Failure(fail) =>  context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case GetMeta(uri, k) => {
        //todo: GetMeta here is very close to GetResource, as currently there is no big work difference between the two
        //The point of GetMeta is mostly to remove work if there were work that was very time
        //consuming ( such as serialising a graph )
        getResource(localName(uri)) match {
          case Success(res) => rwwRouterActor.tell(ScriptMessage(k(res)),context.sender)
          case Failure(fail) =>  context.sender ! akka.actor.Status.Failure(fail)
        }

      }
      case DeleteResource(uri, a) => {
        val pathStream = Files.newDirectoryStream(path.getParent,filter)
        try {
          for (p <- pathStream.asScala) {
            Files.delete(p)
          }
        } finally {
          pathStream.close()
        }
        context.stop(self)
        rwwRouterActor.tell(ScriptMessage(a),context.sender)
      }
      case UpdateLDPR(uri, remove, add, a) => {
        val nme = localName(uri)
        getResource(nme) match {
          case Success(LocalLDPR(_,graph,_,updated)) => {
            val temp = remove.foldLeft(graph) {
              (graph, tripleMatch) => graph - tripleMatch.resolveAgainst(uriW[Plantain](uri).resolveAgainst(baseUri))
            }
            val resultGraph = add.foldLeft(temp) {
              (graph, triple) => graph + triple.resolveAgainst(uriW[Plantain](uri).resolveAgainst(baseUri))
            }
            setResource(nme,resultGraph)
            rwwRouterActor.tell(ScriptMessage(a),context.sender)
          }
          case Success(_) => throw RequestNotAcceptable(s"$uri does not contain a GRAPH, cannot Update")
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case PatchLDPR(uri, update, bindings, k) => {
        val nme = localName(uri)
        getResource(nme) match {
          case Success(LocalLDPR(_,graph,_,updated)) => {
            PlantainLDPatch.executePatch(graph,update,bindings) match {
              case Success(gr) => {
                setResource(nme, gr)
                rwwRouterActor.tell(ScriptMessage(k(true)),context.sender)
              }
              case Failure(e) => throw e
            }
          }
          case Success(_) =>  context.sender ! RequestNotAcceptable(s"$uri does not contain a GRAPH - PATCH is not possible")
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case SelectLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)) match {
          case Success(LocalLDPR(_,graph,_,_)) => {
            val solutions = sparqlGraph(graph).executeSelect(query, bindings)
            rwwRouterActor.tell(ScriptMessage(k(solutions)),context.sender)
          }
          case Success(_) => context.sender ! RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
          case Failure(fail) => context.sender ! akka.actor.Status.Failure(fail)
        }
      }
      case ConstructLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)).get match {
          case LocalLDPR(_,graph,_,_) => {
            val result = sparqlGraph(graph).executeConstruct(query, bindings)
            rwwRouterActor.tell(ScriptMessage(k(result)),context.sender)
          }
          case _ => throw RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
        }

      }
      case AskLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)).get match {
          case LocalLDPR(_,graph,_,_) => {
            val result = sparqlGraph(graph).executeAsk(query, bindings)
            rwwRouterActor.tell(ScriptMessage(k(result)),context.sender)
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
  final def run[A](sender: ActorRef, script: LDPCommand.Script[Plantain,A]) {
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


  def adviceCmd[A](cmd: LDPCommand[Plantain, LDPCommand.Script[Plantain,A]]) {
    //todo: improve for issues of extensions ( eg. .n3, ... )
    //    val advices = adviceSelector(getResource(fileName))
    //    advices foreach ( _.pre(cmd) map { throw _ } )
    runLocalCmd(cmd)
    //    advices foreach (_.post(cmd))
  }


  def receive = returnErrors {
    case s: ScriptMessage[Plantain,_]  => {
      run(sender, s.script)
    }
    case cmd: CmdMessage[Plantain,_] => {
      adviceCmd(cmd.command)
    }
  }
}


// maybe this can be removed soon, just a workaround until banana is improved.
// see https://github.com/w3c/banana-rdf/issues/80
object RDFParseExceptionMatcher {
  private val ExceptionClass = classOf[org.openrdf.rio.RDFParseException]
  def unapply(t: Throwable) = {
    val list = Throwables.getCausalChain(t).asScala.filter(ex => ExceptionClass.isAssignableFrom(ex.getClass))
    // this may be strange that we return t and not list.headOption but we want to have the whole stacktrace...
    if ( !list.isEmpty ) Some(t)
    else None
  }
}

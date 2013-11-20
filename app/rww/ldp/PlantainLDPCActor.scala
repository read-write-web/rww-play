package rww.ldp

import scala.language.reflectiveCalls

import org.w3.banana._
import java.nio.file._
import akka.actor._
import java.net.{URI => jURI}
import org.w3.banana.plantain.{PlantainLDPatch, Plantain}
import java.nio.file.attribute.BasicFileAttributes
import java.util
import scalaz.-\/
import scalaz.\/-
import scala.Some
import java.nio.file.Path
import scala.util.{Try, Success, Failure}
import java.util.Date
import scala.io.Codec
import java.io.{File, FileOutputStream}
import java.nio.file.DirectoryStream.Filter
import org.w3.banana.diesel.Diesel
import com.google.common.cache._
import java.util.concurrent.{TimeUnit, Callable}
import scalax.io.Resource

/**
 * A LDP Container actor that is responsible for the equivalent of a directory
 *
 *
 * @param baseUri the URI for the container
 * @param root the path on the file system where data is saved to
 * @param ops
 * @param sparqlGraph
 */
class PlantainLDPCActor(baseUri: Plantain#URI, root: Path)
                                 (implicit ops: RDFOps[Plantain],
                                     sparqlGraph: SparqlGraph[Plantain],
                                     adviceSelector: AdviceSelector[Plantain]=new EmptyAdviceSelector) extends PlantainLDPRActor(baseUri,root) {
  import org.w3.banana.syntax._
  import ops._
  import diesel._

  override lazy val fileName = ""

  override def preStart {
     //start all agents for all files and subdirectories
    //start starting directories lazily may in many cases be a better option, especially for systems with a huge
    //number of directories.... http://stackoverflow.com/questions/16633515/creating-akka-actor-hierarchies-lazily
    //currently we do no optimization, just to make code simpler. Optimization to come later.
    //todo: memory optimizations
    //todo: handle exceptions
    //todo: deal with index file...
    Files.walkFileTree(root,util.Collections.emptySet(), 1,
      new SimpleFileVisitor[Path] {

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
          if (dir == root) super.preVisitDirectory(dir,attrs)
          else FileVisitResult.SKIP_SUBTREE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          val pathSegment = file.getFileName.toString
          if (attrs.isDirectory) {
            context.actorOf(Props(new PlantainLDPCActor(absoluteUri(pathSegment+"/"),root.resolve(file))),pathSegment)
          } else if (attrs.isSymbolicLink) {
            //we use symbolic links to point to the file that contains the default representation
            //this is because we may have many different representations, and we don't want an actor
            //for each of the representations.
            context.actorOf(Props(new PlantainLDPRActor(absoluteUri(pathSegment),root.resolve(file))),pathSegment)
          }
        FileVisitResult.CONTINUE
      }
    })
  }

  override
  def localName(uri: Plantain#URI): String = {
    val requestedPath = uri.underlying.getPath
    val ldpcPath = baseUri.underlying.getPath
    if (ldpcPath.length > requestedPath.length) fileName
    else super.localName(uri)
  }

  private def descriptionFor(path: Path, attrs: BasicFileAttributes): Plantain#Graph = {
    def graphFor(uri: Plantain#URI) = {
      var res = emptyGraph +
        Triple(baseUri, ldp.created, uri) +
        Triple(uri, stat.mtime, TypedLiteral(attrs.lastModifiedTime().toMillis.toString,xsd.integer))
      if (attrs.isDirectory)
        res + Triple(uri, rdf.typ, ldp.Container)
      else if (attrs.isSymbolicLink) {
        val target: Path = root.resolve(Files.readSymbolicLink(path))
        res + Triple(uri, stat.size, TypedLiteral((target.toFile.length()).toString, xsd.integer))
      } else {
        res
      }
    }
    if (attrs.isDirectory) {
      graphFor(absoluteUri(path.getFileName+"/"))
    } else if (attrs.isSymbolicLink){
      graphFor(absoluteUri(path.getFileName.toString))
    } else Graph.empty
  }

  private def absoluteUri(pathSegment: String) =  uriW[Plantain](baseUri)/pathSegment

  /**
   *
   * @param name the name of the file ( only the index file in the case of LPDCs
   * @throws ResourceDoesNotExist
   * @return
   */
  override def getResource(name: String): Try[LocalNamedResource[Plantain]] = {
    super.getResource(name) match {
      case ok @ Success(ldpr: LocalLDPR[Plantain]) => {
        if (name == fileName) {
          //if this is the index file, add all the content info
          var contentGrph = ldpr.graph + Triple(baseUri, rdf.typ, ldp.Container)
          Files.walkFileTree(root, util.Collections.emptySet(), 1,
            new SimpleFileVisitor[Path] {

              override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
                if (dir == root) super.preVisitDirectory(dir, attrs)
                else FileVisitResult.SKIP_SUBTREE
              }

              override def visitFile(file: Path, attrs: BasicFileAttributes) = {
                contentGrph = contentGrph union descriptionFor(file, attrs)
                FileVisitResult.CONTINUE
              }
            })
          Success(LocalLDPR[Plantain](baseUri, contentGrph, root, Some(new Date(Files.getLastModifiedTime(root).toMillis))))
        } else ok
      }
      case badContent @ Success(_) =>  Failure(StorageError(s"Data in LDPC must be a graph. "))
      case err => err
    }
  }

  def randomPathSegment(): String = java.util.UUID.randomUUID().toString.replaceAll("-", "")

  /**
   * for directories we resolve requests for metadata files as referring to .xxx.ext files
   * inside the directory
   *
   * @param name
   * @return
   */
  override
  def fileFrom(name: String): File = {
    //note any request to this method means that the initial request started with collection.xxxx
    //so we know that the first part of the string is a collection
    val doti = name.indexOf('.')
    val afterDot = if (doti>=0) name.substring(doti) else ""
    root.resolve(afterDot+ext).toFile
  }


  /**
   * Runs a command that can be evaluated on this container.
   * @param cmd the command to evaluate
   * @tparam A The final return type of the script
   * @return a script for further evaluation
   */
  override
  def runLocalCmd[A](cmd: LDPCommand[Plantain, LDPCommand.Script[Plantain,A]]) {
    log.info(s"in PlantainLDPCActor.runLocalCmd - received $cmd")

    cmd match {
      case CreateLDPR(_, slugOpt, graph, k) => {
        val (uri, path) = mkFile(slugOpt, ext)

        val (actor, iri) = try {
          val actor = context.actorOf(Props(new PlantainLDPRActor(uri, path)), path.getFileName.toString)
          (actor, uri)
        } catch {
          case e: InvalidActorNameException => {
            val (uri2, path2) = mkFile(slugOpt, ext)
            Files.deleteIfExists(Files.readSymbolicLink(path))
            Files.delete(path)
            val actor = context.actorOf(Props(new PlantainLDPRActor(uri2, path2)), path2.getFileName.toString)
            (actor, uri2)
          }
        }

        //todo: move this into the resource created ( it should know on creation how to find the parent )
        val linkedGraph = graph + Triple(baseUri, ldp.created, iri)

        //todo: should these be in the header?
        val scrpt = LDPCommand.updateLDPR[Plantain](iri, add = graphToIterable(linkedGraph)).flatMap(_ => k(iri))
        actor forward Scrpt(scrpt)
      }
      case CreateBinary(_, slugOpt, mime: MimeType, k) => {
        mimeExt.extension(mime).map { ext =>
          val (uri, path) = mkFile(slugOpt, ext)
          val (actor,iri) = try {
            val actor = context.actorOf(Props(new PlantainLDPRActor(uri,path)),path.getFileName.toString)
            (actor,uri)
          } catch {
            case e: InvalidActorNameException => {
              val (uri2,path2) = mkFile(slugOpt,ext)
              Files.deleteIfExists(Files.readSymbolicLink(path))
              Files.delete(path)
              val actor = context.actorOf(Props(new PlantainLDPRActor(uri2,path2)),path2.getFileName.toString)
              (actor, uri2)
            }
          }
          val s = LDPCommand.getResource[Plantain,NamedResource[Plantain]](iri)
          actor forward Scrpt(s.flatMap{
            case br: BinaryResource[Plantain] =>k(br)
            case x => throw UnsupportedMediaType("was looking for a BinaryResource but received a "+x.getClass)//todo: not the right error code
          })
          //todo: make sure the uri does not end in ";aclPath" or whatever else the aclPath standard will be
        } getOrElse(throw UnsupportedMediaType("we do not yet support "+mime))
      }
      case CreateContainer(_,slugOpt,graph,k) => {
        val (uri, pathSegment) = mkDir(slugOpt)
        val p = root.resolve(pathSegment)
        val dirUri = uriW[Plantain](uri) / ""
        val ldpc = context.actorOf(Props(new PlantainLDPCActor(dirUri, p)), pathSegment.getFileName.toString)
        val creationRel = Triple(baseUri, ldp.created, dirUri)
        val linkedGraph = graph + creationRel
        //todo: should these be in the header?
        val scrpt = LDPCommand.updateLDPR[Plantain](dirUri, add = graphToIterable(linkedGraph)).flatMap(_ => k(dirUri))
        ldpc forward Scrpt(scrpt)
      }
      case DeleteResource(uri, a) => {
//        val name = uriW[Plantain](uri).lastPathSegment
        log.info(s"DeleteResource($uri,$a) Resource is a Container")
        if (context.children.size == 0 ) { //delete all special directory files
          Files.walkFileTree(root,new SimpleFileVisitor[Path]() {
            override def visitFile(file: Path, attrs: BasicFileAttributes) = {
              Files.delete(file)
              super.visitFile(file, attrs)
            }
          })
          Files.delete(root)
          context.stop(self)
        } else {
          throw PreconditionFailed("Can't delete a container that has remaining members")
        }
        context.stop(self)
        rwwActor.tell(Scrpt(a),context.sender)
      }
      case _ => super.runLocalCmd(cmd)
//      case SelectLDPC(_,query, bindings, k) => {
//        val solutions = PlantainUtil.executeSelect(tripleSource, query, bindings)
//        k(solutions)
//      }
//      case ConstructLDPC(_,query, bindings, k) => {
//        val graph = PlantainUtil.executeConstruct(tripleSource, query, bindings)
//        k(graph)
//      }
//      case AskLDPC(_,query, bindings, k) => {
//        val b = PlantainUtil.executeAsk(tripleSource, query, bindings)
//        k(b)
//      }
    }
  }


  /**
   * creates a file from the slug, and returns the URI and path for it.
   * @param slugOpt, optional file name
   * @param ext for the extension of the file, should not be the "" string
   **/
  protected def mkFile[A](slugOpt: Option[String], ext: String): (Plantain#URI, Path) = {
    assert (ext != "")
    def mkTmpFile: Path = {
      val file = Files.createTempFile(root, "r_", ext)
      val name = file.getFileName.toString
      val link = root.resolve(name.substring(0, name.length - ext.length))
      Files.createSymbolicLink(link, file)
      val aclFile = link.resolveSibling(link.getFileName.toString+acl+ext)
      Files.createFile(aclFile)
      link
    }
    val path = slugOpt match {
      case None =>  mkTmpFile
      case Some(slug) => {
        val safeSlug = slug.replaceAll("[/.]+", "_")
        val slugLink = root.resolve(safeSlug)
        val slugFile =  slugLink.resolveSibling(slugLink.getFileName.toString+ext)
        val slugAcl = slugLink.resolveSibling(slugLink.getFileName.toString+acl+ext)
        if (Files.exists(slugLink, LinkOption.NOFOLLOW_LINKS)
          || Files.exists(slugFile)
          || Files.exists(slugLink)) {
          mkTmpFile
        } else {
          Files.createFile(slugFile)
          Files.createFile(slugAcl)
          Files.createSymbolicLink(slugLink, slugFile.getFileName)
        }
      }
    }
    val uri = uriW[Plantain](baseUri) / path.getFileName.toString
    (uri, path)

  }

  /**
   * creates a dir/collection from the slug, and returns the URI and path for it.
   * @param slugOpt, optional file name
   **/
  protected def mkDir[A](slugOpt: Option[String]): (Plantain#URI, Path) = {
    val path = slugOpt match {
      case None =>  Files.createTempDirectory(root,"d")
      case Some(slug) => {
        val safeSlug = slug.replaceAll("[/.]+", "_")
        val slugFile = root.resolve(safeSlug)
        if (Files.exists(slugFile))
          Files.createTempDirectory(root,"d"+safeSlug)
        else {
          Files.createDirectory(slugFile)
        }
      }
    }
    Files.createFile(path.resolve(ext))
    Files.createFile(path.resolve(acl+ext))
    val uri = uriW[Plantain](baseUri) / path.getFileName.toString
    (uri, path)

  }


}


class PlantainLDPRActor(val baseUri: Plantain#URI,path: Path)
                       (implicit ops: RDFOps[Plantain],
                                 sparqlGraph: SparqlGraph[Plantain],
                                 reader: RDFReader[Plantain,Turtle],
                                 writer: RDFWriter[Plantain,Turtle],
                                 adviceSelector: AdviceSelector[Plantain]= new EmptyAdviceSelector
                       ) extends RActor {
  var ext = ".ttl"
  val acl = ".acl"

  val ldp = LDPPrefix[Plantain]
  val rdfs = RDFSPrefix[Plantain]
  val rdf = RDFPrefix[Plantain]
  val stat = STATPrefix[Plantain]

  val mimeExt = WellKnownMimeExtensions

  import org.w3.banana.syntax._
  import ops._
  import scala.collection.convert.decorateAsScala._

  //google cache with soft values: at least it will remove the simplest failures
  val cacheg: LoadingCache[String,Try[LocalNamedResource[Plantain]]] = CacheBuilder.newBuilder()
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
          }
        } else Success(LocalBinaryR[Plantain](file.toPath, iri))

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
    println(s"~~~~~> creating PlantainLDPRActor($baseUri,$path) ~~~")
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
    cacheg.get(name) match {
      case Success(LocalLDPR(_,_,path,updated)) if (path.toFile.lastModified() > updated.get.getTime) => {
        cacheg.invalidate(name)
        cacheg.get(name)
      }
      case ldpr => ldpr
    }
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
    cacheg.put(name,Success(LocalLDPR[Plantain](iri,graph, file.toPath, Some(new Date(file.lastModified())))))
  }


  def localName(uri: Plantain#URI): String = uriW[Plantain](uri).lastPathSegment

  /*
     * Runs a command that can be evaluated on this container.
     * @param cmd the command to evaluate
     * @tparam A The final return type of the script
     * @return a script for further evaluation
     */
  def runLocalCmd[A](cmd: LDPCommand[Plantain, LDPCommand.Script[Plantain,A]]) {
    log.info(s"in PlantainLDPRActor.runLocalCmd - received $cmd")


    cmd match {
      case GetResource(uri, agent, k) => {
        val res = getResource(localName(uri)).get
        self forward Scrpt(k(res))
      }
      case GetMeta(uri, k) => {
        //todo: GetMeta here is very close to GetResource, as currently there is no big work difference between the two
        //The point of GetMeta is mostly to remove work if there were work that was very time
        //consuming ( such as serialising a graph )
        getResource(localName(uri)) match {
          case Success(res) => self forward Scrpt(k(res))
          case Failure(fail) =>  context.sender ! fail
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
        rwwActor.tell(Scrpt(a),context.sender)
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
            self forward Scrpt(a)
          }
          case Success(_) => throw RequestNotAcceptable(s"$uri does not contain a GRAPH, cannot Update")
          case Failure(fail) => context.sender ! fail
        }
      }
      case PatchLDPR(uri, update, bindings, k) => {
        val nme = localName(uri)
        getResource(nme) match {
          case Success(LocalLDPR(_,graph,_,updated)) => {
            PlantainLDPatch.executePatch(graph,update,bindings) match {
              case Success(gr) => {
                setResource(nme, gr)
                self forward Scrpt(k(true))
              }
              case Failure(e) => throw e
             }
          }
          case Success(_) =>  context.sender ! RequestNotAcceptable(s"$uri does not contain a GRAPH - PATCH is not possible")
          case Failure(fail) => context.sender ! fail
        }
      }
      case SelectLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)) match {
          case Success(LocalLDPR(_,graph,_,_)) => {
            val solutions = sparqlGraph(graph).executeSelect(query, bindings)
            self forward Scrpt(k(solutions))
          }
          case Success(_) => context.sender ! RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
          case Failure(fail) => context.sender ! fail
        }
      }
      case ConstructLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)).get match {
          case LocalLDPR(_,graph,_,_) => {
            val result = sparqlGraph(graph).executeConstruct(query, bindings)
            self forward Scrpt(k(result))
          }
          case _ => throw RequestNotAcceptable(s"$uri does not contain a GRAPH - SELECT is not possible")
        }

      }
      case AskLDPR(uri, query, bindings, k) => {
        getResource(localName(uri)).get match {
          case LocalLDPR(_,graph,_,_) => {
            val result = sparqlGraph(graph).executeAsk(query, bindings)
            self forward Scrpt(k(result))
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
          log.info(s"sending to $rwwActor")
          rwwActor.tell(Cmd(cmd),context.sender)
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

  lazy val rwwActor= context.actorSelection("/user/rww")

  def receive = returnErrors {
    case s: Scrpt[Plantain,_]  => {
      run(sender, s.script)
    }
    case cmd: Cmd[Plantain,_] => {
      adviceCmd(cmd.command)
    }
  }
}

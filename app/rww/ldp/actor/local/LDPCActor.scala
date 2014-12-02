package rww.ldp.actor.local

import java.io.File
import java.net.{URI => jURI}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Path, _}
import java.util
import java.util.{Date, UUID}

import akka.actor.{InvalidActorNameException,Props}
import org.w3.banana.io._
import org.w3.banana.{RDF, _}
import rww.ldp.LDPExceptions._
import rww.ldp.actor.common.CommonActorMessages.ScriptMessage
import rww.ldp.model._
import rww.ldp.{CreateBinary, CreateContainer, CreateLDPR, DeleteResource, _}

import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}

/**
 * A LDP Container actor that is responsible for the equivalent of a directory
 *
 *
 * @param ldpcUri the URI for the container
 * @param root the path on the file system where data is saved to
 * @param ops
 */
class LDPCActor[Rdf<:RDF](ldpcUri: Rdf#URI, root: Path)
                         (implicit
                          ops: RDFOps[Rdf],
                          sparqlOps: SparqlOps[Rdf],
                          sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph]  with SparqlUpdate[Rdf, Try, Rdf#Graph],
                          reader: RDFReader[Rdf, Try, Turtle],
                          writer: RDFWriter[Rdf, Try, Turtle]
//                                  adviceSelector: AdviceSelector[Rdf] = new EmptyAdviceSelector
                          ) extends LDPRActor[Rdf](ldpcUri,root) {
  import ops._

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
            context.actorOf(Props(new LDPCActor[Rdf](absoluteUri(pathSegment+"/"),root.resolve(file))),pathSegment)
          } else if (attrs.isSymbolicLink) {
            //we use symbolic links to point to the file that contains the default representation
            //this is because we may have many different representations, and we don't want an actor
            //for each of the representations.
            context.actorOf(Props(new LDPRActor[Rdf](absoluteUri(pathSegment),root.resolve(file))),pathSegment)
          }
        FileVisitResult.CONTINUE
      }
    })
  }

  override
  def localName(uri: Rdf#URI): String = {
    if (uri/"" == ldpcUri) fileName
    else super.localName(uri)
  }

  // TODO permits to retrieve metadatas on the file etc...
  // this is relative to LDP spec and may need to be update with newer spec version
  // TODO add better documentation
  private def descriptionFor(path: Path, attrs: BasicFileAttributes): Rdf#Graph = {
    def graphFor(uri: Rdf#URI) = {
      var res = Graph(
        Triple(ldpcUri, ldp.contains, uri),
        Triple(uri, stat.mtime, Literal(attrs.lastModifiedTime().toMillis.toString,xsd.integer))
      )
      if (attrs.isDirectory)
        res union Graph(Triple(uri, rdf.typ, ldp.BasicContainer))
      else if (attrs.isSymbolicLink) {
        val target: Path = root.resolve(Files.readSymbolicLink(path))
        res union Graph(Triple(uri, stat.size, Literal((target.toFile.length()).toString, xsd.integer)))
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

  def absoluteUri(pathSegment: String): Rdf#URI =  ldpcUri/pathSegment

  /**
   *
   * @param name the name of the file ( only the index file in the case of LPDCs
   * @return
   */
  @throws[ResourceDoesNotExist]
  override def getResource(name: String): Try[LocalNamedResource[Rdf]] = {
    super.getResource(name) match {
      case ok @ Success(ldpr: LocalLDPR[Rdf]) => {
        if (name == fileName) {
          //if this is the index file, add all the content info
          val metadata = Graph(Triple(ldpcUri, rdf.typ, ldp.BasicContainer))
          var contentGrph = ldpr.graph union metadata
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
          Success(
            LocalLDPC[Rdf](
              ldpcUri,
              contentGrph,
              root,
              Some(metadata union Graph(Triple(ldpcUri, rdf.typ, ldp.Resource))) //silly spec wants us to tell the obvious
            )
          )
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
  def runLocalCmd[A](cmd: LDPCommand[Rdf, LDPCommand.Script[Rdf,A]]) {
    log.debug(s"received $cmd")
    cmd match {
      case CreateLDPR(_, slugOpt, graph, k) => {
        val (uri, path) = mkFile(slugOpt, ext)

        val (actor, iri) = try {
          val actor = context.actorOf(Props(new LDPRActor(uri, path)), path.getFileName.toString)
          (actor, uri)
        } catch {
          case e: InvalidActorNameException => {
            val (uri2, path2) = mkFile(slugOpt, ext)
            Files.deleteIfExists(Files.readSymbolicLink(path))
            Files.delete(path)
            val actor = context.actorOf(Props(new LDPRActor(uri2, path2)), path2.getFileName.toString)
            (actor, uri2)
          }
        }

        //todo: move this into the resource created ( it should know on creation how to find the parent )
        val linkedGraph = graph union Graph(Triple(ldpcUri, ldp.contains, iri))

        //todo: should these be in the header?
        val scrpt = LDPCommand.updateLDPR[Rdf](iri, add = linkedGraph.triples).flatMap(_ => k(iri))
        actor forward ScriptMessage(scrpt)
      }
      case CreateBinary(_, slugOpt, mime: MimeType, k) => {
        SupportedBinaryMimeExtensions.extension(mime).map { ext =>
          log.debug(s"Receiving createBinary message for resource with slug $slugOpt. ext=$ext")
          val (uri, path) = mkFile(slugOpt, ext)
          val (actor,iri) = try {
            val actor = context.actorOf(Props(new LDPRActor(uri,path)),path.getFileName.toString)
            (actor,uri)
          } catch {
            case e: InvalidActorNameException => {
              val (uri2,path2) = mkFile(slugOpt,ext)
              Files.deleteIfExists(Files.readSymbolicLink(path))
              Files.delete(path)
              val actor = context.actorOf(Props(new LDPRActor(uri2,path2)),path2.getFileName.toString)
              (actor, uri2)
            }
          }
          val s = LDPCommand.getResource[Rdf,NamedResource[Rdf]](iri)
          actor forward ScriptMessage(s.flatMap{
            case br: BinaryResource[Rdf] => k(br)
            case x => throw UnsupportedMediaType("was looking for a BinaryResource but received a "+x.getClass)//todo: not the right error code
          })
          //todo: make sure the uri does not end in ";aclPath" or whatever else the aclPath standard will be
        } getOrElse(throw UnsupportedMediaType("we do not yet support "+mime))
      }
//      case PutLDPR(uri,graph,headers,k) => {
//        for {
//          headerGraph <- headers;
//          if (PointedGraph(uri, headerGraph) / rdf.typ).nodes.exists(_.fold(u => u == ldp.BasicContainer, _ => false, _ => false))
//        } yield {
//
//        }
//      }
      case CreateContainer(_,slugOpt,graph,k) => {
        val (uri, pathSegment) = mkDir(slugOpt)
        val p = root.resolve(pathSegment)
        val dirUri = uri / ""
        val ldpc = context.actorOf(Props(new LDPCActor(dirUri, p)), pathSegment.getFileName.toString)
        val creationRel = Triple(ldpcUri, ldp.contains, dirUri)
        val linkedGraph = graph union Graph(creationRel)
        //todo: should these be in the header?
        val scrpt = LDPCommand.updateLDPR[Rdf](dirUri, add = linkedGraph.triples).flatMap(_ => k(dirUri))
        ldpc forward ScriptMessage(scrpt)
      }
      case DeleteResource(uri, a) => {
//        val name = uriW[Rdf](uri).lastPathSegment
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
        rwwRouterActor.tell(ScriptMessage(a),context.sender)
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


  protected def mkFile[A](slugOpt: Option[String], ext: String): (Rdf#URI, Path) = {
    val slug = slugOpt.getOrElse(generateRandomString)
    mkFile(slug,ext)
  }

  /**
   * creates a file from the slug, and returns the URI and path for it.
   * @param slug
   * @param ext for the extension of the file, should not be the "" string
   **/
  protected def mkFile[A](slug: String, ext: String): (Rdf#URI, Path) = {
    assert (ext != "")
    val safeSlug = slug.replaceAll("[/.]+", "_")
    val slugLink = root.resolve(safeSlug)
    val slugFile =  slugLink.resolveSibling(slugLink.getFileName.toString+ext)
    val slugAcl = slugLink.resolveSibling(slugLink.getFileName.toString+acl+".ttl")
    if ( Files.exists(slugLink, LinkOption.NOFOLLOW_LINKS) || Files.exists(slugFile) || Files.exists(slugLink) ) {
      val slugFallback = generateSlugFallback(slug)
      log.info(s"filename $slug is not available, will try with new slug $slugFallback")
      mkFile(Some(slugFallback),ext)
    }
    else {
      Files.createFile(slugFile)
      Files.createFile(slugAcl)
      log.debug(s"Created file $slugFile with acl file $slugAcl")
      val symPath = Files.createSymbolicLink(slugLink, slugFile.getFileName)
      val uri = ldpcUri / symPath.getFileName.toString
      (uri, symPath)
    }
  }

  def generateSlugFallback(baseSlug: String): String =  baseSlug + "_" + generateRandomString

  def generateRandomString = UUID.randomUUID().toString.replaceAll("-","").substring(0,10)


  /**
   * creates a dir/collection from the slug, and returns the URI and path for it.
   * @param slugOpt, optional file name
   **/
  protected def mkDir[A](slugOpt: Option[String]): (Rdf#URI, Path) = {
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
    val uri = absoluteUri(path.getFileName.toString+"/")
    (uri, path)

  }


}



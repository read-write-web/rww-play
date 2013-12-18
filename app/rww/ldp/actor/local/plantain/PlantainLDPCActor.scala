package rww.ldp.actor.plantain

import scala.language.reflectiveCalls

import org.w3.banana._
import java.nio.file._
import akka.actor._
import java.net.{URI => jURI}
import org.w3.banana.plantain.Plantain
import java.nio.file.attribute.BasicFileAttributes
import java.util
import java.nio.file.Path
import scala.util.Try
import java.util.Date
import java.io.File
import rww.ldp._
import rww.ldp.actor.common.CommonActorMessages
import rww.ldp.CreateContainer
import rww.ldp.DeleteResource
import scala.util.Failure
import scala.Some
import scala.util.Success
import rww.ldp.LDPExceptions._
import CommonActorMessages.ScriptMessage
import rww.ldp.CreateBinary
import akka.actor.InvalidActorNameException
import rww.ldp.CreateLDPR
import rww.ldp.model._


/**
 * A LDP Container actor that is responsible for the equivalent of a directory
 *
 *
 * @param ldpcUri the URI for the container
 * @param root the path on the file system where data is saved to
 * @param ops
 * @param sparqlGraph
 */
class PlantainLDPCActor(ldpcUri: Plantain#URI, root: Path)
                                 (implicit ops: RDFOps[Plantain],
                                     sparqlGraph: SparqlGraph[Plantain],
                                     adviceSelector: AdviceSelector[Plantain]=new EmptyAdviceSelector) extends PlantainLDPRActor(ldpcUri,root) {
  import org.w3.banana.syntax._
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
    val ldpcPath = ldpcUri.underlying.getPath
    if (ldpcPath.length > requestedPath.length) fileName
    else super.localName(uri)
  }

  // TODO permits to retrieve metadatas on the file etc...
  // this is relative to LDP spec and may need to be update with newer spec version
  // TODO add better documentation
  private def descriptionFor(path: Path, attrs: BasicFileAttributes): Plantain#Graph = {
    def graphFor(uri: Plantain#URI) = {
      var res = emptyGraph +
        Triple(ldpcUri, ldp.created, uri) +
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

  def absoluteUri(pathSegment: String): Plantain#URI =  uriW[Plantain](ldpcUri)/pathSegment

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
          var contentGrph = ldpr.graph + Triple(ldpcUri, rdf.typ, ldp.Container)
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
          Success(LocalLDPR[Plantain](ldpcUri, contentGrph, root, Some(new Date(Files.getLastModifiedTime(root).toMillis))))
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
    log.debug(s"received $cmd")
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
        val linkedGraph = graph + Triple(ldpcUri, ldp.created, iri)

        //todo: should these be in the header?
        val scrpt = LDPCommand.updateLDPR[Plantain](iri, add = graphToIterable(linkedGraph)).flatMap(_ => k(iri))
        actor forward ScriptMessage(scrpt)
      }
      case CreateBinary(_, slugOpt, mime: MimeType, k) => {
        mimeExt.extension(mime).map { ext =>
          log.debug(s"Receiving createBinary message for resource with slug $slugOpt. ext=$ext")
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
          actor forward ScriptMessage(s.flatMap{
            case br: BinaryResource[Plantain] => k(br)
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
        val creationRel = Triple(ldpcUri, ldp.created, dirUri)
        val linkedGraph = graph + creationRel
        //todo: should these be in the header?
        val scrpt = LDPCommand.updateLDPR[Plantain](dirUri, add = graphToIterable(linkedGraph)).flatMap(_ => k(dirUri))
        ldpc forward ScriptMessage(scrpt)
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
    val uri = uriW[Plantain](ldpcUri) / path.getFileName.toString
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
    val uri = absoluteUri(path.getFileName.toString+"/")
    (uri, path)

  }


}



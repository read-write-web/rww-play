package rww.ldp.actor.local

//import org.w3.banana.plantain.LDPatch
import java.net.{URI => jURL}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util

import akka.actor.Props
import org.w3.banana._
import org.w3.banana.io._

import scala.util.Try

//import rww.ldp.{EmptyAdviceSelector, AdviceSelector}

/**
 * Any collections in a SubdomainActor end up creating subdomains.
 * Ie a collection joe in an ldpcURI https://localhost:8443/ creates a collection https://joe.localhost:8443/
 */
class LDPCSubdomainActor[Rdf<:RDF](
  ldpcUri: Rdf#URI,
  root: Path
)(implicit
  ops: RDFOps[Rdf],
  sparqlOps: SparqlOps[Rdf],
  sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
  reader: RDFReader[Rdf, Try, Turtle],
  writer: RDFWriter[Rdf, Try, Turtle]
  //                                  patch: LDPatch[Rdf, Try]
  //                                adviceSelector: AdviceSelector[Rdf]=new EmptyAdviceSelector
) extends LDPCActor[Rdf](ldpcUri, root) {

  log.info(s"Creating LDPCSubdomainActor($ldpcUri,$root)")
  val ju = new jURL(ldpcUri.toString)

  override
  def preStart {
    //start all agents for all files and subdirectories
    //start starting directories lazily may in many cases be a better option, especially for
    // systems with a huge
    //number of directories.... http://stackoverflow
    // .com/questions/16633515/creating-akka-actor-hierarchies-lazily
    //currently we do no optimization, just to make code simpler. Optimization to come later.
    //todo: memory optimizations
    //todo: handle exceptions
    //todo: deal with index file...
    Files.walkFileTree(root, util.Collections.emptySet(), 1,
      new SimpleFileVisitor[Path] {

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
          if (dir == root) super.preVisitDirectory(dir, attrs)
          else FileVisitResult.SKIP_SUBTREE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          val pathSegment = file.getFileName.toString
          if (attrs.isDirectory) {
            context.actorOf(
              Props(new LDPCActor(absoluteUri(file.getFileName + "/"), root.resolve(file))),
              pathSegment
            )
          } else if (attrs.isSymbolicLink) {
            //we use symbolic links to point to the file that contains the default representation
            //this is because we may have many different representations, and we don't want an actor
            //for each of the representations.
            context.actorOf(
              Props(new LDPRActor(absoluteUri(file.getFileName.toString), root.resolve(file))),
              pathSegment
            )
          }
          FileVisitResult.CONTINUE
        }
      })
  }


  override
  def absoluteUri(pathSegment: String): Rdf#URI = {
    val host = if (pathSegment.endsWith("/")) pathSegment
      .substring(0, pathSegment.length - 1) + "." + ju.getHost
    else ju.getHost
    val path = if (pathSegment.endsWith("/")) "/" else ju.getPath + pathSegment
    val url = new jURL(ju.getScheme,null, host, ju.getPort, path, "", "")
    val res = ops.URI(url.toString)
    res
  }
}

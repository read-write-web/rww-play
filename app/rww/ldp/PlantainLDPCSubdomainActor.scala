package rww.ldp

import org.w3.banana.plantain.Plantain
import java.nio.file.{FileVisitResult, SimpleFileVisitor, Files, Path}
import org.w3.banana.{SparqlGraph, RDFOps}
import java.util
import java.nio.file.attribute.BasicFileAttributes
import akka.actor.Props
import java.net.{URL=>jURL}
import org.w3.banana.plantain.model.URI

/**
 * Any collections in a SubdomainActor end up creating subdomains.
 * Ie a collection joe in an ldpcURI https://localhost:8443/ creates a collection https://joe.localhost:8443/
 */
class PlantainLDPCSubdomainActor (ldpcUri: Plantain#URI, root: Path)
                                 (implicit ops: RDFOps[Plantain],
                                  sparqlGraph: SparqlGraph[Plantain],
                                  adviceSelector: AdviceSelector[Plantain]=new EmptyAdviceSelector) extends PlantainLDPCActor(ldpcUri,root) {


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
            context.actorOf(Props(new PlantainLDPCActor(absoluteUri(file.getFileName+"/"),root.resolve(file))),pathSegment)
          } else if (attrs.isSymbolicLink) {
            //we use symbolic links to point to the file that contains the default representation
            //this is because we may have many different representations, and we don't want an actor
            //for each of the representations.
            context.actorOf(Props(new PlantainLDPRActor(absoluteUri(file.getFileName.toString),root.resolve(file))),pathSegment)
          }
          FileVisitResult.CONTINUE
        }
      })
  }


  override
  def absoluteUri(pathSegment: String): Plantain#URI ={
    val u = ldpcUri.underlying
    val host = if (pathSegment.endsWith("/")) pathSegment.substring(0,pathSegment.length-1) + "." + u.getHost else u.getHost
    val path = if (pathSegment.endsWith("/")) "/" else u.getPath + pathSegment
    val url = new jURL(u.getScheme, host, u.getPort, path)
    URI(url.toURI)
  }
}

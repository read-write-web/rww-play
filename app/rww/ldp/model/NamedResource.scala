package rww.ldp.model

import java.nio.file.{Files, Path}
import java.util.Date

import akka.http.scaladsl.model.headers.EntityTag
import org.w3.banana.{LDPPrefix => _, _}

import scala.util.{Success, Try}

/**
 * A resource on the server ( Resource is already taken. )
 * TODO: find a better name
 *  • State?
 *     + The resource can have different states, but it can also show different
 *       representations for different users per state
 *
 *  • Representation?
 *     + A resource returns representations. This helps explain the importance of the URL.
 *       ( A representation is very closely tied to a resource. )
 *     +
 *     ? I (Henry) have tended to think of representations as binary objects, not as the
 *       interpreted graph
 *  • Message?
 *     + A web server returns a message to a request, the message has an envelope
 *       which contains the metadata.
 *     ? A message is less closely tied to the origin of the resource.
 *     ? the POST with the body could also be considered a message, and so could the reply
 *
 * There can be named and unnamed resources, as when a POST creates a
 * resource that is not given a name... so this should probably extend a more abstract resource
 */
trait NamedResource[Rdf<:RDF] extends Meta[Rdf] {
  def location: Rdf#URI // TODO already defined in meta so is it useful to keep it here too
}


//todo: the way of finding the meta data should not be set here, but in the implementation
trait LocalNamedResource[Rdf<:RDF] extends NamedResource[Rdf] {
  implicit val ops: RDFOps[Rdf]
  def path: Path

  import ops._
  import org.w3.banana.diesel._

  protected val wac = WebACLPrefix[Rdf] //todo, should be a static object somewhere
  protected val ldp = LDPPrefix[Rdf]

  /** context specific metadata */
  def contextualMetadata: Option[Rdf#Graph]
  
  /** type specific metadata */
  def typeMetadata: Rdf#Graph

  val size = Try(Files.size(path))

  val updated =
    Try(new Date(Files.getLastModifiedTime(path).toMillis))

  lazy val meta:  Try[PointedGraph[Rdf]] = {
    val aclgr = (for (u <- acl) yield {
      (location -- wac.accessTo ->- u).graph
    }).getOrElse(Graph.empty)
    val resultGraph = contextualMetadata.fold(typeMetadata union aclgr)(_ union typeMetadata union aclgr)
    Success(PointedGraph(location,resultGraph))
  }

  lazy val acl: Try[Rdf#URI]= Try{
    var loc=location.toString
    if (loc.endsWith(".acl")) {
      location
    } else if (loc.endsWith(".acl.ttl")) {
      ops.URI(loc.substring(0,loc.length-4))
    } else {
      val fileName = location.lastPathSegment
      val i = fileName.indexOf('.')
      val coreName = if ( i > 0) fileName.substring(0,i) else fileName
      location.resolve(URI(coreName+".acl"))
    }
  }

  //todo: create strong etags for RDF Sources
  def etag = for {
    date <- updated
  } yield {
    EntityTag(date.getTime+"|"+size,false)
  }
}
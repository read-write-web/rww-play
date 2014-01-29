package rww.ldp.model

import org.w3.banana.RDF
import java.nio.file.Path
import org.w3.banana.syntax.URISyntax
import scala.util.Try

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

  def path: Path

  lazy val acl: Try[Rdf#URI]= Try{
    var loc=location.toString
    if (loc.endsWith(".acl")) {
      location
    } else if (loc.endsWith(".acl.ttl")) {
      ops.URI(loc.substring(0,loc.length-4))
    } else {
      import URISyntax._
      implicit val o = ops
      val fileName = location.lastPathSegment
      val i = fileName.indexOf('.')
      val coreName = if ( i > 0) fileName.substring(0,i) else fileName
      location.resolve(coreName+".acl")
    }
  }
}
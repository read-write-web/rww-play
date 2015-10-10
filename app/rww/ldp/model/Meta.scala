package rww.ldp.model

import akka.http.scaladsl.model.headers.EntityTag
import org.w3.banana.{RDFOps, PointedGraph, RDF}
import java.util.Date
import scala.util.Try

/**
 *   Metadata about the representation of a resource, or about the state of a resource ( not sure which yet )
 *   This may be thought to be so generic that a graph representation would do,
 *   but it is very likely to be very limited set of properties and so to be
 *   better done in form methods for efficiency reasons.
 *   todo: should the HTTP response code be part of the meta data?
 *     That does not seem correct: The error code can only be understood in the context of the request made.
 *     The idea of MetaData is that it should be understandable without ( but this may be a flawed assumption ),
 *     moving us more towards a speech act view of resources
 *
 */
trait Meta[Rdf <: RDF] {
  def location: Rdf#URI

  //move all the metadata to this, and have the other functions
  def meta: Try[PointedGraph[Rdf]]

  def ops: RDFOps[Rdf]

  def size: Try[Long]

  def updated: Try[Date]
  /*
 * A resource should ideally be versioned, so any change would get a version URI
 **/
  def version: Option[Rdf#URI] = None

  def etag: Try[EntityTag]
  /**
   * location of initial ACL for this resource
   **/
  def acl: Try[Rdf#URI]

  //other metadata candidates:
  // - owner
  // - etag
  //

}

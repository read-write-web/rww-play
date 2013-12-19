package rww.ldp.model

import org.w3.banana._
import java.nio.file.Path
import java.util.Date
import scala.Some

/**
 * todo: naming
 *  - LDPR here could also mean LDP Representation, or LDPR State.
 *
 *  LDPR And LDPC is currently defined in the LDP ontology as a subclass of an LDPR.
 * This LDPR class is more what we are thinking as the non binary non LDPCs...
 * - an LDPS must subscribe to the death of its LDPC
 */

trait LDPR[Rdf <: RDF] extends NamedResource[Rdf] with LinkedDataResource[Rdf]  {
  import org.w3.banana.syntax._
  def location: Rdf#URI

  def graph: Rdf#Graph // all uris are relative to location

  /* the graph such that all URIs are relative to $location */
  def relativeGraph(implicit ops: RDFOps[Rdf]): Rdf#Graph  = graph.relativize(location)

  def resource: PointedGraph[Rdf] = PointedGraph(location,graph)

}




/**
 * it's important for the uris in the graph to be absolute
 * this invariant is assumed by the sparql engine (TripleSource)
 */
case class LocalLDPR[Rdf<:RDF](location: Rdf#URI,
                               graph: Rdf#Graph,
                               path: Path,
                               updated: Option[Date] = Some(new Date))
                              (implicit val ops: RDFOps[Rdf])
  extends LDPR[Rdf] with LocalNamedResource[Rdf]{
  import ops._
  def meta = PointedGraph(location,Graph.empty)  //todo: build up aclPath from local info
  def size = None
}

case class RemoteLDPR[Rdf<:RDF](location: Rdf#URI, graph: Rdf#Graph, meta: PointedGraph[Rdf], updated: Option[Date])
                               (implicit val ops: RDFOps[Rdf]) extends LDPR[Rdf] {
  import org.w3.banana.diesel._

  val link = IANALinkPrefix[Rdf]

  /**
   * location of initial ACL for this resource
   **/
  lazy val acl: Option[Rdf#URI] = (meta/link.acl).collectFirst{ case PointedGraph(p: Rdf#URI,g) => p }
  def size = None
}
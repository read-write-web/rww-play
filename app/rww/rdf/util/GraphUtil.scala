package rww.rdf.util

import org.w3.banana.{RDFOps, RDF}
import akka.http.scaladsl.model.Uri
/**
 * Created by hjs on 19/02/2014.
 */
object GraphUtil {
  /**
   * normalise all URIs in the graph and the baseURI
   * @param graph
   * @return a clean non-relative URI graph
   * //todo: move to util lib
   */
  def normalise[Rdf<:RDF](base: Uri, graph: Rdf#Graph)(implicit ops: RDFOps[Rdf]): Rdf#Graph = {
    import org.w3.banana.syntax.GraphW
    new GraphW(graph).copy { uri =>
      val normalised = Uri.parseAndResolve(uri.toString,base)
      ops.makeUri(normalised.toString)
    }
  }

//  /**
//   * returns a copy of the graph where uri are transformed through urifunc
//   */
//  def copy[Rdf<:RDF](graph: Rdf#Graph)(urifunc: Rdf#URI => Rdf#URI)(implicit ops: RDFOps[Rdf]): Rdf#Graph = {
//    def nodefunc(node: Rdf#Node): Rdf#Node = ops.foldNode(node)(urifunc, bn => bn, lit => lit)
//    var triples = Set[Rdf#Triple]()
//    val it = ops.graphToIterable(graph).toIterable
//    val transformed = for (ops.Triple(s,p,o)<-it) yield {
//      // question: what about predicates?
//      ops.Triple(nodefunc(s), urifunc(p), nodefunc(o))
//    }
//    ops.makeGraph(transformed)
//  }


}


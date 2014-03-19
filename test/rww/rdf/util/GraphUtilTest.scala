package rww.rdf.util

import test.ldp.TestGraphs
import org.w3.banana._
import org.scalatest._
import org.scalatest.matchers._
import org.w3.banana.binder._
import org.w3.banana.plantain.Plantain
import spray.http.Uri


class PlantainGraphUtilTest extends GraphUtilTest[Plantain]

/**
 * Created by hjs on 19/02/2014.
 */
class GraphUtilTest[Rdf<: RDF]( implicit val recordBinder: RecordBinder[Rdf], val ops: RDFOps[Rdf])
  extends  WordSpec with Matchers with TestGraphs[Rdf] {

  import ops._
  import org.w3.banana.syntax._
  import org.w3.banana.diesel._
  import certbinder._


  val henryCardX = URI("http://bblfish.net:80/people/henry/card")
  val henryCardComvoluted = "http://bblfish.net:80/people/joe/../henry/card"
  val henryX =  URI(henryCardX.toString+"#me")
  val henryGraphX : Rdf#Graph = (
    henryX -- cert.key ->- henryKeys.pub
      -- foaf.name ->- "Henry"
    ).graph


  import rww.rdf.util.GraphUtil._

  s"Graphs showing port, i.e. containing urls such as with <$henryCardX>" when {

    "normalising spray URIs removes default port numbers" in {
      val cardXNormed: String = Uri(henryCardX.toString).toString()
      cardXNormed should equal ( henryCard.toString )
    }

    "normalising spray URI with odd relative paths" in {
      val cardComvolutedNormed: String = Uri(henryCardComvoluted).toString()
      cardComvolutedNormed should be(henryCard.toString)
   }

    "normalise" in {
      // this does not work because issue https://github.com/w3c/banana-rdf/issues/87 creates URIs
      val cleanGraph = normalise(Uri(henryCardComvoluted),henryGraphX).resolveAgainst(henryCard)
      val wanted = henryGraph.resolveAgainst(henryCard)

      assert( cleanGraph.isIsomorphicWith(wanted), "cleaned graph is "+cleanGraph.toString +
        " but should have been " + wanted )
    }
  }
}

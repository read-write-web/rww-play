package test.ldp

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.w3.banana._
import rww.ldp.LinkHeaderParser

import scala.util.control.NonFatal
import test.ldp.TestSetup._
class PlantainLinkHeaderParserTest extends LinkHeaderParserTest[Rdf]()(ops)


abstract class LinkHeaderParserTest[Rdf<:RDF](
  implicit ops: RDFOps[Rdf]
) extends WordSpec with Matchers with BeforeAndAfterAll {

  val lhp = new LinkHeaderParser
  val foaf = FOAFPrefix[Rdf]
  val link = IANALinkPrefix[Rdf]
  val dc = DCPrefix[Rdf]
  val dct = DCTPrefix[Rdf]

  import diesel._
  import ops._
  import Literal.tagged


  "test rel=" in {
    val p1 = lhp.parse("""<.>; rel="collection"""").get
    val expected = ( URI("") -- link.collection ->- URI(".") ).graph
    assert (p1.graph isIsomorphicWith expected,s"${p1.graph} must be isomorphic with expected ${expected}")
  }


  "test OOM" in {
    val p1 = lhp.parse("""<.>; rel="collection"""").get
    val expected = ( URI("") -- link.collection ->- URI(".") ).graph
    assert (p1.graph isIsomorphicWith expected,s"${p1.graph} must be isomorphic with expected ${expected}")
  }


  "test rel with anchor" in {
    val p1 = lhp.parse("""</>; rel=http://xmlns.com/foaf/0.1/homepage; anchor="#me"""").get
    val expected = ( URI("#me") -- foaf.homepage ->- URI("/") ).graph
    assert (p1.graph isIsomorphicWith expected,s"${p1.graph} must be isomorphic with expected ${expected}")
  }


  "test rel no quote with title" in {
    val p2 = lhp.parse("""</TheBook/chapter2>; rel=previous; title*=UTF-8'de'letztes%20Kapitel""").get
    val expected = (
      URI("/TheBook/chapter2") -- dct.title ->- tagged("letztes Kapitel",Lang("de"))
      ).graph union (
      URI("") -- link.previous ->- URI("/TheBook/chapter2")
      ).graph
    assert(p2.graph isIsomorphicWith expected,s"${p2.graph} must be isomorphic with expected ${expected}")
  }

  "test two link relations seperated by ','" in {
    val p3 = lhp.parse("""</TheBook/chapter2>; rel="previous"; title*=UTF-8'de'letztes%20Kapitel,
                        | </TheBook/chapter4>; rel="next"; title*=UTF-8'de'n%c3%a4chstes%20Kapitel""".stripMargin).get
    val expected = (
       URI("/TheBook/chapter2") -- dct.title ->- tagged("letztes Kapitel",Lang("de"))
      ).graph union (
       URI("") -- link.previous ->- URI("/TheBook/chapter2")
      ).graph union (
       URI("/TheBook/chapter4") -- dct.title ->- tagged("nächstes Kapitel",Lang("de"))
      ).graph union (
       URI("") -- link.next ->- URI("/TheBook/chapter4")
      ).graph
    assert(p3.graph isIsomorphicWith expected,s"${p3.graph} must be isomorphic with expected ${expected}")
  }

  "multiple relations" in {
    val p4 = lhp.parse("""<http://example.org/>;
                        | rel="start http://example.net/relation/other"""".stripMargin).get
    val expected = (
      URI("") -- link.start ->- URI("http://example.org/")
              -- URI("http://example.net/relation/other") ->- URI("http://example.org/")
      ).graph

    assert(p4.graph isIsomorphicWith expected,s"${p4.graph} must be isomorphic with expected ${expected}")
  }

  "two relations test tests" in {
    val pg = lhp.parse("""<http://example.org/>; rel="start http://example.net/relation/other"""").get
    val expected = (
      URI("")
        -- link.start ->- URI("http://example.org/")
        -- URI("http://example.net/relation/other") ->- URI("http://example.org/") ).graph
    assert (pg isIsomorphicWith expected,s"${pg} must be isomorphic with expected ${expected}")
  }

  "simple meta rel test that is broken" in {
    //todo: should this return a parse error?
    val pg = lhp.parse("""<http://example.org/>; rel="meta"; """).get
    assert (pg.isIsomorphicWith(Graph.empty), s"${pg} should be empty as the Link header is broken")
// one could argue that this should be the case but it does not I think fit the spec.
//    val expected = ( URI("") -- link.meta ->- URI("http://example.org/") ).graph
//    assert (pg isIsomorphicWith expected,s"${pg} must be isomorphic with expected ${expected}")
  }

  "simple meta rel test that should work" in {
    val pg = lhp.parse("""<http://example.org/>; rel="meta" """).get
    val expected = ( URI("") -- link.meta ->- URI("http://example.org/") ).graph
    assert (pg isIsomorphicWith expected,s"${pg} must be isomorphic with expected ${expected}")
  }

  "rel with title" in {
    val pg = lhp.parse("""<http://example.org/>; rel="meta"; title="Metadata File"""").get
    val expected = ( URI("") -- link.meta ->- URI("http://example.org/") ).graph union
      ( URI("http://example.org/") -- dct.title ->- "Metadata File" ).graph
    assert (pg isIsomorphicWith expected,s"${pg} must be isomorphic with expected ${expected}")
  }

  "rel with empty title" in {
    val pg = lhp.parse("""<http://example.org/>; rel="meta"; title=""""").get
    val expected = ( URI("") -- link.meta ->- URI("http://example.org/") ).graph union
      ( URI("http://example.org/") -- dct.title ->- "" ).graph
    assert (pg isIsomorphicWith expected,s"${pg} must be isomorphic with expected ${expected}")
  }


  "test white space in name" in {
    //the Web-Linking spec http://tools.ietf.org/html/rfc5988#section-5 uses the definition of URI from
    //RFC-3986 http://tools.ietf.org/html/rfc3986
    //which does not allow white space in the URL, but this depends on the URL parser
    try {
      val pg = lhp
        .parse( """<http://id.myopenlink.net/DAV/VAD/wa/RDFData/All/iid (1030025).rdf,meta>; rel="meta"; title="Metadata File"""")

      val expected = (URI("") -- link
        .meta ->- URI("http://id.myopenlink.net/DAV/VAD/wa/RDFData/All/iid (1030025).rdf,meta"))
        .graph
    } catch {
      case NonFatal(e) => println("if this is a parsing error related to white space in the URL then everything is fine:"+e)
    }

    //depending on whether the URI parser parses the RDF the above will throw an exception

  }




}

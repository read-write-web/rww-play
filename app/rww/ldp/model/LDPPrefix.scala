package rww.ldp.model

import org.w3.banana.{PrefixBuilder, RDFOps, RDF}

object LDPPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new LDPPrefix(ops)
}

class LDPPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("ldp", "http://www.w3.org/ns/ldp#")(ops) {
  val BasicContainer = apply("BasicContainer")
  val Container = apply("Container")
  val DirectContainer = apply("DirectContainer")
  val IndirectContainer = apply("IndirectContainer")
  val NonRDFSource = apply("NonRDFSource")
  val RDFSource = apply("RDFSource")
  val Resource = apply("Resource")
  val contains = apply("contains")
  val hasMemberRelation = apply("hasMemberRelation")
  val insertedContentRelation = apply("insertedContentRelation")
  val isMemberOfRelation = apply("isMemberOfRelation")
  val member = apply("member")
  val membershipResource = apply("membershipResource")
}
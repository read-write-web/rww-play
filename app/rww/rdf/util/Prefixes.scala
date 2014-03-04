package rww.rdf.util

import org.w3.banana._


object LDPPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new LDPPrefix(ops)
}

class LDPPrefix[Rdf<:RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("ldp", "http://www.w3.org/ns/ldp#")(ops) {
//  val AggregateContainer = apply("AggregateContainer")
//  val CompositeContainer = apply("CompositeContainer")
//  val Container = apply("Container")
//  val Page = apply("Page")
//  val Resource = apply("Resource")
//  val containerSortPredicates = apply("containerSortPredicates")
//  val membershipPredicate = apply("membershipPredicate")
//  val membershipSubject = apply("membershipSubject")
//  val nextPage = apply("nextPage")
//  val created = apply("created")
//  val pageOf = apply("pageOf")

  val Resource = apply("Resource")
  val RDFSource = apply("RDFSource")
  val NonRDFSource = apply("NonRDFSource")
  val Container = apply("Container")
  val BasicContainer = apply("BasicContainer")
  val DirectContainer = apply("DirectContainer")
  val IndirectContainer = apply("IndirectContainer")

  val hasMemberRelation = apply("hasMemberRelation")
  val isMemberOfRelation = apply("isMemberOfRelation")
  val membershipResource = apply("membershipResource")
  val insertedContentRelation = apply("insertedContentRelation")
  val member = apply("member")
  val contains = apply("contains")
  val MemberSubject = apply("MemberSubject")
  val PreferContainment = apply("PreferContainment")
  val PreferMembership = apply("PreferMembership")
  val PreferEmptyContainer = apply("PreferEmptyContainer")
}
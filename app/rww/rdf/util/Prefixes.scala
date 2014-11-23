package rww.rdf.util

import org.w3.banana._

object StatPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new StatPrefix(ops)
}

class StatPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("stat", "http://www.w3.org/ns/posix/stat#")(ops) {
  val atime = apply("atime")
  val blksize = apply("blksize")
  val blocks = apply("blocks")
  val ctime = apply("ctime")
  val dev = apply("dev")
  val gid = apply("gid")
  val ino = apply("ino")
  val mode = apply("mode")
  val mtime = apply("mtime")
  val nlink = apply("nlink")
  val rdev = apply("rdev")
  val size = apply("size")
  val uid = apply("uid")
}


object LDPPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new LDPPrefix(ops)
}

class LDPPrefix[Rdf<:RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("ldp", "http://www.w3.org/ns/ldp#")(ops) {

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


object WebACLPrefix {
  def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new WebACLPrefix(ops)
}

class WebACLPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("acl", "http://www.w3.org/ns/auth/acl#")(ops) {
  val Authorization = apply("Authorization")
  val agent = apply("agent")
  val agentClass = apply("agentClass")
  val accessTo = apply("accessTo")
  val accessToClass = apply("accessToClass")
  val defaultForNew = apply("defaultForNew")
  val mode = apply("mode")
  val Access = apply("Access")
  val Read = apply("Read")
  val Write = apply("Write")
  val Append = apply("Append")
  val accessControl = apply("accessControl")
  val Control = apply("Control")
  val owner = apply("owner")
  val WebIDAgent = apply("WebIDAgent")

  //not officially supported:
  val include = apply("include")
  val regex = apply("regex")
  val acl = apply("acl")
}
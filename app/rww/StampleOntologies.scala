package rww

import org.w3.banana._

/**
 * Permits to group custom ontologies used in this platform
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
object StampleOntologies {



  object StampleDisplayPrefix {
    def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new StampleDisplayPrefix(ops)
  }
  class StampleDisplayPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("stampleDisplay", "http://ont.stample.co/2013/display#")(ops) {
    val EventsDocument = apply("EventsDocument")
  }


  object StampleAdminPrefix {
    def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new StampleAdminPrefix(ops)
  }
  class StampleAdminPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("stampleAdmin", "http://ont.stample.co/2013/admin#")(ops) {
    val claimedInbox = apply("claimedInbox")
    val claimedInboxConfirmationPassword = apply("claimedInboxConfirmationPassword")
    val claimedInboxConfirmed = apply("claimedInboxConfirmed")
    val subdomainCreated = apply("subdomainCreated")
    val webIdCardCreated = apply("webIdCardCreated")
  }



  object WebappPrefix {
    def apply[Rdf <: RDF](implicit ops: RDFOps[Rdf]) = new WebappPrefix(ops)
  }
  class WebappPrefix[Rdf <: RDF](ops: RDFOps[Rdf]) extends PrefixBuilder("stampleAdmin", "http://ns.rww.io/wapp#")(ops) {
    val description = apply("description")
    val endpoint = apply("endpoint")
    val name = apply("name")
    val serviceId = apply("serviceId")
  }

}


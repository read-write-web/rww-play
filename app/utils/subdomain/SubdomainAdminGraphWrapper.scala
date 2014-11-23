package utils.subdomain

import org.w3.banana._
import rww.StampleOntologies


/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
// TODO maybe a LinkedDataResource of banana should be used here !
class SubdomainAdminGraphWrapper[Rdf<:RDF](uri: Rdf#URI,underlyingGraph: Rdf#Graph)(implicit ops: RDFOps[Rdf]) {

  // TODO it seems there RecordBinder in Banana RDF which could handle this kind of graph wrapping logic
  // see
  // https://github.com/w3c/banana-rdf/blob/master/rdf-test-suite/src/main/scala/RecordBinderTest.scala
  // https://github.com/w3c/banana-rdf/blob/master/rdf-test-suite/src/main/scala/ObjectExamples.scala

  play.api.Logger.info(s"SubdomainAdminGraphWrapper uri = $uri \n underlyingGraph=$underlyingGraph")



  import org.w3.banana.diesel._

  val ldp = LDPPrefix[Rdf]
  val cert = CertPrefix[Rdf]
  val foaf = FOAFPrefix[Rdf]
  val wac = WebACLPrefix[Rdf]

  val stampleDisplay = StampleOntologies.StampleDisplayPrefix[Rdf]
  val stampleAdmin = StampleOntologies.StampleAdminPrefix[Rdf]

  def pointedGraph: PointedGraph[Rdf] = PointedGraph(uri,underlyingGraph)


  def email: String = (pointedGraph / stampleAdmin.claimedInbox).as[String].get
  def password: String = (pointedGraph / stampleAdmin.claimedInboxConfirmationPassword).as[String].get


  def emailConfirmed: Boolean = (pointedGraph / stampleAdmin.claimedInboxConfirmed).as[Boolean].getOrElse(false)
  def subdomainCreated: Option[Rdf#URI] = (pointedGraph / stampleAdmin.subdomainCreated).as[Rdf#URI].toOption
  def webIdCardCreated: Option[Rdf#URI] = (pointedGraph / stampleAdmin.webIdCardCreated).as[Rdf#URI].toOption

}

object SubdomainAdminGraphWrapper {

  def apply[Rdf<:RDF](uri: Rdf#URI,underlyingGraph: Rdf#Graph)(implicit ops: RDFOps[Rdf]) = new SubdomainAdminGraphWrapper(uri,underlyingGraph)

}

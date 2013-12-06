package utils.subdomain

import org.w3.banana._
import java.net.URL
import java.nio.file.Path
import rww.ldp.RWWeb
import java.security.interfaces.RSAPublicKey
import org.w3.banana.diesel
import rww.StampleOntologies
import rww.ldp.LDPCommand._
import scala.Some


/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
class SubdomainGraphUtils[Rdf<:RDF](implicit ops: RDFOps[Rdf]) {


  import ops._
  import org.w3.banana.syntax.URISyntax._

  val ldp = LDPPrefix[Rdf]
  val cert = CertPrefix[Rdf]
  val foaf = FOAFPrefix[Rdf]
  val wac = WebACLPrefix[Rdf]

  val stampleDisplay = StampleOntologies.StampleDisplayPrefix[Rdf]
  val stampleAdmin = StampleOntologies.StampleAdminPrefix[Rdf]
  val webapp = StampleOntologies.WebappPrefix[Rdf]

  val calendar = "calendar"




  def createSubdomainAdminGraph(subdomain: String,email: String): Rdf#Graph = {
    import diesel._
    val oneTimePassword = java.util.UUID.randomUUID().toString.substring(0,8)
    val pg: PointedGraph[Rdf] = (
      URI("").a(foaf.OnlineAccount)
        -- stampleAdmin.claimedInbox ->- TypedLiteral(email, xsd.string)
        -- stampleAdmin.claimedInboxConfirmationPassword ->- TypedLiteral(oneTimePassword, xsd.string)
      )
    pg.graph
  }

  /*
  def createSubdomainWebIdCardGraph(key: RSAPublicKey, email: String): Rdf#Graph = {
    import org.w3.banana.diesel._
    import syntax.GraphSyntax._

    val pg: PointedGraph[Rdf] = (
      URI("") -- rdf.typ ->- foaf.PersonalProfileDocument
        -- foaf.primaryTopic ->- (
        URI("#i") -- cert.key ->- (
          bnode() -- cert.exponent ->- TypedLiteral(key.getPublicExponent.toString(10), xsd.integer)
            -- cert.modulus ->- TypedLiteral(key.getModulus.toString(16), xsd.hexBinary)
          )
          -- foaf.mbox ->- URI("mailto:" + email)
        )
      )

    val ldcal = URI("#ld-cal")
    // TODO this is temporary webapp test and will be removed
    pg.graph.union(Graph(
      Triple(ldcal,webapp.description,TypedLiteral("Simple Linked Data calendar with agenda.")),
      Triple(ldcal,webapp.endpoint,URI(calendar)),
      Triple(ldcal,webapp.name,TypedLiteral("LD-Cal")),
      Triple(ldcal,webapp.serviceId,URI("https://ld-cal.rww.io")),
      Triple(ldcal,rdf.typ,webapp("App"))
    ))
  }
  */

  def createSubdomainWebIdCardGraph(email: String): Rdf#Graph = {
    import org.w3.banana.diesel._
    import syntax.GraphSyntax._

    val pg: PointedGraph[Rdf] = (
      URI("").a(foaf.PersonalProfileDocument)
        -- foaf.primaryTopic ->- (
        URI("#i") -- foaf.mbox ->- URI("mailto:" + email)
        )
      )

    val ldcal = URI("#ld-cal")
    // TODO this is temporary webapp test and will be removed
    pg.graph.union(Graph(
      Triple(ldcal,webapp.description,TypedLiteral("Simple Linked Data calendar with agenda.")),
      Triple(ldcal,webapp.endpoint,URI(calendar)),
      Triple(ldcal,webapp.name,TypedLiteral("LD-Cal")),
      Triple(ldcal,webapp.serviceId,URI("https://ld-cal.rww.io")),
      Triple(ldcal,rdf.typ,webapp("App"))
    ))
  }





  def domainAcl(domain: String): Rdf#Graph = {
    import diesel._
    val pg: PointedGraph[Rdf] = ( bnode() -- wac.accessToClass ->- (
      bnode() -- wac.regex ->- TypedLiteral(domain + ".*"))
      -- wac.mode ->- wac.Read
      -- wac.mode ->- wac.Write
      -- wac.agent ->- URI("card#i")
      )

    pg.graph
  }

  def calendarEventEmptyGraph: Rdf#Graph = Graph(Triple(URI(""),rdf.typ, stampleDisplay.EventsDocument))

  def createAndSetAcl(container: Rdf#URI, slug: String, graph: Rdf#Graph) = {
    import syntax.GraphSyntax._
    for {
      ldpr <- createLDPR(container, Some(slug), graph)
      meta <- getMeta(ldpr)
      _ <- updateLDPR(meta.acl.get,add=Graph(Triple(URI(""),wac.include,URI(".acl"))).toIterable)
    } yield ldpr
  }

}

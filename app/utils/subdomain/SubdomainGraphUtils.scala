package utils.subdomain

import java.security.interfaces.RSAPublicKey

import org.w3.banana._
import rww.StampleOntologies
import rww.ldp.LDPCommand._


/**
 * @author Sebastien Lorber (lorber.sebastien@gmail.com)
 */
class SubdomainGraphUtils[Rdf<:RDF](implicit ops: RDFOps[Rdf]) {


  import ops._
  import org.w3.banana.diesel._

  val ldp = LDPPrefix[Rdf]
  val cert = CertPrefix[Rdf]
  val foaf = FOAFPrefix[Rdf]
  val wac = WebACLPrefix[Rdf]

  val stampleDisplay = StampleOntologies.StampleDisplayPrefix[Rdf]
  val stampleAdmin = StampleOntologies.StampleAdminPrefix[Rdf]
  val webapp = StampleOntologies.WebappPrefix[Rdf]

  val calendar = "calendar"

  // for now we hardcode the person fragment in the card graph
  // TODO we should instead try to find the foaf primaryTopic or something?
  val personFragment = "#i"



  def createSubdomainAdminGraph(subdomain: String,email: String, emailConfirmationPassword: String): Rdf#Graph = {
    val pg: PointedGraph[Rdf] = (
      URI("").a(foaf.OnlineAccount)
        -- stampleAdmin.claimedInbox ->- Literal(email, xsd.string)
        -- stampleAdmin.claimedInboxConfirmationPassword ->- Literal(emailConfirmationPassword, xsd.string)
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
        URI(personFragment) -- cert.key ->- (
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

    val pg: PointedGraph[Rdf] = (
      URI("").a(foaf.PersonalProfileDocument)
        -- foaf.primaryTopic ->- (
        URI(personFragment).a(foaf.Person)
          -- foaf.mbox ->- URI("mailto:" + email)
          // TODO temporary
          // for now we add some "default friends" because there's no way to discover people on a new account
          -- foaf.knows ->- URI("http://bblfish.net/people/henry/card#me")
          -- foaf.knows ->- URI("https://my-profile.eu/people/deiu/card#me")
        )
      )


    val ldcal = URI("#ld-cal")
    // TODO this is temporary webapp test and will be removed
    pg.graph.union(Graph(
      Triple(ldcal,webapp.description,Literal("Simple Linked Data calendar with agenda.")),
      Triple(ldcal,webapp.endpoint,URI(calendar)),
      Triple(ldcal,webapp.name,Literal("LD-Cal")),
      Triple(ldcal,webapp.serviceId,URI("https://ld-cal.rww.io")),
      Triple(ldcal,rdf.typ,webapp("App"))
    ))
  }

  def getSubdomainValidationTriples(subdomainUri: Rdf#URI, webidUri: Rdf#URI): Iterable[Rdf#Triple] = {
    (
      URI("") -- stampleAdmin.claimedInboxConfirmed ->- Literal("true",xsd.boolean)
        -- stampleAdmin.subdomainCreated ->- subdomainUri
        -- stampleAdmin.webIdCardCreated ->- webidUri
      ).graph.triples
  }

  /**
   * As the card is created initially without any certificate, this method permits to append a public certificate to a Person
   * @param key
   * @return
   */
  def getCardPublicKeyTriples(key: RSAPublicKey): Iterable[Rdf#Triple] = {
    val pg = URI(personFragment) -- cert.key ->- (
      bnode() -- cert.exponent ->- Literal(key.getPublicExponent.toString(10), xsd.integer)
        -- cert.modulus ->- Literal(key.getModulus.toString(16), xsd.hexBinary)
      )
    pg.graph.triples
  }


  def domainAcl(domain: String): Rdf#Graph = {
    val pg: PointedGraph[Rdf] = ( bnode() -- wac.accessToClass ->- (
      bnode() -- wac.regex ->- Literal("https://"+domain + ".*"))
      -- wac.mode ->- wac.Read
      -- wac.mode ->- wac.Write
      -- wac.agent ->- URI("card"+personFragment)
      )

    pg.graph
  }

  def calendarEventEmptyGraph: Rdf#Graph = Graph(Triple(URI(""),rdf.typ, stampleDisplay.EventsDocument))

  def publicCardAcl: Rdf#Graph = {
    // All agents should be able to access the user's public key to be able to authenticate him
    val readAccessForAllAgents: Rdf#Graph =
      ( bnode()
        -- wac.accessTo ->- URI("card")
        -- wac.agentClass ->- foaf.Agent
        -- wac.mode ->- wac.Read
        ).graph
    // The subdomain Acl permits to give write permission to the subdomain owner
    val importContainerAcl = ( URI("") -- wac.include ->- URI(".acl") ).graph
    readAccessForAllAgents union importContainerAcl
  }

  def createAndSetAcl(container: Rdf#URI, slug: String, graph: Rdf#Graph) = {
    for {
      ldpr <- createLDPR(container, Some(slug), graph)
      meta <- getMeta(ldpr)
      _ <- updateLDPR(meta.acl.get,add=publicCardAcl.triples)
    } yield {
      ldpr
    }
  }

}

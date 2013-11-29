package controllers

import rww.ldp.RWWeb
import org.w3.banana._
import play.api.mvc.{ResponseHeader, SimpleResult, Action}
import play.api.mvc.Results._
import java.security.cert.X509Certificate
import java.net.{URI=>jURI,URL=>jURL}
import rww.ldp.LDPCommand._
import scala.concurrent.{ExecutionContext, Future}
import java.security.interfaces.RSAPublicKey
import org.w3.banana.plantain.Plantain
import java.util.Date
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import play.api.Logger
import java.security.PublicKey
import play.api.libs.iteratee.Enumerator
import play.Play
import scala.Some
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import controllers.CreateUserSpaceForm
import scala.Some
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import controllers.CreateUserSpaceForm


case class CreateUserSpaceForm(subdomain: String, key: PublicKey, email: String)

/**
 *
 */
class Subdomains[Rdf<:RDF](subdomainContainer: jURL, rww: RWWeb[Rdf])
                         (implicit ops: RDFOps[Rdf]) {


  import ops._
  import org.w3.banana.syntax.URISyntax._

  val ldp = LDPPrefix[Rdf]
  val cert = CertPrefix[Rdf]
  val foaf = FOAFPrefix[Rdf]
  val wac = WebACLPrefix[Rdf]
  val calendar = "calendar"



  val container = Graph(Triple(URI(""), rdf.typ, ldp.Container))

  val subDomainContainerUri = URI(subdomainContainer.toString)

  def create() = Action{ request =>
    Ok("something")
  }

  private
  def cardGraph(key: RSAPublicKey, email: String): Rdf#Graph = {
    val certNode = bnode()
    import diesel._
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
    import syntax.GraphSyntax._

    val ldcal = bnode("#ld-cal")
    def webapp(name: String): Rdf#URI = URI("http://ns.rww.io/wapp#"+name)

    pg.graph.union(Graph(
      Triple(ldcal,webapp("description"),TypedLiteral("Simple Linked Data calendar with agenda.")),
      Triple(ldcal,webapp("endpoint"),URI(calendar)),
      Triple(ldcal,webapp("name"),TypedLiteral("LD-Cal")),
      Triple(ldcal,webapp("serviceId"),URI("https://ld-cal.rww.io")),
      Triple(ldcal,rdf.typ,webapp("App"))
    ))
  }

  private def domainAcl(domain: String): Rdf#Graph = {
    import diesel._
    val pg: PointedGraph[Rdf] = ( bnode() -- wac.accessToClass ->- (
        bnode() -- wac.regex ->- TypedLiteral(domain + ".*"))
       -- wac.mode ->- wac.Read
       -- wac.mode ->- wac.Write
       -- wac.agent ->- URI("card#i")
      )

    pg.graph
  }



  import ClientCertificateApp._
  val createUserSpaceForm : Form[CreateUserSpaceForm] = Form(
    mapping(
      "subdomain" -> nonEmptyText(minLength = 3),
      "spkac" -> of(spkacFormatter),
      "email" -> email
    )(CreateUserSpaceForm.apply)(CreateUserSpaceForm.unapply)
  )

  def index = Action {
    Ok(views.html.index(createUserSpaceForm))
  }


  def createUserSpace = Action.async { implicit request =>
    import ExecutionContext.Implicits.global  //todo import Play execution context
    createUserSpaceForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.index(formWithErrors))),
      form => {
        Logger.info("Will try to create new subdomain: " + form)
        // TODO handle userspace creation
//        val subdomainURL = plantain.hostRootSubdomain(form.subdomain)
        val res = deploy(form.subdomain.toLowerCase,form.key.asInstanceOf[RSAPublicKey],form.email,tenMinutesAgo,yearsFromNow(2))
        res.map { case (domain,cert) =>
          SimpleResult(
            //https://developer.mozilla.org/en-US/docs/NSS_Certificate_Download_Specification
            header = ResponseHeader(200, Map("Content-Type" -> "application/x-x509-user-cert")),
            body = Enumerator(cert.getEncoded)
          )
        }
      }
    )
  }

  val mail = """([\w\.]*)@.*""".r

  private
  def deploy(subdomain: String, rsaKey: RSAPublicKey, email: String,
             validFrom: Date, validTo: Date): Future[(Rdf#URI,X509Certificate)] = {
    val mail(name) = email
    import syntax.GraphSyntax._

    //1. create subdomain
    rww.execute{
      for {
        subdomain <- createContainer(subDomainContainerUri, Some(subdomain), container)
        subDomainMeta <- getMeta(subdomain)
        _     <- updateLDPR(subDomainMeta.acl.get,add=domainAcl(subdomain.toString).toIterable)
        card  <- createAndSetAcl(subdomain, "card", cardGraph(rsaKey,email))
        calendar <- createAndSetAcl(subdomain,calendar,Graph(Triple(URI(""),rdf.typ, URI("http://ont.stample.co/2013/display#EventsDocument"))))
      } yield {
        val webid=card.fragment("i")
        val certreq = CertReq(name+"@"+subdomain.underlying.getHost,List(webid.underlying.toURL),rsaKey, validFrom,validTo)
        (subdomain,certreq.certificate)
      }
    }


    //2. create card

    //3. give user access to subdomain
    //
    //    meta <- getMeta(c)
    //    //locally we know we always have an ACL rel
    //    //todo: but this should really be settable in turtle files. For example it may be much better
    //    //todo: if every file in a directory just use the acl of the directory. So that would require the
    //    //todo: collection to specify how to build up the acls.
    //    aclg = (meta.acl.get -- wac.include ->- URI("../.acl")).graph
    //    _ <- updateLDPR(meta.acl.get, add = aclg.toIterable)


  }

  def createAndSetAcl(container: Rdf#URI, slug: String, graph: Rdf#Graph) = {
    import syntax.GraphSyntax._
    for {
      ldpr <- createLDPR(container, Some(slug), graph)
      meta <- getMeta(ldpr)
      _ <- updateLDPR(meta.acl.get,add=Graph(Triple(URI(""),wac.include,URI(".acl"))).toIterable)
    } yield ldpr
  }

}

object Subdomains extends Subdomains[Plantain](plantain.rwwRoot,plantain.rww) {

}
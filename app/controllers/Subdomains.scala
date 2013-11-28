package controllers

import rww.ldp.RWWeb
import org.w3.banana._
import play.api.mvc.Action
import play.api.mvc.Results._
import java.security.cert.X509Certificate
import java.net.{URI=>jURI,URL=>jURL}
import rww.ldp.LDPCommand._
import scala.concurrent.Future
import scala.Some
import java.security.interfaces.RSAPublicKey
import org.w3.banana.plantain.Plantain
import java.util.Date


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



  val container = Graph(Triple(URI(""), rdf.typ, ldp.Container))

  val subDomainContainerUri = URI(subdomainContainer.toString)

  def create() = Action{ request =>
    Ok("something")
  }

  private
  def cardGraph(key: RSAPublicKey, email: jURI): Rdf#Graph = {
    val certNode = bnode()
    Graph(Triple(URI(""), foaf.primaryTopic, URI("#i")),
      Triple(URI("#i"), cert.key, certNode),
      Triple(certNode, cert.exponent, TypedLiteral(key.getPublicExponent.toString(10), xsd.integer)),
      Triple(certNode, cert.modulus, TypedLiteral(key.getModulus.toString(16), xsd.hexBinary))
    )
  }

  private
  def deploy(name: String, rsaKey: RSAPublicKey, email: jURI,
             subdomain: String,
             validFrom: Date, validTo: Date): Future[X509Certificate] = {

    //1. create subdomain
    rww.execute{
      for {
        subdomain <- createContainer(subDomainContainerUri, Some(subdomain), container)
        card  <-     createLDPR(subdomain, Some("card"), cardGraph(rsaKey,email))
      } yield {
        val webid=card.fragment("#i")
        val certreq = CertReq(name+"@"+subdomain,List(webid.underlying.toURL),rsaKey, validFrom,validTo)
        certreq.certificate
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

}

object Subdomains extends Subdomains[Plantain](plantain.rwwRoot,plantain.rww)
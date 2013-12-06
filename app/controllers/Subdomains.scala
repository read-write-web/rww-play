package controllers

import rww.ldp.RWWeb
import org.w3.banana._
import play.api.mvc.Action
import play.api.mvc.Results._
import java.security.cert.X509Certificate
import java.net.{URL=>jURL}
import rww.ldp.LDPCommand._
import scala.concurrent.Future
import org.w3.banana.plantain.Plantain
import play.api.data.Form
import play.api.data.Forms._
import play.api.Logger
import java.security.PublicKey
import play.api.libs.iteratee.Enumerator
import play.api.mvc.SimpleResult
import play.api.mvc.ResponseHeader
import scala.Some
import utils.SubdomainConfirmationMailUtils.SubdomainConfirmationLinkData
import java.nio.file.Path
import play.api.templates.{Html, Txt}
import utils.SubdomainGraphUtils

case class CreateUserSpaceForm(subdomain: String, key: PublicKey, email: String)


case class CreateCertificateRequest(key: PublicKey)

case class CreateUserSpaceRequest(subdomain: String, email: String)

/**
 *
 */
class Subdomains[Rdf<:RDF](subdomainContainer: jURL, subdomainContainerPath: Path, rww: RWWeb[Rdf])
                          (implicit ops: RDFOps[Rdf]) {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext


  val SessionGenerateCertificateForSubdomain = "generateCertificateForSubdomain"


  val calendar = "calendar"

  val subdomainGraphUtils = new SubdomainGraphUtils


  // TODO try to avoid these global imports on controller which rather not manipulate any graph but delegate this to other classes like SubdomainGraphUtils
  import ops._
  import ops.Graph
  val ldp = LDPPrefix[Rdf]
  val container = Graph(Triple(URI(""), rdf.typ, ldp.Container))
  val subDomainContainerUri = URI(subdomainContainer.toString)





  // TODO to move: not specially related to subdomains
  def index = Action {
    Ok(views.html.index())
  }

  def createSubdomain = Action {
    Ok(views.html.subdomain.createSubdomain(createUserSpaceRequestForm))
  }

  val createUserSpaceRequestForm : Form[CreateUserSpaceRequest] = Form(
    mapping(
      "subdomain" -> nonEmptyText(minLength = 3),
      "email" -> email
    )(CreateUserSpaceRequest.apply)(CreateUserSpaceRequest.unapply)
  )

  def createUserSpaceRequest = Action.async { implicit request =>
    createUserSpaceRequestForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.subdomain.createSubdomain(formWithErrors))),
      form => {
        Logger.info("Will handle new subdomain creation request: " + form)
        val confirmationPassword = generateSubdomainConfirmationPassword
        createSubdomainAdminResource(form.subdomain,form.email,confirmationPassword) map { adminResourceURI =>
          sendSubdomainConfirmationEmail(form.subdomain,form.email,confirmationPassword)
          Redirect(routes.Subdomains.subdomainWaitingConfirmation).flashing(
            "subdomain" -> form.subdomain,
            "email" -> form.email
          )
        }
      }
    )
  }

  private
  def createSubdomainAdminResourceName(subdomain: String): String = subdomain+"_admin"

  type SubdominConfirmationPassword = String

  private
  def generateSubdomainConfirmationPassword: SubdominConfirmationPassword = java.util.UUID.randomUUID().toString.substring(0,8)

  private
  def createSubdomainAdminResource(subdomain: String, email: String, confirmationPassword: SubdominConfirmationPassword): Future[(Rdf#URI)] = {
    val graph = subdomainGraphUtils.createSubdomainAdminGraph(subdomain,email): Rdf#Graph
    // TODO how to check the admin resource does not already exist?
    // TODO we don't want to "override" an existing subdomain neither create
    // TODO Should we use a "script" do to that?
    // TODO How to we know the URI of this resource ????
    val slug = Some(createSubdomainAdminResourceName(subdomain)) // TODO this should perhaps not be a slug but we may need to force the name of this resource...
    rww.execute {
      for {
        adminResource <- createLDPR(subDomainContainerUri,slug, graph)
      } yield adminResource
    }
  }


  def subdomainWaitingConfirmation() = Action { implicit request =>
    (for {
      subdomain <- request.flash.get("subdomain")
      email <- request.flash.get("email")
    } yield {
      Ok( views.html.subdomain.subdomainWaitingConfirmation(subdomain,email) )
    }).getOrElse(BadRequest("Bad request, maybe your subdomain has already been created and you will receive a confirmation link"))
  }

  def sendSubdomainConfirmationEmail(subdomain: String, email: String,confirmationPassword: SubdominConfirmationPassword): Unit = {
    import utils.SubdomainConfirmationMailUtils._
    import utils.Mailer._
    val linkData = SubdomainConfirmationLinkData(subdomain,email,confirmationPassword)
    val link = createSignedSubdomainConfirmationLink(routes.Subdomains.confirmSubdomain.url,linkData)
    // TODO remove hardcoded domain
    val temporaryHardcodedDomain = "https://localhost:8443"
    val linkWithDomain = temporaryHardcodedDomain + link
    // TODO email templating
    val txt = Txt("Click here: " + linkWithDomain)
    val html = Html("Click here: <a href=\"" + linkWithDomain + "\"> VALIDATE </a>")
    sendEmail(
      to = email,
      subject = "Subdomain Confirmation email: "+subdomain,
      body = (txt,html)
    )
  }

  def confirmSubdomain() = Action.async { implicit request =>
  // TODO add querystring signature verification
    import utils.SubdomainConfirmationMailUtils._
    getSubdomainConfirmationLinkData(request) map { linkData =>
      doConfirmSubdomain(linkData) map { subdomainCreated =>
        Logger.info(s"Subdomain has been created: $subdomainCreated")
        val subdomainURL = "https://"+linkData.subdomain+".localhost:8443/" // TODO url of subdomain non hardcoded
        // TODO maybe the session is not the best way to transmit the domain info to the next request???
        Ok(views.html.subdomain.subdomainConfirmation(linkData.subdomain,subdomainURL))
          .withSession(SessionGenerateCertificateForSubdomain -> linkData.subdomain)
      }
    } recover {
      case e => {
        Logger.error("Error during subdomain confirmation request",e)
        // TODO add a more user friendly error
        Future.successful(BadRequest("This confirmation link seems unusable. Maybe it has already been used"))
      }
    } get
  }

  case class SubdomainCreated(subdomain: Rdf#URI,card: Rdf#URI)

  private
  def doConfirmSubdomain(linkData: SubdomainConfirmationLinkData): Future[SubdomainCreated] = {
    Logger.warn("TODO Confirming domain with data " + linkData)
    // TODO check the one time password
    // TODO check subdomain doesn't already exist
    createSubdomain(linkData.subdomain,linkData.email)
  }

  private
  def createSubdomain(subdomain: String, email: String): Future[SubdomainCreated] = {
    import syntax.GraphSyntax._
    // TODO this should not be a slug normally because it may create another domain if the asked domain is already taken or invalid domain name etc...
    val subdomainSlug = Some(subdomain)
    rww.execute {
      for {
        subdomain <- createContainer(subDomainContainerUri, subdomainSlug, container)
        subdomainMeta <- getMeta(subdomain)
        _ <- updateLDPR(subdomainMeta.acl.get, add = subdomainGraphUtils.domainAcl(subdomain.toString).toIterable )
        card <- subdomainGraphUtils.createAndSetAcl(subdomain, "card", subdomainGraphUtils.createSubdomainWebIdCardGraph(email))
        calendar <- subdomainGraphUtils.createAndSetAcl(subdomain,calendar, subdomainGraphUtils.calendarEventEmptyGraph)
      } yield SubdomainCreated(subdomain,card)
    }
  }

  val createCertificateRequestForm : Form[CreateCertificateRequest] = Form(
    mapping(
      "spkac" -> of(ClientCertificateApp.spkacFormatter)
    )(CreateCertificateRequest.apply)(CreateCertificateRequest.unapply)
  )

  def createCertificate = Action.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    createCertificateRequestForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest("No certificate found in this request")),
      form => {
        request.session.get(SessionGenerateCertificateForSubdomain) match {
          case None => Future.successful(BadRequest("Can't find the subdomain for which you want to create a certificate")) // TODO redirect to appropriate page
          case Some(subdomain) => {
            appendCertificateToSubdomainOwnerCard(subdomain,form.key) map { certificate =>
              SimpleResult(
                //https://developer.mozilla.org/en-US/docs/NSS_Certificate_Download_Specification
                header = ResponseHeader(200, Map("Content-Type" -> "application/x-x509-user-cert")),
                body = Enumerator(certificate.getEncoded)
              ).withSession(request.session - SessionGenerateCertificateForSubdomain)
            }
          }
        }
      }
    )
  }

  def appendCertificateToSubdomainOwnerCard(subdomain: String, key: PublicKey): Future[X509Certificate] = {
    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    Logger.info(s"Adding new certificate for owner of domain $subdomain")
    val name = "todo"
    val webIdUrl = "https://www.toto.webid"
    val certReq = CertReq(name+"@"+subdomain,List(new jURL(webIdUrl)),key,ClientCertificateApp.tenMinutesAgo,ClientCertificateApp.yearsFromNow(2))
    Future.successful(certReq.certificate)
  }

























  //
  //  def createUserSpace = Action.async { implicit request =>
  //    import ExecutionContext.Implicits.global  //todo import Play execution context
  //    import ExecutionContext.Implicits.global  //todo import Play execution context
  //    createUserSpaceForm.bindFromRequest.fold(
  //      formWithErrors => Future.successful(BadRequest(views.html.createSubdomain(formWithErrors))),
  //      form => {
  //        Logger.info("Will try to create new subdomain: " + form)
  //        // TODO handle userspace creation
  ////        val subdomainURL = plantain.hostRootSubdomain(form.subdomain)
  //        val res = deploy(form.subdomain.toLowerCase,form.key.asInstanceOf[RSAPublicKey],form.email,tenMinutesAgo,yearsFromNow(2))
  //        res.map { case (domain,cert) =>
  //          SimpleResult(
  //            //https://developer.mozilla.org/en-US/docs/NSS_Certificate_Download_Specification
  //            header = ResponseHeader(200, Map("Content-Type" -> "application/x-x509-user-cert")),
  //            body = Enumerator(cert.getEncoded)
  //          )
  //        }
  //      }
  //    )
  //  }









  /*
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
        _     <- updateLDPR(subDomainMeta.acl.get,add=subdomainGraphUtils.domainAcl(subdomain.toString).toIterable)
        card  <- createAndSetAcl(subdomain, "card", subdomainGraphUtils.createSubdomainWebIdCardGraph(rsaKey,email))
        calendar <- createAndSetAcl(subdomain,calendar,Graph(Triple(URI(""),rdf.typ, stampleDisplay.EventsDocument)))
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
  */


}

object Subdomains extends Subdomains[Plantain](plantain.rwwRoot,plantain.rootContainerPath,plantain.rww) {

}
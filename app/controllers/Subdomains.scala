package controllers

import java.net.{URI => jURI, URL => jURL}
import java.nio.file.Path
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey

import akka.http.scaladsl.model.Uri
import org.bouncycastle.cert.X509CertificateHolder
import org.w3.banana._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee.Enumerator
import play.api.mvc.Results._
import play.api.mvc.{Action, ResponseHeader, Result}
import play.twirl.api._
import rww.ldp.LDPCommand._
import rww.ldp.actor.RWWActorSystem
import utils.ActionUtils.SignedQueryStringAction
import utils.Mailer
import utils.subdomain.SubdomainConfirmationMailUtils.SubdomainConfirmationLinkData
import utils.subdomain.{SubdomainAdminGraphWrapper, SubdomainGraphUtils}

import scala.concurrent.Future

case class CreateUserSpaceForm(subdomain: String, key: PublicKey, email: String)


case class CreateCertificateRequest(key: PublicKey)

case class CreateUserSpaceRequest(subdomain: String, email: String)

/**
 *
 */
class Subdomains[Rdf<:RDF](
  subdomainContainer: jURL,
  subdomainContainerPath: Path,
  rwwAgent: RWWActorSystem[Rdf],
  mailer: Mailer
)(implicit
  ops: RDFOps[Rdf]
) {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  import mailer._

  val SessionGenerateCertificateForSubdomain = "generateCertificateForSubdomain"


  val calendar = "calendar"

  val subdomainGraphUtils = new SubdomainGraphUtils


  // TODO try to avoid these global imports on controller which rather not manipulate any graph but delegate this to other classes like SubdomainGraphUtils
  import ops.{Graph, _}
  val ldp = LDPPrefix[Rdf]
  val container = Graph(Triple(URI(""), rdf.typ, ldp.Container))
  val subDomainContainerUri = URI(subdomainContainer.toString)



  def createSubdomain = Action {
    Ok(views.html.subdomain.createSubdomain(createUserSpaceRequestForm))
  }

  val createUserSpaceRequestForm : Form[CreateUserSpaceRequest] = Form(
    mapping(
      "subdomain" -> nonEmptyText(minLength = 3).transform(s => s.toLowerCase,identity[String]),
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
    val graph = subdomainGraphUtils.createSubdomainAdminGraph(subdomain,email,confirmationPassword): Rdf#Graph
    // TODO how to check the admin resource does not already exist?
    // TODO we don't want to "override" an existing subdomain neither create
    // TODO Should we use a "script" do to that?
    // TODO How to we know the URI of this resource ????
    val slug = Some(createSubdomainAdminResourceName(subdomain)) // TODO this should perhaps not be a slug but we may need to force the name of this resource...
    rwwAgent.execute {
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
    import utils.subdomain.SubdomainConfirmationMailUtils._
    val linkData = SubdomainConfirmationLinkData(subdomain,email,confirmationPassword)
    val baseLinkUrl = RdfSetup.hostRoot.toString + routes.Subdomains.confirmSubdomain.url
    val link = createSignedSubdomainConfirmationLinkPath(baseLinkUrl,linkData)
    // TODO email templating
    val txt = Txt("Click here: " + link)
    val html = Html("Click here: <a href=\"" + link + "\"> VALIDATE </a>")
    Logger.info(s"Sending email to validate subdomain $subdomain to email $email with confirmation link $link")
    sendEmail(
      to = email,
      subject = "Subdomain Confirmation email: "+subdomain,
      body = (txt,html)
    )
  }

  def confirmSubdomain() = SignedQueryStringAction.async { implicit request =>
    import utils.subdomain.SubdomainConfirmationMailUtils._
    getSubdomainConfirmationLinkData(request).map ({ linkData =>
      doConfirmSubdomain(linkData) map { subdomainCreated =>
        Logger.info(s"Subdomain has been created: $subdomainCreated")
        val subdomainURL = RdfSetup.hostRootSubdomain(linkData.subdomain).toString
        // TODO maybe the session is not the best way to transmit the domain info to the next request???
        Ok(views.html.subdomain.subdomainConfirmation(linkData.subdomain,subdomainURL))
          .withSession(SessionGenerateCertificateForSubdomain -> linkData.subdomain)
      }
    }).recover ({
      case e => {
        Logger.error("Error during subdomain confirmation request",e)
        // TODO add a more user friendly error
        Future.successful(BadRequest("This confirmation link seems unusable. Maybe it has already been used"))
      }
    }).get
  }

  case class SubdomainCreated(subdomain: Rdf#URI,card: Rdf#URI)

  private
  def doConfirmSubdomain(linkData: SubdomainConfirmationLinkData): Future[SubdomainCreated] = {
    getAdminResource(linkData.subdomain) flatMap { adminResourceWrapper =>
      getConfirmationDataMatchError(adminResourceWrapper,linkData) match {
        case Some(error) => throw new IllegalStateException(error)
        case None => {
          // TODO check the one time password
          // TODO check subdomain doesn't already exist
          createSubdomain(linkData.subdomain,linkData.email) map { createdSubdomain =>

            createdSubdomain
          }
        }
      }
    }
  }

  private
  def getConfirmationDataMatchError(adminResourceWrapper: SubdomainAdminGraphWrapper[Rdf],linkData: SubdomainConfirmationLinkData): Option[String] = {
    if ( adminResourceWrapper.emailConfirmed ) {
      // TODO in this case we should provide the possibility for an user to generate a new certificate
      Some(s"The email is already confirmed. The subdomain may probably already exist or has already been confirmed: ${linkData.subdomain}}")
    } else if ( adminResourceWrapper.email != linkData.email ) {
      Some(s"The email you try to confirm (${linkData.email}) is unknown")
    } else if ( adminResourceWrapper.password != linkData.password ) {
      Some(s"The validation password you sent (${linkData.password}) is wrong")
    } else {
      None
    }
  }

  // TODO not sure this is the best way to get the adminResourceURI
  private
  def getAdminResourceURI(subdomain: String) = URI(subDomainContainerUri.toString + createSubdomainAdminResourceName(subdomain))

  private
  def getAdminResource(subdomain: String): Future[SubdomainAdminGraphWrapper[Rdf]] = {
    val adminResourceURI = getAdminResourceURI(subdomain)
    rwwAgent.execute {
      for {
        adminGraph <- getLDPR(adminResourceURI)
      } yield {
        Logger.info(s"AdminResourceURI $adminResourceURI yield = resource graph $adminGraph")
        SubdomainAdminGraphWrapper(adminResourceURI,adminGraph)
      }
    }
  }


  private
  def createSubdomain(subdomainName: String, email: String): Future[SubdomainCreated] = {
    // TODO this should not be a slug normally because it may create another domain if the asked domain is already taken or invalid domain name etc...
    val subdomainSlug = Some(subdomainName)
    rwwAgent.execute {
      for {
        subdomainC <- createContainer(subDomainContainerUri, subdomainSlug, container)
        subdomainMeta <- getMeta(subdomainC)
        _ <- updateLDPR(subdomainMeta.acl.get, add = subdomainGraphUtils.domainAcl(subdomainName.toString).triples )
        card <- subdomainGraphUtils.createAndSetAcl(subdomainC, "card", subdomainGraphUtils.createSubdomainWebIdCardGraph(email))
        calendar <- subdomainGraphUtils.createAndSetAcl(subdomainC,calendar, subdomainGraphUtils.calendarEventEmptyGraph)
        adminResource <- updateLDPR(getAdminResourceURI(subdomainName), add=subdomainGraphUtils.getSubdomainValidationTriples(subdomainC,card).toIterable)
      } yield SubdomainCreated(subdomainC,card)
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
            val rsaPublicKey = form.key.asInstanceOf[RSAPublicKey] // TODO unsafe? check with henry
            doCreateCertificate(subdomain,rsaPublicKey) map { certificate =>
              Result(
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

  def doCreateCertificate(subdomain: String,publicKey: RSAPublicKey): Future[X509CertificateHolder] = {
    getAdminResource(subdomain) flatMap { adminResourceWrapper =>
    // TODO check the subdomain and webid have been created ?
      val cardUri = adminResourceWrapper.webIdCardCreated.get
      val webidUri = URI(Uri(cardUri.toString+"#i").toString) // TODO remove hardcoded
      val certificate = createX509Certificate(webidUri,subdomain,publicKey)
      addPublicKeyToCard(cardUri,publicKey) map { unit =>
        certificate
      }
    }
  }

  def createX509Certificate(webid: Rdf#URI,subdomain: String, key: PublicKey): X509CertificateHolder = {
    Logger.info(s"Adding new certificate for owner of domain $subdomain")
    val webIdUrl = new jURI(Uri(webid.toString).toString())
    val subdomainURL = RdfSetup.hostRootSubdomain(subdomain).toString
    val commonName = "WebID Cert for " +subdomainURL
    val certReq = CertReq(commonName,List(webIdUrl),key,ClientCertificateApp.tenMinutesAgo,ClientCertificateApp.yearsFromNow(2))
    certReq.certificate
  }

  def addPublicKeyToCard(cardURI: Rdf#URI,publicKey: RSAPublicKey): Future[Unit] = {
    rwwAgent.execute {
      for {
        card <- updateLDPR(cardURI,add=subdomainGraphUtils.getCardPublicKeyTriples(publicKey))
      } yield card
    }
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

object Subdomains extends Subdomains(RdfSetup.rwwRoot,RdfSetup.rootContainerPath,RdfSetup.rwwAgent,RdfSetup.mailer())
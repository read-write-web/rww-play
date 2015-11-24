package ldp.auth

import java.net.URL
import java.nio.file.{Files, Path}
import java.security.Principal
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.io.{RDFReader, RDFWriter, Turtle}
import rww.ldp.LDPCommand._
import rww.ldp._
import rww.ldp.actor.RWWActorSystem
import rww.ldp.auth.{Claim, WACAuthZ, WebIDVerifier, X509CertSigner}
import sun.security.x509.X500Name
import test.ldp.TestSetup._
import test.ldp.{TestGraphs, TestHelper}

import scala.concurrent.Future
import scala.util.Try


class RdfWebIDVerifierTest
  extends WebIDVerifierTest[Rdf](baseUri)(
    ops,recordBinder,sparqlOps,sparqlGraph,turtleWriter,turtleReader
  )


/**
 * Test WebIDVerifier
 *
 */
abstract class WebIDVerifierTest[Rdf<:RDF](
  baseUri: Rdf#URI
)(implicit
  val ops: RDFOps[Rdf],
  val recordBinder: binder.RecordBinder[Rdf],
  sparqlOps: SparqlOps[Rdf],
  sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
  turtleWriter: RDFWriter[Rdf, Try, Turtle],
  reader: RDFReader[Rdf, Try, Turtle]
) extends WordSpec with Matchers with BeforeAndAfterAll with TestHelper with TestGraphs[Rdf] {

  import ops._

  val dir: Path = Files.createTempDirectory("WebIDVerifierTest" )

  implicit val timeout: Timeout = Timeout(1, TimeUnit.MINUTES)
  val rwwAgent: RWWActorSystem[Rdf] = actor.RWWActorSystemImpl.plain[Rdf](baseUri, dir, testFetcher)

  implicit val authz: WACAuthZ[Rdf] =  new WACAuthZ[Rdf](new WebResource(rwwAgent))

  val webidVerifier = new WebIDVerifier(new WebResource(rwwAgent))

  val web = new WebResource[Rdf](rwwAgent)

  val bertailsCertSigner = new X509CertSigner(bertKeyPair.priv)
  val bertailsCert = bertailsCertSigner.generate(new X500Name("CN=Alex, O=W3C"),
    bertKeyPair.pub,2,new URL(bertails.getString))

  //of course bertails cannot create a cert with henry's public key, because not having the private key he would not
  //be able to connect to a server with it. So he must use a different key - his own will do for the test
  val bertailsFakeHenryCert = bertailsCertSigner.generate(new X500Name("CN=Henry, O=bblfish.net"),
    bertKeyPair.pub,3,new URL(henry.getString))

  "add bertails card and acls" in {
    val script = rwwAgent.execute(for {
      ldpc     <- createContainer(baseUri,Some("bertails"),Graph.empty)
      ldpcMeta <- getMeta(ldpc)
      card     <- createLDPR(ldpc,Some(bertailsCard.lastPathSegment),bertailsCardGraph)
      keyDoc <- createLDPR(ldpc,Some(bertailsKeyDoc.lastPathSegment),bertailsKeyGraph)
      keyGraph <- getLDPR(keyDoc)
      cardMeta <- getMeta(card)
      _        <- updateLDPR(ldpcMeta.acl.get, add=bertailsContainerAclGraph.triples)
      _        <- updateLDPR(cardMeta.acl.get, add=bertailsCardAclGraph.triples)
      rGraph   <- getLDPR(card)
      aclGraph <- getLDPR(cardMeta.acl.get)
      containerAclGraph <- getLDPR(ldpcMeta.acl.get)
    } yield {
      ldpc should be(bertailsContainer)
      cardMeta.acl.get should be(bertailsCardAcl)
      assert(rGraph isIsomorphicWith (bertailsCardGraph union containsRel).resolveAgainst(bertailsCard))
      assert(aclGraph isIsomorphicWith bertailsCardAclGraph.resolveAgainst(bertailsCardAcl))
      assert(containerAclGraph isIsomorphicWith bertailsContainerAclGraph.resolveAgainst(bertailsContainerAcl))
    })
    script.getOrFail()
  }

  "verify bertails' cert" in {
    val webidListFuture = webidVerifier.verify(Claim.ClaimMonad.point(bertailsCert))
    val futureWebids = Future.find(webidListFuture)(_=>true)
    val webidOpt = futureWebids.getOrFail()
    webidOpt.map{p=>p.getName} should be(Some(bertails.getString))
  }

  "verify bertails' fake cert fails" in {
    val webidListFuture = webidVerifier.verify(Claim.ClaimMonad.point(bertailsFakeHenryCert))
    val firstFuture: Future[Option[Principal]] = Future.find(webidListFuture)(_=>true)
    val res = firstFuture.getOrFail()
    res should be(None)
  }


  val henryCertSigner = new X509CertSigner(henryKeyPair.priv)
  val henryCert = henryCertSigner.generate(new X500Name("CN=Henry, O=bblfish.net"),henryKeyPair.pub,2,new URL(henry.toString))
  val henrysFakeBertailsCert = henryCertSigner.generate(new X500Name("CN=Alex, O=W3C"),henryKeyPair.pub,3,new URL(bertails.toString))


  "verify Henry's cert" in {
    val webidListFuture = webidVerifier.verify(Claim.ClaimMonad.point(henryCert))
    val futureWebids = Future.find(webidListFuture)(_=>true)
    val webids = futureWebids.getOrFail()
    webids.map{p=>p.getName} should be(Some(henry.getString))
  }

  "verify Henry's fake cert fails" in {
    val webidListFuture = webidVerifier.verify(Claim.ClaimMonad.point(henrysFakeBertailsCert))
    val futureWebids = Future.find(webidListFuture)(_=>true)
    val res = futureWebids.getOrFail()
    res should be(None)
  }


}

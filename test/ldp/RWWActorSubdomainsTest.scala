package test.ldp

import java.net.{URI => jURI}
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import org.scalatest.{Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.io.{RDFReader, RDFWriter, Turtle}
import rww.ldp.actor.router.RWWRoutingActorSubdomains
import rww.ldp.actor.{RWWActorSystem, RWWActorSystemImpl}
import test.ldp.TestSetup._

import scala.util.Try


class RwwActorSubdomainsTest
  extends RWWActorSubdomainsTest[Rdf](baseUri, dir)(
    ops,recordBinder,sparqlOps,sparqlGraph,turtleWriter,turtleReader
  )


/**
 * Created by hjs on 23/11/2013.
 */
abstract class RWWActorSubdomainsTest[Rdf<:RDF](
  baseUri: Rdf#URI,
  dir: Path
)(implicit
  val ops: RDFOps[Rdf],
  val recordBinder: binder.RecordBinder[Rdf],
  sparqlOps: SparqlOps[Rdf],
  sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
  turtleWriter: RDFWriter[Rdf,Try,Turtle],
  reader: RDFReader[Rdf,Try, Turtle]
) extends WordSpec with Matchers with TestGraphs[Rdf] {
  import RWWRoutingActorSubdomains._
  import rww.ldp.LDPCommand._


  implicit val timeout: Timeout = Timeout(1,TimeUnit.MINUTES)
  import ops._

  val localBase                     = new jURI("https://localhost:8443/2013/")
  val rootLDPCStr                   = localBase.toString
  val rwwAgent: RWWActorSystem[Rdf] = RWWActorSystemImpl.withSubdomains[Rdf](baseUri,dir,testFetcher)


  def subdomain(sub: String,lb: jURI) =
    s"${lb.getScheme}://$sub.${lb.getAuthority}"


  s"base=$localBase tests relative uris" in {
    local(new jURI("/2013/"), localBase )  should be (Some(SubdomainSwitch(None,"")))
    local(new jURI("/2013/.acl"), localBase )  should be (Some(SubdomainSwitch(None,"")))
    local(new jURI("/2013/test"), localBase )  should be (Some(SubdomainSwitch(None,"test")))
    local(new jURI("/2013/test/"), localBase )  should be (Some(SubdomainSwitch(None,"test")))
    local(new jURI("/2013/test/img"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new jURI("/2013/test/img/"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new jURI("/2013/test/img/cat.jpg"), localBase )  should be (Some(SubdomainSwitch(None,"test/img/cat")))
    local(new jURI("/2013/test/img/.acl.ttl"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new jURI("/2013/card.acl"), localBase )  should be (Some(SubdomainSwitch(None,"card")))
  }

  s"base=$localBase tests full uris on localhost" in {
    local(new jURI(rootLDPCStr+"card.acl"), localBase) should be (Some(SubdomainSwitch(None,"card")))
    local(new jURI(rootLDPCStr+".acl"), localBase) should be (Some(SubdomainSwitch(None,"")))
    local(new jURI(rootLDPCStr+"test/img"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new jURI(rootLDPCStr+"test/img/"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new jURI(rootLDPCStr+"test/img/cat.jpg"), localBase )  should be (Some(SubdomainSwitch(None,"test/img/cat")))
    local(new jURI(rootLDPCStr+"test/img/.acl.ttl"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new jURI(rootLDPCStr+"card.acl"), localBase )  should be (Some(SubdomainSwitch(None,"card")))

  }

  s"base=$localBase tests with subdomains on localhost" in {
    local(new jURI(subdomain("joe",localBase)+"/2013/"),localBase) should be (Some(SubdomainSwitch(Some("joe"),"2013")))
    local(new jURI(subdomain("james",localBase)+"/2013"),localBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new jURI(subdomain("james",localBase)+""),localBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new jURI(subdomain("james",localBase)+"/.acl"),localBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new jURI(subdomain("james",localBase)+"/2013/.acl"),localBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new jURI(subdomain("slim",localBase)+"/"),localBase) should be (Some(SubdomainSwitch(Some("slim"),"")))
  }

  val base_jURI = new jURI(baseUri.toString)

  s"base=$base_jURI tests with subdomains for baseURI of example" in {
    local(new java.net.URL(base_jURI.toURL,"").toURI,base_jURI) should be (Some(SubdomainSwitch(None,"")))
    local(new jURI(subdomain("joe",base_jURI)+"/2013/"),base_jURI) should be (Some(SubdomainSwitch(Some("joe"),"2013")))
    local(new jURI(subdomain("james",base_jURI)+"/2013"),base_jURI) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new jURI(subdomain("james",base_jURI)+""),base_jURI) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new jURI(subdomain("james",base_jURI)+"/.acl"),base_jURI) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new jURI(subdomain("james",base_jURI)+"/2013/.acl"),base_jURI) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new jURI(subdomain("slim",base_jURI)+"/"),base_jURI) should be (Some(SubdomainSwitch(Some("slim"),"")))
  }

  val stampleBase = new jURI("https://stample.io/2013/")

  s"base=$stampleBase tests with subdomains on stample inversing ports" in {
    local(new jURI("https://stample.io:443/2013/"),stampleBase) should be (Some(SubdomainSwitch(None,"")))
    local(new jURI(subdomain("joe",stampleBase)+"/2013/"),stampleBase) should be (Some(SubdomainSwitch(Some("joe"),"2013")))
    local(new jURI(subdomain("james",stampleBase)+"/2013"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new jURI(subdomain("james",stampleBase)+""),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new jURI(subdomain("james",stampleBase)+"/.acl"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new jURI(subdomain("james",stampleBase)+"/2013/.acl"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new jURI(subdomain("slim",stampleBase)+"/"),stampleBase) should be (Some(SubdomainSwitch(Some("slim"),"")))
  }

  "creating subdomains" when {

    "for slim, joe and a normal file" in {
      val script = rwwAgent.execute(for {
        slim <- createContainer(baseUri, Some("slim"), Graph.empty)
        joe <- createContainer(baseUri, Some("jim"), Graph.empty)
        file <- createLDPR(baseUri,Some("slim_meta"),groupACLForRegexResource)
      } yield ( slim,joe,file ))
      var container = script.getOrFail()
      container should equal((URI(subdomain("slim", base_jURI)+"/"),URI(subdomain("jim", base_jURI)+"/"),URI(base_jURI+"slim_meta")))
    }


   }

}

package test.ldp

import java.net.{URI => jURI}
import java.nio.file.Path

import org.scalatest.{Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.binder.RecordBinder
import org.w3.banana.io._
import play.api.libs.iteratee.Iteratee
import rww.ldp.actor.{RWWActorSystem, RWWActorSystemImpl}
import rww.ldp.auth.{WACAuthZ, WebIDPrincipal, WebIDVerifier}
import rww.ldp.{LDPCommand, WebResource}
import rww.play.Method
import test.ldp.TestSetup._

import scala.util.Try


object RdfWebTest extends WebTestSuite[Rdf](baseUri, dir)(
  ops,recordBinder,sparqlOps,sparqlGraph,turtleWriter,turtleReader
)

/**
 *
 * tests the local and remote LDPR request, creation, LDPC creation, access control, etc...
 */
abstract
class WebTestSuite[Rdf <: RDF](
  baseUri: Rdf#URI,
  dir: Path
)(implicit
  val ops: RDFOps[Rdf],
  val recordBinder: RecordBinder[Rdf],
  sparqlOps: SparqlOps[Rdf],
  sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
  turtleWriter: RDFWriter[Rdf, Try, Turtle],
  reader: RDFReader[Rdf, Try, Turtle]
) extends WordSpec with Matchers with TestHelper with TestGraphs[Rdf] {

  import LDPCommand._
  import diesel._
  import ops._

  val rwwAgent: RWWActorSystem[Rdf] = RWWActorSystemImpl.plain[Rdf] (baseUri, dir, testFetcher)

  val webidVerifier = new WebIDVerifier(rwwAgent)
  implicit val authz: WACAuthZ[Rdf] = new WACAuthZ[Rdf](new WebResource(rwwAgent))(ops)
  import Method._

  implicit def underlying(uri: Rdf#URI): jURI = new jURI(uri.getString)

  "access to Henry's resources" when {

    "Henry's card to its acl" in {
      val ex = authz.acl(henryCard)
      val aclsFuture = ex.run(Iteratee.fold(List[LinkedDataResource[Rdf]] ()) {
        case (lst, ldr) => ldr :: lst
      })
      val res = aclsFuture.map {
        res =>
          res.size should be(1)
          res(0).location should be(henryCardAcl)
          assert(res(0).resource.graph isIsomorphicWith henryCardAclGraph.resolveAgainst(henryCard))
      }
      res.getOrFail()
    }


    "Henry can Authenticate" in {
      val futurePrincipal = webidVerifier.verifyWebID(henry.toString, henryKeys.pub)
      val res = futurePrincipal.map {
        p =>
          assert(p.isInstanceOf[WebIDPrincipal] && p.getName == henry.toString)
      }
      res.getOrFail()
    }

    "Who can access Henry's WebID profile?" in {
      val ex = for {
        read <- authz.getAuthorizedWebIDsFor(henryCard, wac.Read)
        write <- authz.getAuthorizedWebIDsFor(henryCard, wac.Write)
      } yield {
          assert(read.contains(henry))
          assert(read.contains(foaf.Agent))
          assert(write.contains(henry))
          assert(!write.contains(foaf.Agent))
        }
      ex.getOrFail()
    }

    "What methods does Henry's WebID profile permit an anonymous user" in {
      val ex = authz.getAllowedMethodsForAgent(henryCard, List())
      val modes = ex.getOrFail()
      modes should be(Set(Read))
    }

    "What methods does Henry's WebID profile permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryCard, List(WebIDPrincipal(henry)))
      val modes = ex.getOrFail()
      modes should be(Set(Read, Write))
    }

    "What methods does Henry's WebID profile acl permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryCardAcl, List(WebIDPrincipal(henry)))
      val modes = ex.getOrFail()
      modes should be(Set(Read, Write))
    }

    "henry creates his foaf list ( no ACL here )" in {
      testFetcher.synMap -= henryFoaf
      val ex = rwwAgent.execute {
        for {
          foaf <- createLDPR(henryColl, Some("foaf"), henryFoafGraph)
          collGr <- getLDPR(henryColl)
        } yield {
          foaf should be(henryFoaf)
          assert {
            (PointedGraph(henryColl, collGr) / rdfs.member).exists(_.pointer == henryFoaf)
          }
        }
      }
      ex.getOrFail()
    }

    "Who can access Henry's foaf profile?" in {
      val ex =
        for {
          read <- authz.getAuthorizedWebIDsFor(henryFoaf, wac.Read)
          write <- authz.getAuthorizedWebIDsFor(henryFoaf, wac.Write)
        } yield {
          assert(read.contains(timbl), "timbl can read")
          assert(read.contains(bertails), "alex can read")
          assert(read.contains(henry), "henry can read")
          assert(!read.contains(foaf.Agent), "not everyone can read profile")
          assert(write.contains(henry))
          assert(!write.contains(foaf.Agent))
        }

      ex.getOrFail()
    }


    "What methods does Henry's foaf profile permit an anonymous user" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf, List())
      val modes = ex.getOrFail()
      modes should be(Set())
    }

    "What methods does Henry's foaf profile permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf, List(WebIDPrincipal(henry)))
      val modes = ex.getOrFail()
      modes should be(Set(Read, Write))
    }

    "What methods does Henry's foaf profile acl permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoafWac, List(WebIDPrincipal(henry)))
      val modes = ex.getOrFail()
      modes should be(Set(Read, Write))
    }

    "What methods does Henry's foaf profile acl permit TimBL to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf, List(WebIDPrincipal(timbl)))
      val modes = ex.getOrFail()
      modes should be(Set(Read))
    }

  }



  "Alex's profile" when {

    "add bertails card and acls" in {
      val script = rwwAgent.execute(for {
        ldpc <- createContainer(baseUri, Some("bertails"), Graph.empty)
        ldpcMeta <- getMeta(ldpc)
        card <- createLDPR(ldpc, Some(bertailsCard.lastPathSegment), bertailsCardGraph)
        cardMeta <- getMeta(card)
        _ <- updateLDPR(ldpcMeta.acl.get, add = bertailsContainerAclGraph.triples)
        _ <- updateLDPR(cardMeta.acl.get, add = bertailsCardAclGraph.triples)
        rGraph <- getLDPR(card)
        aclGraph <- getLDPR(cardMeta.acl.get)
        containerAclGraph <- getLDPR(ldpcMeta.acl.get)
      } yield {
          ldpc should be(bertailsContainer)
          cardMeta.acl.get should be(bertailsCardAcl)
          val shouldBe = (bertailsCardGraph union containsRel).resolveAgainst(bertailsCard)
          println(s"~~~~~>rGraph $rGraph should be $shouldBe ")
          assert(rGraph isIsomorphicWith shouldBe)
          assert(aclGraph isIsomorphicWith bertailsCardAclGraph.resolveAgainst(bertailsCardAcl))
          assert(containerAclGraph isIsomorphicWith bertailsContainerAclGraph.resolveAgainst
          (bertailsContainerAcl))
        })
      script.getOrFail()

    }

    "Alex can Authenticate" in {
      val futurePrincipal = webidVerifier.verifyWebID(bertails.toString, bertailsKeys.pub)
      val res = futurePrincipal.map {
        p =>
          assert(p.isInstanceOf[WebIDPrincipal] && p.getName == bertails.toString)
      }
      res.getOrFail()
    }

    "can Access Alex's profile" in {
      val ex = for {
        read <- authz.getAuthorizedWebIDsFor(bertailsCard, wac.Read)
        write <- authz.getAuthorizedWebIDsFor(bertailsCard, wac.Write)
      } yield {
          assert(read.contains(bertails))
          assert(read.contains(foaf.Agent))
          assert(write.contains(bertails))
          assert(!write.contains(henry))
          assert(!write.contains(foaf.Agent))
        }
      ex.getOrFail()
    }

    "can Access other resources in Alex's container" in {
      val ex = for {
        read <- authz.getAuthorizedWebIDsFor(bertailsCard, wac.Read)
        write <- authz.getAuthorizedWebIDsFor(bertailsCard, wac.Write)
      } yield {
          assert(read.contains(bertails))
          assert(read.contains(foaf.Agent))
          assert(write.contains(bertails))
          assert(!write.contains(foaf.Agent))
        }
      ex.getOrFail()
    }

    "What methods does Alex's profile permit an anonymous user" in {
      val ex = authz.getAllowedMethodsForAgent(bertailsCard, List())
      val modes = ex.getOrFail()
      modes should be(Set(Read))
    }

    "What methods does Alex's profile permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(bertailsCard, List(WebIDPrincipal(henry)))
      val modes = ex.getOrFail()
      modes should be(Set(Read))
    }

    "What methods does Alex's profile acl permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(bertailsCard, List(WebIDPrincipal(bertails)))
      val modes = ex.getOrFail()
      modes should be(Set(Read, Write))
    }

    "What methods does Alex's profile acl permit TimBL to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf, List(WebIDPrincipal(timbl)))
      val modes = ex.getOrFail()
      modes should be(Set(Read))
    }


  }


  "w3c WebID group" when {

    // we are going to create these
    testFetcher.synMap -= webidColl
    testFetcher.synMap -= tpacGroupDoc

    "tpac group creation" in {
      val ex = rwwAgent.execute {
        for {
          tpac <- createContainer(webidColl, Some("tpac"), Graph.empty)
          tpacGroup <- createLDPR(tpac, Some("group"), tpacGroupGraph)
          graph <- getLDPR(tpacGroup)
        } yield {
          tpac should be(tpacColl)
          tpacGroup should be(tpacGroupDoc)
          assert(graph.relativize(tpacGroupDoc) isIsomorphicWith (tpacGroupGraph))
        }
      }
      ex.getOrFail()
    }


  }


}

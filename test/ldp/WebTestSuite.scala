package test.ldp

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.scalatest.ShouldMatchers
import org.scalatest.WordSpec
import org.w3.banana._
import rww.ldp.LDPCommand._
import org.w3.banana.plantain.{LDPatch, Plantain}
import play.api.libs.iteratee._
import rww.ldp.auth.{WebIDVerifier, WACAuthZ}
import rww.ldp._
import rww.ldp.actor.RWWActorSystem
import scala.Some
import rww.ldp.auth.WebIDPrincipal
import scala.util.Try
import akka.util.Timeout


class PlantainWebTest extends WebTestSuite[Plantain](baseUri,dir)
/**
 *
 * tests the local and remote LDPR request, creation, LDPC creation, access control, etc...
 */
abstract class WebTestSuite[Rdf<:RDF](baseUri: Rdf#URI, dir: Path)
                                     (implicit val ops: RDFOps[Rdf],
                                      sparqlOps: SparqlOps[Rdf],
                                      sparqlGraph: SparqlGraph[Rdf],
                                      val recordBinder: binder.RecordBinder[Rdf],
                                      turtleWriter: RDFWriter[Rdf,Turtle],
                                      reader: RDFReader[Rdf, Turtle],
                                      patch: LDPatch[Rdf,Try])
    extends WordSpec with ShouldMatchers  with TestHelper with TestGraphs[Rdf]  {

    import diesel._
    import ops._
    import syntax._

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)
  val rww: RWWActorSystem[Rdf] = actor.RWWActorSystemImpl.plain[Rdf](baseUri, dir, testFetcher)

  val webidVerifier = new WebIDVerifier(rww)
  implicit val authz: WACAuthZ[Rdf] =  new WACAuthZ[Rdf](new WebResource(rww))(ops)


  "access to Henry's resources" when {

    "Henry's card to its acl" in {
      val ex = authz.acl(henryCard)
      val aclsFuture = ex.run(Iteratee.fold(List[LinkedDataResource[Rdf]]()){case (lst,ldr)=> ldr::lst })
      val res = aclsFuture.map{ res =>
        res.size should be(1)
        res(0).location should be(henryCardAcl)
        assert(res(0).resource.graph isIsomorphicWith henryCardAclGraph.resolveAgainst(henryCard))
      }
      res.getOrFail()
    }


    "Henry can Authenticate" in {
      val futurePrincipal = webidVerifier.verifyWebID(henry.toString, henryKeys.pub)
      val res = futurePrincipal.map{p=>
        assert(p.isInstanceOf[WebIDPrincipal] && p.getName == henry.toString)
      }
      res.getOrFail()
    }

    "Who can access Henry's WebID profile?" in {
      val ex = for {
          read <- authz.getAuthorizedWebIDsFor(henryCard,wac.Read)
          write <- authz.getAuthorizedWebIDsFor(henryCard,wac.Write)
         } yield {
           assert(read.contains(henry))
           assert(read.contains(foaf.Agent))
           assert(write.contains(henry))
           assert(!write.contains(foaf.Agent))
         }
      ex.getOrFail()
    }

    "What methods does Henry's WebID profile permit an anonymous user" in {
      val ex = authz.getAllowedMethodsForAgent(henryCard,List())
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read))
    }

    "What methods does Henry's WebID profile permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryCard,List(henry))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read,wac.Write))
    }

    "What methods does Henry's WebID profile acl permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryCardAcl,List(henry))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read,wac.Write))
    }

    "henry creates his foaf list ( no ACL here )" in {
      testFetcher.synMap -= henryFoaf
      val ex = rww.execute{
        for {
          foaf <- createLDPR(henryColl,Some("foaf"),henryFoafGraph)
          collGr <- getLDPR(henryColl)
        } yield {
          foaf should be(henryFoaf)
          assert{
            (PointedGraph(henryColl,collGr)/rdfs.member).exists(_.pointer == henryFoaf)
          }
        }
      }
      ex.getOrFail()
    }

    "Who can access Henry's foaf profile?" in {
      val ex =
        for {
          read <- authz.getAuthorizedWebIDsFor(henryFoaf,wac.Read)
         write <- authz.getAuthorizedWebIDsFor(henryFoaf,wac.Write)
        } yield {
          assert(read.contains(timbl),"timbl can read")
          assert(read.contains(bertails),"alex can read")
          assert(read.contains(henry),"henry can read")
          assert(!read.contains(foaf.Agent),"not everyone can read profile")
          assert(write.contains(henry))
          assert(!write.contains(foaf.Agent))
        }

      ex.getOrFail()
    }


    "What methods does Henry's foaf profile permit an anonymous user" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf,List())
      val modes = ex.getOrFail()
      modes should be(Set())
    }

    "What methods does Henry's foaf profile permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf,List(henry))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read,wac.Write))
    }

    "What methods does Henry's foaf profile acl permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoafWac,List(henry))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read,wac.Write))
    }

    "What methods does Henry's foaf profile acl permit TimBL to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf,List(timbl))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read))
    }

  }



  "Alex's profile" when {

    "add bertails card and acls" in {
      val script = rww.execute(for {
        ldpc     <- createContainer(baseUri,Some("bertails"),Graph.empty)
        ldpcMeta <- getMeta(ldpc)
        card     <- createLDPR(ldpc,Some(bertailsCard.lastPathSegment),bertailsCardGraph)
        cardMeta <- getMeta(card)
        _        <- updateLDPR(ldpcMeta.acl.get, add=bertailsContainerAclGraph.toIterable)
        _        <- updateLDPR(cardMeta.acl.get, add=bertailsCardAclGraph.toIterable)
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

    "Alex can Authenticate" in {
      val futurePrincipal = webidVerifier.verifyWebID(bertails.toString,bertailsKeys.pub)
      val res = futurePrincipal.map{p=>
        assert(p.isInstanceOf[WebIDPrincipal] && p.getName == bertails.toString)
      }
      res.getOrFail()
    }

    "can Access Alex's profile" in {
      val ex = for {
          read <- authz.getAuthorizedWebIDsFor(bertailsCard,wac.Read)
          write <- authz.getAuthorizedWebIDsFor(bertailsCard,wac.Write)
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
      val ex = authz.getAllowedMethodsForAgent(bertailsCard,List())
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read))
    }

    "What methods does Alex's profile permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(bertailsCard,List(henry))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read))
    }

    "What methods does Alex's profile acl permit Henry to access" in {
      val ex = authz.getAllowedMethodsForAgent(bertailsCard,List(bertails))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read,wac.Write))
    }

    "What methods does Alex's profile acl permit TimBL to access" in {
      val ex = authz.getAllowedMethodsForAgent(henryFoaf,List(timbl))
      val modes = ex.getOrFail()
      modes should be(Set(wac.Read))
    }


  }


  "w3c WebID group" when {

    // we are going to create these
    testFetcher.synMap -= webidColl
    testFetcher.synMap -= tpacGroupDoc

    "tpac group creation" in {
      val ex = rww.execute {
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

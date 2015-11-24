package test.ldp

import java.net.{URI => jURI}
import java.nio.file.Path

import org.scalatest.{Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.binder.RecordBinder
import org.w3.banana.io._
import play.api.libs.iteratee.Iteratee
import rww.ldp.LDPExceptions.NoAuthorization
import rww.ldp.actor.{RWWActorSystem, RWWActorSystemImpl}
import rww.ldp.auth._
import rww.ldp.{LDPCommand, WebResource}
import rww.play.auth.{Subject, Anonymous}
import test.ldp.TestSetup._

import scala.util.Try


class RdfWebTest
  extends WebTestSuite[Rdf](baseUri, dir)(
  ops,recordBinder,sparqlOps,sparqlGraph,turtleWriter,turtleReader
)

/**
 *
 * tests the local and remote LDPR request, creation, LDPC creation, access control, etc...
 */
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

  val webidVerifier = new WebIDVerifier(new WebResource(rwwAgent))
  implicit val authz: WACAuthZ[Rdf] = new WACAuthZ[Rdf](new WebResource(rwwAgent))(ops)
  import Method._

  implicit def underlying(uri: Rdf#URI): jURI = new jURI(uri.getString)

  "access to Henry's resources" when {

    "Henry's card to its acl" in {
      val res = authz.aclFor(henryCard).map { optAcl =>
          optAcl.size should be(1)
          optAcl.get.location should be(henryCardAcl)
          assert(optAcl.get.pointedGraph.graph isIsomorphicWith henryCardAclGraph.resolveAgainst(henryCard))
      }
      res.getOrFail()
    }


    "Henry can Authenticate" in {
      val futurePrincipal = webidVerifier.verifyWebID(henry.toString, henryKeyPair.pub)
      val res = futurePrincipal.map {
        p =>
          assert(p.isInstanceOf[WebIDPrincipal] && p.getName == henry.toString)
      }
      res.getOrFail()
    }

    "Who can access Henry's WebID profile?" in {
      val ex = for {
        read <- authz.isAuthorized(webidToSubject(henry), Method.Read, henryCard)
        write <- authz.isAuthorized(webidToSubject(henry), Method.Write, henryCard)
      } yield {
          Set(webIdToPrincipal(henry),Agent)
          webIdToPrincipal(henry) should be(write)
        }
      ex.getOrFail()
    }

    "Can anyone access Henry's profile document in read mode" in {
      val ex = authz.isAuthorized(Anonymous,Method.Read,henryCard)
      val principal = ex.getOrFail()
      principal should be(Agent)
    }


    "Can Henry's WebID profile permit Henry identified by his key to access" in {
      for (mode <- List(Read,Write)) yield {
        val ex = authz.authorizedPrincipals(keyToSubject(henryKeyId),mode, henryCard)
        val principals = ex.getOrFail()
        if (mode==Read) principals should be(Set(keyToPrincipal(henryKeyId),Agent))
        else principals should be(Set(keyToPrincipal(henryKeyId)))
      }
    }

    "What methods does Henry's WebID profile acl permit Henry to access" in {
      for (mode <- List(Read,Write)) yield {
        val ex = authz.authorizedPrincipals(webidToSubject(henry), mode, henryCardAcl)
        val principals = ex.getOrFail()
        //note that the web access control only gives henry access in read mode, and not the world!
        //also it does not give access to henry as identified by his key, but by his WebID
        principals should be(Set(webIdToPrincipal(henry)))
      }
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

    val idp = webIdToPrincipal _

    "Who can access Henry's foaf profile?" in {
      val fusion = Subject(Set(idp(timbl),idp(henry),idp(bertails)))
      val ex =
        for {
          read <- authz.authorizedPrincipals(fusion,  Read, henryFoaf)
          write <- authz.authorizedPrincipals(fusion, Write, henryFoaf)
        } yield {
          assert(read.contains(idp(timbl)), "timbl can read")
          assert(read.contains(idp(bertails)), "alex can read")
          assert(read.contains(idp(henry)), "henry can read")
          assert(!read.contains(Agent), "not everyone can read profile")
          assert(write.contains(idp(henry)), "henry can write")
          assert(!write.contains(Agent), "Anonymous users can't write")
        }

      ex.getOrFail()
    }


    "What methods does Henry's foaf profile permit an anonymous user" in {
      val ex =
        for {
          read <- authz.authorizedPrincipals(webidToSubject(henry),  Read, henryFoaf)
          write <- authz.authorizedPrincipals(webidToSubject(henry), Write, henryFoaf)
        } yield {
          read should not contain(Agent)
          write should not contain(Agent)
          read should contain(idp(henry))
          write should contain(idp(henry))
          read.size should be(1)
          write.size should be(1)
        }
      ex.getOrFail()
    }


    "What methods does Henry's foaf profile acl permit Henry to access" in {
      val ex =
        for {
          read <- authz.authorizedPrincipals(webidToSubject(henry),  Read, henryFoafWac)
          write <- authz.authorizedPrincipals(webidToSubject(henry), Write, henryFoafWac)
        } yield {
          read should not contain(Agent)
          write should not contain(Agent)
          read should contain(idp(henry))
          write should contain(idp(henry))
          read.size should be(1)
          write.size should be(1)
        }
      ex.getOrFail()
    }

    "What methods does Henry's foaf profile acl permit TimBL to access" in {
      val timblSubj = webidToSubject(timbl)
      val ex = authz.authorizedPrincipals(timblSubj,  Read, henryFoafWac).map(p=>
        fail("TimBl is not allowed access to document in read mode")
      ).failed.map{
        case no: NoAuthorization => () //everything ok
        case x => fail("wrong error thrown: "+x)
      }

      ex.getOrFail()

      val ex2 = authz.authorizedPrincipals(timblSubj,  Write, henryFoafWac).map(p=>
        fail("TimBl is not allowed access to document in read mode")
      ).failed.map{
        case no: NoAuthorization => () //everything ok
        case x => fail("wrong error thrown: "+x)
      }

      ex2.getOrFail()
    }

  }



  "Alex's profile" when {

    "add bertails card and acls" in {
      val script = rwwAgent.execute(for {
        ldpc <- createContainer(baseUri, Some("bertails"), Graph.empty)
        ldpcMeta <- getMeta(ldpc)
        card <- createLDPR(ldpc, Some(bertailsCard.lastPathSegment), bertailsCardGraph)
        keyDoc <- createLDPR(ldpc,Some(bertailsKeyDoc.lastPathSegment),bertailsKeyGraph)
        keyGraph <- getLDPR(keyDoc)
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
          assert(rGraph isIsomorphicWith shouldBe)
          assert(aclGraph isIsomorphicWith bertailsCardAclGraph.resolveAgainst(bertailsCardAcl))
          assert(containerAclGraph isIsomorphicWith
            bertailsContainerAclGraph.resolveAgainst(bertailsContainerAcl)
          )
          keyDoc should be(bertailsKeyDoc)
          //todo: remove the links here, they should be in the headers!!
          val fullKeyGraph = (keyGraph union containsRel).resolveAgainst(keyDoc)
          assert( keyGraph  isIsomorphicWith( fullKeyGraph.resolveAgainst(bertailsKeyDoc)))
        })
      script.getOrFail()

    }

    "Alex can Authenticate" in {
      val futurePrincipal = webidVerifier.verifyWebID(bertails.toString, bertKeyPair.pub)
      val res = futurePrincipal.map {
        p =>
          assert(p.isInstanceOf[WebIDPrincipal] && p.getName == bertails.toString)
      }
      res.getOrFail()
    }

    val idp = webIdToPrincipal _

    "can Access Alex's profile" in {
      val fusion = Subject(Set(idp(timbl),idp(henry),idp(bertails)))

      val ex = for {
        read <- authz.authorizedPrincipals(fusion, Read, bertailsCard)
        write <- authz.authorizedPrincipals(fusion, Write, bertailsCard)
      } yield {
          read should contain(idp(bertails))
          read should contain(Agent)
          write should contain(idp(bertails))
          write should not contain(idp(henry))
          write should not contain(foaf.Agent)
        }
      ex.getOrFail()
    }


    "What methods does Alex's profile permit Henry to access" in {
      val ex = for {
        read <- authz.authorizedPrincipals(webidToSubject(henry), Read, bertailsCard)
      } yield {
        read should not contain (idp(henry))
        read should contain(Agent)
      }
      ex.getOrFail()

      val ex2 = authz.authorizedPrincipals(webidToSubject(henry), Write, bertailsCard).failed.map{
        case no: NoAuthorization => () //everything ok
        case e => fail("wrong exception: "+e)
      }
      ex2.getOrFail()

    }

    "What methods does Alex's profile acl permit Henry to access" in {
      val ex = for {
        read <- authz.authorizedPrincipals(webidToSubject(bertails), Read, bertailsCard)
        write <- authz.authorizedPrincipals(webidToSubject(bertails), Write, bertailsCard)
      } yield {
        read should contain(idp(bertails))
        read should contain(Agent)
        write should contain(idp(bertails))
        write should not contain(idp(henry))
        write should not contain(foaf.Agent)
      }
      ex.getOrFail()
    }

    "What methods does Alex's profile acl permit TimBL to access" in {
      val ex = for {
        read <- authz.authorizedPrincipals(webidToSubject(timbl), Read, bertailsCard)
      } yield {
        read should not contain(idp(bertails))
        read should contain(Agent)
      }
      ex.getOrFail()

      val ex2 = authz.authorizedPrincipals(webidToSubject(timbl), Write, bertailsCard).failed.map{
        case no: NoAuthorization => () //everything ok
        case e => fail("wrong exception: "+e)
      }
      ex2.getOrFail()
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

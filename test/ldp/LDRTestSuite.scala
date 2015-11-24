package test.ldp

import java.net.{URI => jURI, URL => jURL}
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.binder.RecordBinder
import org.w3.banana.io._
import play.api.libs.iteratee._
import rww.ldp.LDPCommand._
import rww.ldp.WebResource
import rww.ldp.actor.{RWWActorSystem, RWWActorSystemImpl}
import rww.ldp.auth.{WACAuthZ, WebIDVerifier}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

//class PlantainLDRTest
//  extends LDRTestSuite[Rdf](baseUri, dir)(
//    ops,recordBinder,sparqlOps,sparqlGraph,turtleWriter,turtleReader
//  )

/**
 * test the LinkedResource ~ and ~> implementations
 */
abstract
class LDRTestSuite[Rdf<:RDF](
  baseUri: Rdf#URI,
  dir: Path
)(implicit
  val ops: RDFOps[Rdf],
  val recordBinder: RecordBinder[Rdf],
  sparqlOps: SparqlOps[Rdf],
  sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
  turtleWriter: RDFWriter[Rdf,Try, Turtle],
  reader: RDFReader[Rdf, Try, Turtle]
) extends WordSpec with Matchers with BeforeAndAfterAll with TestHelper with TestGraphs[Rdf] {

  import ops._

  implicit val timeout = Timeout(1,TimeUnit.MINUTES)
  val rww: RWWActorSystem[Rdf] = RWWActorSystemImpl.plain[Rdf](baseUri,dir,testFetcher)
  implicit val authz =  new WACAuthZ[Rdf](new WebResource(rww))

  val webidVerifier = new WebIDVerifier(new WebResource(rww))

  val web = new WebResource[Rdf](rww)

  "setup Alex's profile" when {

    "add bertails card and acls" in {
      val script = rww.execute(for {
        ldpc     <- createContainer(baseUri,Some("bertails"),Graph.empty)
        ldpcMeta <- getMeta(ldpc)
        card     <- createLDPR(ldpc,Some(bertailsCard.lastPathSegment),bertailsCardGraph)
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
  }

  "Test WebResource ~" in {
    val ldrEnum = web(tpacGroup)
    val futureResList = ldrEnum(Iteratee.getChunks[LinkedDataResource[Rdf]]).flatMap(_.run)
    val resList = futureResList.getOrFail()
    resList should have length(1)
    val ldr = resList.head
    ldr.location should be(tpacGroupDoc)
    ldr.resource.pointer should be(tpacGroup)
    assert(ldr.resource.graph isIsomorphicWith tpacGroupGraph.resolveAgainst(tpacGroupDoc))
  }

  "Test WebResource ~>" in {
    val memberEnum: Enumerator[LinkedDataResource[Rdf]] = for {
      groupLdr <- web(tpacGroup)
      member <-  web.~>(groupLdr,foaf.member)()
    } yield {
      member
    }
    val memberFuture = memberEnum(Iteratee.getChunks[LinkedDataResource[Rdf]]).flatMap(_.run)
    val memberList = memberFuture.getOrFail()

    val answersMap = Map(memberList.map{ldr => (ldr.resource.pointer,ldr.resource)} : _*)

    val pointers = answersMap.keys.toList
    answersMap should have size (3)

    assert(pointers.contains(henry))
    assert(pointers.contains(bertails))
    assert(pointers.contains(timbl))

    assert(answersMap(bertails).graph isIsomorphicWith((bertailsCardGraph union containsRel).resolveAgainst(bertailsCard)))
    assert(answersMap(henry).graph isIsomorphicWith(henryGraph.resolveAgainst(henryCard)))
    assert(answersMap(timbl).graph isIsomorphicWith(timblGraph.resolveAgainst(timblCard)))

  }

  "Test WebResource ~> followed by ~> to literal" in {
    val nameEnum = for {
      groupLdr <- web(tpacGroup)
      member <-  web.~>(groupLdr,foaf.member)()
      name <- web.~>(member,foaf.name)()
    } yield {
      name
    }
    val nameFuture = nameEnum(Iteratee.getChunks[LinkedDataResource[Rdf]]).flatMap(_.run)
    val namesLDR = nameFuture.getOrFail()

    val answers = namesLDR.map{ldr => ldr.resource.pointer}
    answers should have length (3)
    assert(answers.contains(Literal("Henry")))
    assert(answers.contains("Alexandre".lang("fr")))
    assert(answers.contains(Literal("Tim Berners-Lee")))
  }

  "Test ACLs with ~ and ~> and <~ (tests bnode support too)" in {
    val nameEnum = for {
      wacLdr <- web(henryFoafWac)
      auth  <-  web.<~(LinkedDataResource(wacLdr.location,PointedGraph(henryFoaf,wacLdr.resource.graph)),wac.accessTo)()
      agentClass <-  web.~>(auth,wac.agentClass)()
      member <-  web.~>(agentClass,foaf.member)()
      name <- web.~>(member,foaf.name)()
    } yield {
      name
    }
    val nameFuture = nameEnum(Iteratee.getChunks[LinkedDataResource[Rdf]]).flatMap(_.run)
    val namesLDR = nameFuture.getOrFail()

    val answers = namesLDR.map{ldr => ldr.resource.pointer}
    answers should have length (3)
    assert(answers.contains(Literal("Henry")))
    assert(answers.contains("Alexandre".lang("fr")))
    assert(answers.contains(Literal("Tim Berners-Lee")))
  }

  "Test WebResource ~> with missing remote resource" in {
    //we delete timbl
    val ex = rww.execute(deleteResource(timblCard))
    ex.getOrFail()
    val ex2 = rww.execute(getResource(timblCard))
    val res = ex2.failed.map{_=>true}
    assert{ Await.result(res,Duration(2,TimeUnit.SECONDS)) == true}
    //now we should only have two resources returned
    val memberEnum: Enumerator[LinkedDataResource[Rdf]] = for {
      groupLdr <- web(tpacGroup)
      member <-  web.~>(groupLdr,foaf.member)()
    } yield {
      member
    }
    val memberFuture = memberEnum(Iteratee.getChunks[LinkedDataResource[Rdf]]).flatMap(_.run)
    val memberList = memberFuture.getOrFail()

    val answersMap = Map(memberList.map{ldr => (ldr.resource.pointer,ldr.resource)} : _*)

    val pointers = answersMap.keys.toList
    answersMap should have size (2)

    assert(pointers.contains(henry))
    assert(pointers.contains(bertails))

    assert(answersMap(bertails).graph isIsomorphicWith((bertailsCardGraph union containsRel).resolveAgainst(bertailsCard)))
    assert(answersMap(henry).graph isIsomorphicWith(henryGraph.resolveAgainst(henryCard)))

  }



}

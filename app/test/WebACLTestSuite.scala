/*
 * Copyright 2012 Henry Story, http://bblfish.net/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test

import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers
import org.w3.banana._
import concurrent.{Await, Future}
import org.www.play.auth._
import org.www.readwriteweb.play.LinkedDataCache
import concurrent.duration.Duration
import org.www.play.auth.Subject
import org.w3.banana.LinkedDataResource
import org.www.play.auth.WebIDPrincipal


class WebACLTestSuite[Rdf<:RDF](cache: LinkedDataCache[Rdf])(implicit val diesel: Diesel[Rdf])
    extends WordSpec with MustMatchers with BeforeAndAfterAll with TestHelper {

  import diesel.ops
  import diesel.ops._
  import diesel._

  implicit val wac = WebACL[Rdf]
  val foaf = FOAFPrefix[Rdf]

  def dummyCache(loc: Rdf#URI, g: Rdf#Graph) = new LinkedDataCache[Rdf]{
    def get(uri: Rdf#URI) =
      if (uri==loc) Future(LinkedDataResource(uri, PointedGraph[Rdf](uri,g)))
      else throw new Exception("method should never be called")
  }

  def partialWebCache(loc: Rdf#URI, g: Rdf#Graph) = new LinkedDataCache[Rdf]{
    def get(uri: Rdf#URI) =
      if (uri==loc) Future(LinkedDataResource(uri, PointedGraph[Rdf](uri,g)))
      else cache.get(uri)
  }

  case class DummyWebRequest(subject: Future[Subject],method: Mode, meta: Rdf#URI,uri: Rdf#URI) extends WebRequest[Rdf]

  val henry = new java.net.URI("http://bblfish.net/people/henry/card#me")
  val henrysubj = Subject(List(WebIDPrincipal(henry)))
  val henrysubjAnswer = Subject(List(WebIDPrincipal(henry)),List(WebIDPrincipal(henry)))
  val henryFuture = Future(henrysubj)


  val publicACLForSingleResource: Rdf#Graph = (
    bnode("t1") -- wac.accessTo ->- URI("http://joe.example/pix/img")
       -- wac.agentClass ->- foaf("Agent")
       -- wac.mode ->- wac.Read
    ).graph

  val nonEvaluabableSubject: Future[Subject] = Future {
     sys.error("the resource is public so the subject need not be evaluated")
  }

  val wac1 = WebAccessControl[Rdf](dummyCache(URI("http://joe.example/pix/.meta"),publicACLForSingleResource))


  """Access to a Public resource by an individual
    (see http://www.w3.org/wiki/WebAccessControl#Public_Access)""" when {
//    wac1.authorizations must have size(1)
    val webreq = DummyWebRequest( nonEvaluabableSubject, Read,
        URI("http://joe.example/pix/.meta"),
        URI("http://joe.example/pix/img")
    )
    "read mode" in {
      val fb=wac1.allow(webreq)
      Await.result(fb,Duration("1s")) mustBe Anonymous
    }
    "write mode" in {
      val fb=wac1.allow(webreq.copy(method=Write))
      try {
        Await.result(fb,Duration("1s"))
        fail("Should throw an exception")
      } catch {
        case e: Exception => System.out.println("ok")
      }
    }
    "control mode" in {
      val fb=wac1.allow(webreq.copy(method=Control))
      try {
        Await.result(fb,Duration("1s"))
        fail("Should throw an exception")
      } catch {
        case e: Exception => System.out.println("ok")
      }
    }
  }

  val publicACLForRegexResource: Rdf#Graph = (
    bnode("t1")
      -- wac.accessToClass ->- ( bnode("t2") -- wac.regex ->- "http://joe.example/blog/.*" )
      -- wac.agentClass ->- foaf("Agent")
      -- wac.mode ->- wac.Read
    ).graph

  val wac2 = WebAccessControl[Rdf](dummyCache(URI("http://joe.example/blog/.meta"),publicACLForRegexResource))


  "Access to Public resources defined by a regex" when {
//    wac2.authorizations must have size(1)
    val webreq = DummyWebRequest( nonEvaluabableSubject, Read,
          URI("http://joe.example/blog/.meta"),
          URI("http://joe.example/blog/2012/firstPost")
    )

    "read mode" in {
      val fb=wac2.allow(webreq)
      Await.result(fb,Duration("1s")) mustBe Anonymous

    }
    "write mode" in {
      val fb = wac2.allow(webreq.copy(method=Write))
      try {
        Await.result(fb,Duration("1s"))
        fail("Should throw an exception")
      } catch {
        case e: Exception => System.out.println("ok:"+e)
      }
    }
    "control mode" in {
      val fb = wac2.allow(webreq.copy(method=Control))
      try {
        Await.result(fb,Duration("1s"))
        fail("Should throw an exception")
      } catch {
        case e: Exception => System.out.println("ok:"+e)
      }
    }
  }

  val groupACLForRegexResource: Rdf#Graph = (
    bnode("t1")
      -- wac.accessToClass ->- ( bnode("t2") -- wac.regex ->- "http://bblfish.net/blog/editing/.*" )
      -- wac.agentClass ->- ( URI("http://bblfish.net/blog/editing/.meta#a1") -- foaf("member") ->- URI(henry.toString) )
      -- wac.mode ->- wac.Read
      -- wac.mode ->- wac.Write
    ).graph

  val wac3 = WebAccessControl[Rdf](dummyCache(URI("http://bblfish.net/blog/editing/.meta"),groupACLForRegexResource))


  "Access to group (named with a local uri) protected resources described by a regex" when {
//    wac3.authorizations must have size(1)
    val webreq = DummyWebRequest( henryFuture, Read,
      URI("http://bblfish.net/blog/editing/.meta"),
      URI("http://bblfish.net/blog/editing/post1")
    )

    "read mode" in {
      val fb=wac3.allow(webreq)
      Await.result(fb,Duration("1s")) mustBe henrysubjAnswer
    }
    "write mode" in {
      val fb=wac3.allow(webreq.copy(method=Write))
      Await.result(fb,Duration("1s")) mustBe henrysubjAnswer
    }
    "control mode" in {
      val fb=wac3.allow(webreq.copy(method=Control))
      try {
        Await.result(fb,Duration("1s"))
        fail("Should throw an exception")
      } catch {
        case e: Exception => System.out.println("ok:"+e)
      }
    }

  }

  val remoteGroupACLForRegexResource: Rdf#Graph = (
    bnode("t1")
      -- wac.accessToClass ->- ( bnode("t2") -- wac.regex ->- "http://bblfish.net/blog/editing/.*" )
      -- wac.agentClass ->- URI("http://www.w3.org/2005/Incubator/webid/team#we")
      -- wac.mode ->- wac.Read
      -- wac.mode ->- wac.Write
    ).graph

  val wac4 = WebAccessControl[Rdf](partialWebCache(URI("http://bblfish.net/blog/editing/.meta"),remoteGroupACLForRegexResource))


  "Access to protected resources described by a regex to a group described remotely" when {
//    wac4.authorizations must have size(1)
    val webreq = DummyWebRequest( henryFuture, Read,
      URI("http://bblfish.net/blog/editing/.meta"),
      URI("http://bblfish.net/blog/editing/post1")
    )

    "read mode" in {
      val fb=wac4.allow(webreq)
      Await.result(fb,Duration("15s")) mustBe henrysubjAnswer
    }
    "write mode" in {
      val fb=wac4.allow(webreq.copy(method=Write))
      Await.result(fb,Duration("15s")) mustBe henrysubjAnswer
    }
    "control mode" in {
      val fb=wac4.allow(webreq.copy(method=Control))
      try {
        Await.result(fb,Duration("15s"))
        fail("Should throw an exception")
      } catch {
        case e: Exception => System.out.println("ok:"+e)
      }
    }

  }


}

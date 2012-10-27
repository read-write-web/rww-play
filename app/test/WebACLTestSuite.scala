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
import org.www.readwriteweb.play.auth._
import concurrent.{Await, Future}
import org.www.play.auth.WebIDPrincipal
import scala.util.Success
import scala.Some
import org.www.readwriteweb.play.auth.Subject
import org.www.readwriteweb.play.auth.WebAccessControl
import java.security.Principal
import concurrent.util.Duration
import org.www.readwriteweb.play.LinkedDataCache


class WebACLTestSuite[Rdf<:RDF](cache: LinkedDataCache[Rdf])(implicit val diesel: Diesel[Rdf])
    extends WordSpec with MustMatchers with BeforeAndAfterAll with TestHelper {
  import diesel._
  import diesel.ops._


  val wac = WebACL[Rdf]

  val dummyCache = new LinkedDataCache[Rdf]{
    def get(uri: Rdf#URI) = throw new Exception("method should never be called")
  }

  val henry = new java.net.URI("http://bblfish.net/people/henry/card#me")
  val henryFinder = new SubjectFinder {
    def subject = Future(
      Subject(List(scalaz.Success[BananaException,Principal](WebIDPrincipal(henry))))
    )
  }

  val publicACLForSingleResource: Rdf#Graph = (
    bnode("t1") -- wac.accessTo ->- URI("http://joe.example/pix/img")
       -- wac.agentClass ->- foaf("Agent")
       -- wac.mode ->- wac.Read
    ).graph

  val nonEvaluabableSubject = new SubjectFinder {
    def subject = sys.error("the resource is public so the subject need not be evaluated")
  }

  val wac1 = WebAccessControl[Rdf](
    LinkedDataResource(URI("http://joe.example/pix/.meta"),
      PointedGraph(bnode("t1"),publicACLForSingleResource)),
      dummyCache)

  """Access to a Public resource by an individual
    (see http://www.w3.org/wiki/WebAccessControl#Public_Access)""" when {
    wac1.authorizations must have size(1)
    "read mode" in {
      val fb=wac1.hasAccessTo(nonEvaluabableSubject, wac1.Read, URI("http://joe.example/pix/img"))
      fb.value mustBe Some(Success(true))
    }
    "write mode" in {
      val fb=wac1.hasAccessTo(nonEvaluabableSubject, wac1.Write, URI("http://joe.example/pix/img"))
      fb.value mustBe Some(Success(false))

    }
    "control mode" in {
      val fb=wac1.hasAccessTo(nonEvaluabableSubject, wac1.Control, URI("http://joe.example/pix/img"))
      fb.value mustBe Some(Success(false))
    }
  }

  val publicACLForRegexResource: Rdf#Graph = (
    bnode("t1")
      -- wac.accessToClass ->- ( bnode("t2") -- wac.regex ->- "http://joe.example/blog/.*" )
      -- wac.agentClass ->- foaf("Agent")
      -- wac.mode ->- wac.Read
    ).graph

  val wac2 = WebAccessControl[Rdf](
    LinkedDataResource(URI("http://joe.example/blog/.meta"),
      PointedGraph(bnode("t1"),publicACLForRegexResource)),
    dummyCache)

  "Access to Public resources defined by a regex" when {
    wac2.authorizations must have size(1)
    "read mode" in {
      val fb=wac2.hasAccessTo(nonEvaluabableSubject, wac2.Read, URI("http://joe.example/blog/2012/firstPost"))
      fb.value mustBe Some(Success(true))

    }
    "write mode" in {
      val fb = wac2.hasAccessTo(nonEvaluabableSubject, wac2.Write, URI("http://joe.example/blog/2012/firstPost"))
      fb.value mustBe Some(Success(false))
    }
    "control mode" in {
      val fb = wac2.hasAccessTo(nonEvaluabableSubject, wac2.Control, URI("http://joe.example/blog/2012/firstPost"))
      fb.value mustBe Some(Success(false))
    }
  }

  val groupACLForRegexResource: Rdf#Graph = (
    bnode("t1")
      -- wac.accessToClass ->- ( bnode("t2") -- wac.regex ->- "http://bblfish.net/blog/editing/.*" )
      -- wac.agentClass ->- ( URI("http://bblfish.net/blog/editing/.meta#a1") -- foaf("member") ->- URI(henry.toString) )
      -- wac.mode ->- wac.Read
      -- wac.mode ->- wac.Write
    ).graph

  val wac3 = WebAccessControl[Rdf](
    LinkedDataResource(URI("http://bblfish.net/blog/editing/.meta"),
      PointedGraph(bnode("t1"),groupACLForRegexResource)),
    dummyCache)

  "Access to group (named with a local uri) protected resources described by a regex" when {
    wac3.authorizations must have size(1)

    "read mode" in {
      val fb=wac3.hasAccessTo(henryFinder, wac3.Read, URI("http://bblfish.net/blog/editing/post1"))
      assert(Await.result(fb,Duration("1s")))
    }
    "write mode" in {
      val fb=wac3.hasAccessTo(henryFinder, wac3.Write, URI("http://bblfish.net/blog/editing/post1"))
      assert(Await.result(fb,Duration("1s")))
    }
    "control mode" in {
      val fb=wac3.hasAccessTo(henryFinder, wac3.Control, URI("http://bblfish.net/blog/editing/post1"))
      assert(Await.result(fb,Duration("1s")) == false)
    }

  }

  val remoteGroupACLForRegexResource: Rdf#Graph = (
    bnode("t1")
      -- wac.accessToClass ->- ( bnode("t2") -- wac.regex ->- "http://bblfish.net/blog/editing/.*" )
      -- wac.agentClass ->- URI("http://www.w3.org/2005/Incubator/webid/team#we")
      -- wac.mode ->- wac.Read
      -- wac.mode ->- wac.Write
    ).graph

   val wac4 = WebAccessControl[Rdf](
    LinkedDataResource(URI("http://bblfish.net/blog/editing/.meta"),
      PointedGraph(bnode("t1"),remoteGroupACLForRegexResource)),
    cache)

  "Access to protected resources described by a regex to a group described remotely" when {
    wac4.authorizations must have size(1)

    "read mode" in {
      val fb=wac4.hasAccessTo(henryFinder, wac4.Read, URI("http://bblfish.net/blog/editing/post1"))
      assert(Await.result(fb,Duration("15s")))
    }
    "write mode" in {
      val fb=wac4.hasAccessTo(henryFinder, wac4.Write, URI("http://bblfish.net/blog/editing/post1"))
      assert(Await.result(fb,Duration("15s")))
    }
    "control mode" in {
      val fb=wac4.hasAccessTo(henryFinder, wac4.Control, URI("http://bblfish.net/blog/editing/post1"))
      assert(Await.result(fb,Duration("15s")) == false)
    }

  }


}

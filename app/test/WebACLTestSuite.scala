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
import org.w3.banana.{READ, RDFOps, Diesel, RDF}
import org.w3.readwriteweb.play.auth.{SubjectFinder, Anonymous, WebAccessControl, WebACL}
import scala.Some


class WebACLTestSuite[Rdf<:RDF](implicit  ops: RDFOps[Rdf], diesel: Diesel[Rdf])
    extends WordSpec with MustMatchers with BeforeAndAfterAll with TestHelper {

  import diesel._
  import ops._

  val wac = WebACL[Rdf]

  val publicACLForSingleResource: Rdf#Graph = (
    bnode("t1") -- wac.accessTo ->- URI("http://joe.example/pix/img")
       -- wac.agentClass ->- foaf("Agent")
    ).graph


  object nonEvaluabableSubject extends SubjectFinder {
    def subject = sys.error("the resource is public so the subject need not be evaluated")
  }

  val wac1 = WebAccessControl[Rdf](publicACLForSingleResource)

  "Simple Public Access. see http://www.w3.org/wiki/WebAccessControl#Public_Access" in {
    wac1.authorizations must have size(1)
    wac1.hasAccessTo(nonEvaluabableSubject, wac1.Read, URI("http://joe.example/pix/img"))  must be(true)
  }

}

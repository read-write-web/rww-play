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

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.ws.{WSTrait, WS}
import javax.net.ssl.KeyManager
import scala.Array
import play.core.server.noCATrustManager
import com.ning.http.client.AsyncHttpClient

/**
* add your integration spec here.
* An integration test will fire up a whole play application in a real (or headless) browser
*/
class IntegrationSpec extends Specification {

  "Access controled resources /test/webid/*" should {
//
//    "work from within a browser" in {
//      running(TestServer(3333), HTMLUNIT) { browser =>
//
//        browser.goTo("http://localhost:3333/")
//
//        browser.pageSource must contain("Your new application is ready.")
//
//      }
//  }
    // one has to send the FakeApplication too. See bug report
    // http://play.lighthouseapp.com/projects/82401/tickets/860-21-rc1-playapitestwithserver-fails-when-port-is-given
    // also need to put a test on all subtests annoyingly see:
    // https://groups.google.com/d/topic/play-framework/YTgOMVt0Fqk/discussion
    "when running locally" in  {

      "allRead should be readable by anyone" in new WithServer(app=FakeApplication(),port=19001,sslPort=Some(19002)) {
         val url = "https://localhost:" + sslPort.get +"/test/webid/allRead"
         val h = await(AnonymousTestWS.url(url).get)
         h.status must equalTo(OK)
      }

      "webIdRead should be readable only by an agent with a valid WebID" in new WithServer(app=FakeApplication(),port=19001,sslPort=Some(19002)) {
        val url = "https://localhost:" + sslPort.get +"/test/webid/webIdRead"
        val h = await(AnonymousTestWS.url(url).get)
        h.status must equalTo(UNAUTHORIZED)
      }


    }

  }

}

class TestWS(km: KeyManager=null) extends WSTrait {

  val  trustAllservers = {
    val sslctxt = javax.net.ssl.SSLContext.getInstance("TLS");
    sslctxt.init(null, Array(noCATrustManager),null);
    sslctxt
  }

  val client = {
   val builder = WS.asyncBuilder.setSSLContext(trustAllservers)
    new AsyncHttpClient(builder.build)
  }
}

object AnonymousTestWS extends TestWS
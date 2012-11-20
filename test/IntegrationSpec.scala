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
import play.api.libs.ws.{WSTrait, WSx, WS}
import javax.net.ssl.{SSLEngine, X509ExtendedKeyManager, KeyManager}
import scala.Array
import play.core.server.noCATrustManager
import collection.mutable
import java.security.cert.X509Certificate
import java.security.{Principal, PrivateKey}
import java.net.Socket
import com.ning.http.client.AsyncHttpClient

/**
* add your integration spec here.
* An integration test will fire up a whole play application in a real (or headless) browser
*/
class IntegrationSpec extends Specification {

  "Application" should {
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
    "run in a server" in new WithServer(app=FakeApplication(),port=19001,sslPort=Some(19002)) {
      val url = "https://localhost:" + sslPort.get +"/test/webid/allRead"
      System.out.println("body of get("+url+")=")
      val h = await(TestWS.url(url).get)
      System.out.println(h.body)
      h.status must equalTo(OK)
    }

  }

}

object TestWS extends WSTrait {

  val  trustAllservers = {
    val sslctxt = javax.net.ssl.SSLContext.getInstance("TLS");
    sslctxt.init(null, Array(noCATrustManager),null);
    sslctxt
  }

  val client = new AsyncHttpClient(WS.asyncBuilder.setSSLContext(trustAllservers).build )
}
package test

import org.scalatest.{Matchers, WordSpec}
import rww.ldp.RWWebActorSubdomains
import java.net.URI
import scala.Some

/**
 * Created by hjs on 23/11/2013.
 */
class RWWActorSubdomainsTest extends WordSpec with Matchers {
  import RWWebActorSubdomains._

  val localBase = new URI("https://localhost:8443/2013/")

  s"base=$localBase tests " in {
    local(new URI("/2013/"), localBase )  should be (Some(Switch(nosubdomain,"")))
    local(new URI("/2013/test"), localBase )  should be (Some(Switch(nosubdomain,"test")))
    local(new URI("/2013/test/"), localBase )  should be (Some(Switch(nosubdomain,"test")))
    local(new URI("/2013/test/img"), localBase )  should be (Some(Switch(nosubdomain,"test/img")))
    local(new URI("/2013/test/img/"), localBase )  should be (Some(Switch(nosubdomain,"test/img")))
    local(new URI("/2013/test/img/cat.jpg"), localBase )  should be (Some(Switch(nosubdomain,"test/img/cat")))
    local(new URI("/2013/test/img/.acl.ttl"), localBase )  should be (Some(Switch(nosubdomain,"test/img")))
    local(new URI("/2013/card.acl"), localBase )  should be (Some(Switch(nosubdomain,"card")))
  }

  s"base=$localBase tests with subdomains" in {
    local(new URI("https://joe.localhost:8443/2013/"),localBase) should be (Some(Switch("joe","2013")))
    local(new URI("https://james.localhost:8443/2013"),localBase) should be (Some(Switch("james","2013")))
    local(new URI("https://james.localhost:8443"),localBase) should be (Some(Switch("james","")))

  }
}

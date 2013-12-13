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

  s"base=$localBase tests relative uris" in {
    local(new URI("/2013/"), localBase )  should be (Some(SubdomainSwitch(None,"")))
    local(new URI("/2013/.acl"), localBase )  should be (Some(SubdomainSwitch(None,"")))
    local(new URI("/2013/test"), localBase )  should be (Some(SubdomainSwitch(None,"test")))
    local(new URI("/2013/test/"), localBase )  should be (Some(SubdomainSwitch(None,"test")))
    local(new URI("/2013/test/img"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new URI("/2013/test/img/"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new URI("/2013/test/img/cat.jpg"), localBase )  should be (Some(SubdomainSwitch(None,"test/img/cat")))
    local(new URI("/2013/test/img/.acl.ttl"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new URI("/2013/card.acl"), localBase )  should be (Some(SubdomainSwitch(None,"card")))
  }

  s"base=$localBase tests full uris on localhost" in {
    local(new URI("https://localhost:8443/2013/card.acl"), localBase) should be (Some(SubdomainSwitch(None,"card")))
    local(new URI("https://localhost:8443/2013/.acl"), localBase) should be (Some(SubdomainSwitch(None,"")))
    local(new URI("https://localhost:8443/2013/test/img"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new URI("https://localhost:8443/2013/test/img/"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new URI("https://localhost:8443/2013/test/img/cat.jpg"), localBase )  should be (Some(SubdomainSwitch(None,"test/img/cat")))
    local(new URI("https://localhost:8443/2013/test/img/.acl.ttl"), localBase )  should be (Some(SubdomainSwitch(None,"test/img")))
    local(new URI("https://localhost:8443/2013/card.acl"), localBase )  should be (Some(SubdomainSwitch(None,"card")))

  }

  s"base=$localBase tests with subdomains on localhost" in {
    local(new URI("https://joe.localhost:8443/2013/"),localBase) should be (Some(SubdomainSwitch(Some("joe"),"2013")))
    local(new URI("https://james.localhost:8443/2013"),localBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new URI("https://james.localhost:8443"),localBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new URI("https://james.localhost:8443/.acl"),localBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new URI("https://james.localhost:8443/2013/.acl"),localBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new URI("https://slim.localhost:8443/"),localBase) should be (Some(SubdomainSwitch(Some("slim"),"")))
  }

  val stampleBase = new URI("https://stample.io:443/2013/")

  s"base=$stampleBase tests with subdomains on stample" in {
    local(new URI("https://stample.io/2013/"),stampleBase) should be (Some(SubdomainSwitch(None,"")))
    local(new URI("https://joe.stample.io/2013/"),stampleBase) should be (Some(SubdomainSwitch(Some("joe"),"2013")))
    local(new URI("https://james.stample.io/2013"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new URI("https://james.stample.io"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new URI("https://james.stample.io/.acl"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new URI("https://james.stample.io/2013/.acl"),stampleBase) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new URI("https://slim.stample.io/"),stampleBase) should be (Some(SubdomainSwitch(Some("slim"),"")))
  }

  val stampleBase2 = new URI("https://stample.io/2013/")

  s"base=$stampleBase2 tests with subdomains on stample inversing ports" in {
    local(new URI("https://stample.io:443/2013/"),stampleBase2) should be (Some(SubdomainSwitch(None,"")))
    local(new URI("https://joe.stample.io:443/2013/"),stampleBase2) should be (Some(SubdomainSwitch(Some("joe"),"2013")))
    local(new URI("https://james.stample.io:443/2013"),stampleBase2) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new URI("https://james.stample.io:443"),stampleBase2) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new URI("https://james.stample.io:443/.acl"),stampleBase2) should be (Some(SubdomainSwitch(Some("james"),"")))
    local(new URI("https://james.stample.io:443/2013/.acl"),stampleBase2) should be (Some(SubdomainSwitch(Some("james"),"2013")))
    local(new URI("https://slim.stample.io:443/"),stampleBase2) should be (Some(SubdomainSwitch(Some("slim"),"")))
  }


}

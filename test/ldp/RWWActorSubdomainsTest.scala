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
    local(new URI("/2013/"), localBase )  should be (Some(Switch(None,"")))
    local(new URI("/2013/.acl"), localBase )  should be (Some(Switch(None,"")))
    local(new URI("/2013/test"), localBase )  should be (Some(Switch(None,"test")))
    local(new URI("/2013/test/"), localBase )  should be (Some(Switch(None,"test")))
    local(new URI("/2013/test/img"), localBase )  should be (Some(Switch(None,"test/img")))
    local(new URI("/2013/test/img/"), localBase )  should be (Some(Switch(None,"test/img")))
    local(new URI("/2013/test/img/cat.jpg"), localBase )  should be (Some(Switch(None,"test/img/cat")))
    local(new URI("/2013/test/img/.acl.ttl"), localBase )  should be (Some(Switch(None,"test/img")))
    local(new URI("/2013/card.acl"), localBase )  should be (Some(Switch(None,"card")))
  }

  s"base=$localBase tests full uris" in {
    local(new URI("https://localhost:8443/2013/card.acl"), localBase) should be (Some(Switch(None,"card")))
    local(new URI("https://localhost:8443/2013/.acl"), localBase) should be (Some(Switch(None,"")))
    local(new URI("https://localhost:8443/2013/test/img"), localBase )  should be (Some(Switch(None,"test/img")))
    local(new URI("https://localhost:8443/2013/test/img/"), localBase )  should be (Some(Switch(None,"test/img")))
    local(new URI("https://localhost:8443/2013/test/img/cat.jpg"), localBase )  should be (Some(Switch(None,"test/img/cat")))
    local(new URI("https://localhost:8443/2013/test/img/.acl.ttl"), localBase )  should be (Some(Switch(None,"test/img")))
    local(new URI("https://localhost:8443/2013/card.acl"), localBase )  should be (Some(Switch(None,"card")))

  }

  s"base=$localBase tests with subdomains" in {
    local(new URI("https://joe.localhost:8443/2013/"),localBase) should be (Some(Switch(Some("joe"),"2013")))
    local(new URI("https://james.localhost:8443/2013"),localBase) should be (Some(Switch(Some("james"),"2013")))
    local(new URI("https://james.localhost:8443"),localBase) should be (Some(Switch(Some("james"),"")))
    local(new URI("https://james.localhost:8443/.acl"),localBase) should be (Some(Switch(Some("james"),"")))
    local(new URI("https://james.localhost:8443/2013/.acl"),localBase) should be (Some(Switch(Some("james"),"2013")))
  }
}

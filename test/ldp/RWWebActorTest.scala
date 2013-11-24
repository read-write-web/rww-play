package test.ldp

import org.scalatest.WordSpec
import org.scalatest.Matchers
import rww.ldp.RActor
import java.net.{URI, URL}

/**
 * Created by hjs on 12/11/2013.
 */
class RWWebActorTest extends WordSpec with Matchers {
  import rww.ldp.RWWebActor._

  val localBase = new URI("https://localhost:8443/2013/")

  s"base=$localBase tests " in {
     local(new URI("/2013/"), localBase )  should be (Some(""))
     local(new URI("/2013/test"), localBase )  should be (Some("test"))
     local(new URI("/2013/test/"), localBase )  should be (Some("test"))
     local(new URI("/2013/test/img"), localBase )  should be (Some("test/img"))
     local(new URI("/2013/test/img/"), localBase )  should be (Some("test/img"))
     local(new URI("/2013/test/img/cat.jpg"), localBase )  should be (Some("test/img/cat"))
     local(new URI("/2013/test/img/.acl.ttl"), localBase )  should be (Some("test/img"))
     local(new URI("/2013/card.acl"), localBase )  should be (Some("card"))
  }

}

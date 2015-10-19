package ldp.auth

import java.net.{URI => jURI, URL}
import java.nio.file.{Files, Path}
import java.security.Principal
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.io.{RDFReader, RDFWriter, Turtle}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeHeaders, FakeRequest}
import rww.auth.SigInfo._
import rww.auth.{HttpAuthorization, SigInfo}
import rww.ldp.LDPCommand._
import rww.ldp.LDPExceptions.{FetchException, KeyIdException, SignatureRequestException, SignatureVerificationException}
import rww.ldp.actor
import rww.ldp.actor.RWWActorSystem
import rww.ldp.auth.{WebKeyPrincipal, WebKeyVerifier}
import rww.play.auth.Subject
import test.ldp.TestSetup._
import test.ldp.{TestGraphs, TestHelper}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class RdfHttpSignaturesTest
   extends HttpSignaturesTest[Rdf](baseUri)(
     ops,recordBinder,sparqlOps,sparqlGraph,turtleWriter,turtleReader
   )

/**
  * Created by hjs on 16/10/2015.
  */
class HttpSignaturesTest[Rdf<:RDF](
  baseUri: Rdf#URI
)(implicit
  val ops: RDFOps[Rdf],
  val recordBinder: binder.RecordBinder[Rdf],
  sparqlOps: SparqlOps[Rdf],
  sparqlGraph: SparqlEngine[Rdf, Try, Rdf#Graph] with SparqlUpdate[Rdf, Try, Rdf#Graph],
  turtleWriter: RDFWriter[Rdf, Try, Turtle],
  reader: RDFReader[Rdf, Try, Turtle]
) extends WordSpec with Matchers with BeforeAndAfterAll
   with TestHelper with TestGraphs[Rdf]  {

  import ops._
  import rww.auth.HttpAuthorization.futureToFutureTry

  val dir: Path = Files.createTempDirectory("HttpSignaturesTest")

  implicit val timeout: Timeout = Timeout(1, TimeUnit.MINUTES)
  val rwwAgent: RWWActorSystem[Rdf] = actor.RWWActorSystemImpl.plain[Rdf](baseUri, dir, testFetcher)

  implicit val keybinder = certbinder.rsaPubKeybinderWithHash

  val webKeyVerifier = WebKeyVerifier[Rdf](rwwAgent)

  val alexKeyGr = bertKeyPair.pub.toPointedGraph

  implicit def uriToURL(uri: Rdf#URI): URL = new URL(uri.toString)

  "publish key" when {

    val keyDocScript = rwwAgent.execute(for {
      ldpc <- createContainer(baseUri, Some("keys"), Graph.empty)
      bertKeyDoc <- createLDPR(ldpc, Some("key1"), alexKeyGr.graph)
    } yield {
      bertKeyDoc
    })
    val keyDocURI = keyDocScript.getOrFail()
    val keyUri = alexKeyGr.pointer.fold(
      uri => uri.fragment match {
        case Some(frag) => keyDocURI.resolve(uri)
        case None => fail("Alex's key should be named with an absolute URL with a fragment. Was "
          + uri)
      },
      bn => fail("Alex's key should be named with a #uri not be a bnode with value " + bn),
      lit => fail("Alex's key should certainly not be a literal")
    )

    val header =
      """(request-target): get /example
        |host: example.com
        |date: Thu, 05 Jan 2015 21:31:40 GMT""".stripMargin


    val signature = SigInfo.sign(header, bertKeyPair.priv, "SHA256withRSA").get

    "key with good sig" should {
      val siginfo = SigInfo(header, keyUri, "SHA256withRSA", signature)
      val future = webKeyVerifier.verify(siginfo)
      val principal: Principal = future.getOrFail()
      principal should be(WebKeyPrincipal(keyUri.toURI))
    }

    "key with good sig but non existent key doc" should {
      val siginfo = new SigInfo(header, keyUri / "doesnotexist#key", "SHA256withRSA", signature)
      val future = futureToFutureTry(webKeyVerifier.verify(siginfo))

      future.getOrFail() match {
        case Success(x) => fail("should have failed")
        case Failure(FetchException(msg, si)) => () //everything is ok
        case Failure(NonFatal(e)) => fail("cought the wrong exception: cought " + e)
      }
    }

    "key with good sig in correct doc but wrong id" should {
      val nonDefinedUri = keyUri.withFragment("nokey")
      val siginfo = SigInfo(header, nonDefinedUri, "SHA256withRSA", signature)
      val future = futureToFutureTry(webKeyVerifier.verify(siginfo))

      future.getOrFail() match {
        case Success(x) => fail("should have failed")
        case Failure(KeyIdException(msg, si, pg)) => {
          pg.pointer should be(nonDefinedUri)
          si should be(siginfo)
        }
        case Failure(NonFatal(e)) => fail("cought the wrong exception: cought " + e)
      }
    }

    "fail to verify key with bad sig" should {
      val siginfo = SigInfo(header + "x", keyUri, "SHA256withRSA", signature)
      val future = futureToFutureTry(webKeyVerifier.verify(siginfo))

      future.getOrFail() match {
        case Success(x) => fail("should have failed")
        case Failure(SignatureVerificationException(msg, si)) => () //everything is ok
        case Failure(NonFatal(e)) => fail("cought the wrong exception: cought " + e)
      }

    }

    val httpAuth = new HttpAuthorization(webKeyVerifier, new URL(baseUri.toString))

    val specHeaderTxt =
      """POST /foo?param=value&pet=dog HTTP/1.1
        |Host: example.com
        |Date: Thu, 05 Jan 2014 21:31:40 GMT
        |Content-Type: application/json
        |Digest: SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
        |Content-Length: 18""".stripMargin

    val specHeaders = Seq(
      "Host" -> Seq("example.com"),
      "Date" -> Seq("Thu, 05 Jan 2014 21:31:40 GMT"),
      "Content-Type" -> Seq("application/json","text/turtle"),
      "Digest" -> Seq("SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE="),
      "Content-Length"->Seq("18")
    )


    "with default headers" should {
      val toSignHeader = "date: Thu, 05 Jan 2014 21:31:40 GMT"
      val sig = SigInfo.sign(toSignHeader, bertKeyPair.priv, "SHA256withRSA")
        .map(base64encoder.encodeToString(_)).get

      "succeed with good sig" in {
        val sigHeader =
          s"""Signature keyId="${keyUri.getString}",
              |algorithm="rsa-sha256",
              |signature="$sig"
              |""".stripMargin.replaceAll("\n", "")
        val hdrs = FakeHeaders(specHeaders ++ Seq("Authorization" -> Seq(sigHeader)))
        val specReq = FakeRequest("POST","/foo?param=value&pet=dog", hdrs, AnyContentAsEmpty)
        httpAuth(specReq).getOrFail() should be(
          Subject(List(WebKeyPrincipal(new jURI(keyUri.getString))))
        )

      }

      "fail with bad sig" in {
        val sigHeader =
          s"""Signature keyId="${keyUri.getString}",
              |algorithm="rsa-sha256",
              |signature="${sig}a"
              |""".stripMargin.replaceAll("\n", "")
        val hdrs = FakeHeaders(specHeaders ++ Seq("Authorization" -> Seq(sigHeader)))
        val specReq = FakeRequest("POST","/foo?param=value&pet=dog", hdrs, AnyContentAsEmpty)
        val x= httpAuth(specReq).getOrFail()
        x.principals should be (List())
        x.failures.head shouldBe a [SignatureRequestException]
      }

    }

    "with basic headers" should {
      //      "(request-target) host date digest content-length"
      val toSignHeader =
        """(request-target): post /foo?param=value&pet=dog
          |host: example.com
          |date: Thu, 05 Jan 2014 21:31:40 GMT""".stripMargin

      val sig = SigInfo.sign(toSignHeader, bertKeyPair.priv, "SHA256withRSA")
         .map(base64encoder.encodeToString(_)).get

      "succeed with good sig" in {
        val sigHeader =
          s"""Signature keyId="${keyUri.getString}",
              |headers="(request-target) host date",
              |algorithm="rsa-sha256",
              |signature="$sig"
              |""".stripMargin.replaceAll("\n", "")
            .replaceAll("\n", "")
        val hdrs = FakeHeaders(specHeaders ++ Seq(
          "Authorization" -> Seq(sigHeader)))
        val specReq = FakeRequest("POST",
          "/foo?param=value&pet=dog", hdrs, AnyContentAsEmpty)
        httpAuth(specReq).getOrFail() should be(
          Subject(List(
            WebKeyPrincipal(new jURI(keyUri.getString))))
        )

      }
    }


    "with all headers" should {
      val toSignHeader =
        """(request-target): post /foo?param=value&pet=dog
          |host: example.com
          |date: Thu, 05 Jan 2014 21:31:40 GMT
          |content-type: application/json,text/turtle
          |digest: SHA-256=X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=
          |content-length: 18""".stripMargin


      val sig = SigInfo.sign(toSignHeader, bertKeyPair.priv, "SHA256withRSA")
        .map(base64encoder.encodeToString(_)).get

      "succeed with good sig" in {
        val sigHeader =
          s"""Signature keyId="${keyUri.getString}",
              |headers="(request-target) host date content-type digest content-length",
              |algorithm="rsa-sha256",
              |signature="$sig"
              |""".stripMargin.replaceAll("\n", "")
        val hdrs = FakeHeaders(specHeaders ++ Seq("Authorization" -> Seq(sigHeader)))
        val specReq = FakeRequest("POST","/foo?param=value&pet=dog", hdrs, AnyContentAsEmpty)
        httpAuth(specReq).getOrFail() should be(
          Subject(List(WebKeyPrincipal(new jURI(keyUri.getString))))
        )

      }
    }

  }
}

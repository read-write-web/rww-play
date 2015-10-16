package ldp.auth

import java.net.URL
import java.nio.file.{Files, Path}
import java.security.{Principal, Signature}
import java.util.Base64
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.w3.banana._
import org.w3.banana.io.{RDFReader, RDFWriter, Turtle}
import rww.auth.SigInfo
import rww.ldp.LDPCommand._
import rww.ldp.LDPExceptions.{KeyIdException, FetchException, SignatureVerificationException}
import rww.ldp.actor
import rww.ldp.actor.RWWActorSystem
import rww.ldp.auth.{WebKeyPrincipal, WebKeyVerifier}
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
) extends WordSpec with Matchers with BeforeAndAfterAll with TestHelper with TestGraphs[Rdf] {

  import ops._

  val base64encoder = Base64.getEncoder

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

    bertKeyPair.priv
    val sig = Signature.getInstance("SHA256withRSA")
    sig.initSign(bertKeyPair.priv)
    sig.update(header.getBytes("US-ASCII"))
    val signature = sig.sign()

    "verify key with good sig" in {
      val siginfo = SigInfo(header, keyUri, "SHA256withRSA", signature)
      val future = webKeyVerifier.verify(siginfo)
      val principal: Principal = future.getOrFail()
      principal should be(WebKeyPrincipal(keyUri.toURI))
    }

    "verify key with good sig but non existent key doc" in {
      val siginfo = new SigInfo(header, keyUri/"doesnotexist#key", "SHA256withRSA", signature)
      val future = futureToFutureTry(webKeyVerifier.verify(siginfo))

      future.getOrFail() match {
        case Success(x) => fail("should have failed")
        case Failure(FetchException(msg, si)) => () //everything is ok
        case Failure(NonFatal(e)) => fail("cought the wrong exception: cought " + e)
      }
    }

    "verify key with good sig in correct doc but wrong id" in {
      val nonDefinedUri = keyUri.withFragment("nokey")
      val siginfo = SigInfo(header, nonDefinedUri, "SHA256withRSA", signature)
      val future = futureToFutureTry(webKeyVerifier.verify(siginfo))

      future.getOrFail() match {
        case Success(x) => fail("should have failed")
        case Failure(KeyIdException(msg, si,pg)) => {
          pg.pointer should be(nonDefinedUri)
          si should be(siginfo)
        }
        case Failure(NonFatal(e)) => fail("cought the wrong exception: cought " + e)
      }
    }

    "fail to verify key with bad sig" in {
      val siginfo = SigInfo(header + "x", keyUri, "SHA256withRSA", signature)
      val future = futureToFutureTry(webKeyVerifier.verify(siginfo))

      future.getOrFail() match {
        case Success(x) => fail("should have failed")
        case Failure(SignatureVerificationException(msg, si)) => () //everything is ok
        case Failure(NonFatal(e)) => fail("cought the wrong exception: cought " + e)
      }

    }
  }
}

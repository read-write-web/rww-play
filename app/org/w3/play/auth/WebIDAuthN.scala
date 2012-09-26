package org.w3.play.auth

import java.security.cert.X509Certificate
import scalaz._
import Scalaz._
import java.security.{Principal, PublicKey}
import java.net.{MalformedURLException, URL, URISyntaxException}
import org.w3.play.remote.{JenaGraphFetcher, FetchException, GraphFetcher}
import org.w3.banana._
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import jena._
import play.api.libs.concurrent.Promise
import scalaz.Semigroup._
import scalaz.Success
import org.w3.play.remote.GraphNHeaders
import org.w3.banana.FailedConversion


object WebIDAuthN {

  /*
  * Useful when converting the bytes from a BigInteger to a hex for inclusion in rdf
  **/
  def hex(bytes: Array[Byte]): String = bytes.dropWhile(_ == 0).map("%02X" format _).mkString

  def stripSpace(hex: String): String = hex.filter(c => !Character.isWhitespace(c))

}


/**
 *
 */
class WebIDAuthN[Rdf <: RDF](implicit ops: RDFOps[Rdf],
                 sparqlOps: SparqlOps[Rdf],
                 graphQuery: Rdf#Graph => SparqlEngine[Rdf,Id],
                 fetcher: GraphFetcher[Rdf])   {

  import sparqlOps._
  import ops._
  val dsl = Diesel[Rdf]
  import dsl._
  import WebIDAuthN._

//  val webidVerifier = {
//    val wiv = context.actorFor("webidVerifier")
//    if (wiv == context.actorFor("/deadLetters"))
//      context.actorOf(Props(classOf[WebIDClaimVerifier]),"webidVerifier")
//    else wiv
//  }

  def verify(x509claim: Claim[X509Certificate]): List[Promise[Validation[BananaException,WebIDPrincipal]]] = {
      val webidClaims = for (x509 <- x509claim) yield {
        Option(x509.getSubjectAlternativeNames()).toList.flatMap { coll =>
          import scala.collection.JavaConverters.iterableAsScalaIterableConverter
          for {
            sanPair <- coll.asScala if (sanPair.get(0) == 6)
          } yield (sanPair.get(1).asInstanceOf[String].trim,x509.getPublicKey)

        }
      }
      val listOfClaims =  webidClaims.sequence
      for ( webidclaim <- listOfClaims) yield verifyWebIDClaim(webidclaim)
  }




  val base10Types = List(xsd("integer"),xsd("int"),xsd("positiveInteger"),xsd("decimal"))

//  val webidVerifier = {
//    val wiv = context.actorFor("webidVerifier")
//    if (wiv == context.actorFor("/deadLetters"))
//      context.actorOf(Props(classOf[WebIDClaimVerifier]),"webidVerifier")
//    else wiv
//  }

  val query = sparqlOps.SelectQuery("""
      PREFIX : <http://www.w3.org/ns/auth/cert#>
      SELECT ?m ?e
      WHERE {
          ?webid :key [ :modulus ?m ;
                        :exponent ?e ].
      }""")

  /**
   * transform an RDF#Node to a positive Integer if possible
   * A bit heavy this implementation! Can't use asInstanceOf[T] as that info is sadly erased
   * @param node the node - as a literal - that should be the positive integer
   * @return a Validation containing and exception or the number
   */
  private def toPositiveInteger(node: Rdf#Node): Validation[BananaException,BigInteger] =
    node.fold(
       _=> FailedConversion("node must be a typed literal; it was: "+node).fail[BigInteger],
       _=> FailedConversion("node must be a typed literal; it was: "+node).fail[BigInteger],
       lit => lit.fold ( tl => try {
         fromTypedLiteral(tl) match {
           case (hexStr: String, xsd("hexBinary")) => Success(new BigInteger(stripSpace(hexStr), 16))
           case (base10Str: String, base10Tp) if base10Types.contains(base10Tp) => new BigInteger(base10Str).success[BananaException]
           case (_,tp) => FailedConversion(
             "do not recognise datatype "+tp+" as one of the legal numeric ones in node: " + node).fail[BigInteger]
         }
       } catch {
         case num: NumberFormatException =>
           FailedConversion("failed to convert to integer "+node+" - "+num.getMessage).fail[BigInteger]
       },
         langLit => FailedConversion("numbers don't have language tags: "+langLit).fail[BigInteger]
       )
    )


  /**
   * function to verifyWebIDClaim that a given Subject Alternative Name referent is the owner of a public key
   * @param san
   * @param key
   * @return a Promise of a Validation of the WebIDPrincipal if it is
   */
  def verifyWebID(san: String, key: PublicKey):  Promise[Validation[BananaException,WebIDPrincipal]] =  try {
    val uri = new java.net.URI(san)
    val webidProfile = new java.net.URI(san.split("#")(0))
    val scheme = webidProfile.getScheme
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
      return Promise.pure(
        UnsupportedProtocol("we only support http and https urls at present - this one was "+scheme, san).fail
      )

    key match {
      case rsaKey: RSAPublicKey =>  {
        val wid = ops.makeUri(san)
        val p = fetcher.fetch(webidProfile.toURL).map { graphVal: Validation[FetchException, GraphNHeaders[Rdf]]   =>
          graphVal.flatMap { case GraphNHeaders(graph,headers) =>
            val sols = graph.executeSelect(query, Map("webid" -> wid))
            val s: Iterable[Validation[BananaException,WebIDPrincipal]] = solutionIterator(sols).map { sol: Rdf#Solution =>
              val keyVal = ( getNode(sol,"m").flatMap{ toPositiveInteger(_) }
                           ⊛ getNode(sol,"e").flatMap{ toPositiveInteger(_) } ) { RSAPubKey(_,_) }
              keyVal.flatMap { key =>
                if (key.modulus == rsaKey.getModulus && key.exponent == rsaKey.getPublicExponent)
                  WebIDPrincipal(uri).success[BananaException]
                else new KeyMatchFailure("RSA key does not match one in profile",san,rsaKey,key).fail[WebIDPrincipal]
              }
            }
            val result = s.find(_.isSuccess).getOrElse {
              val failures: List[BananaException] = s.toList.map(_.fold(identity,throw new RuntimeException("impossible")))
              if (failures.size == 0) WebIDVerificationFailure("no rsa keys found in profile for WebID.",uri,failures).fail
              else WebIDVerificationFailure("no keys matched the WebID in the profile",uri,failures).fail
            }
            result
          }
        }
        p

      }
      case _ => Promise.pure(new UnsupportedKeyType("cannot verifyWebIDClaim WebID <"+san+"> with key of type "+
        key.getAlgorithm,key).fail)
   }
  } catch {
    case e: URISyntaxException  =>   Promise.pure(URISyntaxError("could not parse uri",List(e),san).fail)
    case e: MalformedURLException => Promise.pure(URISyntaxError("could not parse SAN as a URL",List(e),san).fail)
    case e: Exception => Promise.pure(WrappedThrowable(e).fail)
  }


  def verifyWebIDClaim(webidClaim: Claim[Pair[String,PublicKey]]): Promise[Validation[BananaException,WebIDPrincipal]] =
    webidClaim.verify { sk => verifyWebID(sk._1,sk._2) }
}

object JenaWebIDAuthN extends WebIDAuthN[Jena]()(
  JenaOperations, JenaSparqlOps,
  JenaGraphSparqlEngine.makeSparqlEngine,
  JenaGraphFetcher)

/**
 * A Claim is a Monad that contains something akin to a set of statements, that are not known to
 * be either true or false. The statement can only be extracted via a verifyWebIDClaim method
 *
 * @tparam S A object that represents a set of statement of some form. It can be an object that has relations
 *           to other objects which together can be thought of as a set of statements. Or it could be for example
 *           an RDF graph.
 */
trait Claim[+S] {
  protected val statements: S

  //warning: implicit here is not a good idea at all
  def verify[V](implicit fn: S=> V ): V
}

object Claim {
  implicit val ClaimMonad: Monad[Claim] with Traverse[Claim] =
    new Monad[Claim] with Traverse[Claim] {

    def traverseImpl[G[_] : Applicative, A, B](fa: Claim[A])(f: A => G[B]): G[Claim[B]] =
      f(fa.statements).map(a => this.point(a))

    def point[A](a: => A) = new Claim[A]{
      protected val statements : A = a;
      def verify[V](implicit fn: A=> V ) = fn(statements)
    }

    def bind[A, B](fa: Claim[A])(f: (A) => Claim[B]) = f(fa.statements)

  }

}
//
//trait VClaim[+S,+V] {
//  protected val claimed: S
//  val verified: V
//}
//
//object VClaim {
//  implicit val VClaimMonad: Monad[Claim] with Traverse[Claim] =
//    new Monad[Claim] with Traverse[Claim] {
//
//      def traverseImpl[G[_] : Applicative, A, B](fa: Claim[A])(f: A => G[B]): G[Claim[B]] =
//        f(fa.statements).map(a => this.point(a))
//
//      def point[A](a: => A) = new Claim[A]{
//        protected val statements : A = a;
//        def verify[V](implicit fn: A=> V ) = fn(statements)
//      }
//
//      def bind[A, B](fa: Claim[A])(f: (A) => Claim[B]) = f(fa.statements)
//
//    }
//
//}
//

case class WebIDPrincipal(webid: java.net.URI) extends Principal {
  val getName = webid.toString
}

case class RSAPubKey(modulus: BigInteger, exponent: BigInteger)


object VerificationException {
  implicit val bananaExceptionSemiGroup = firstSemigroup[VerificationException]

}

trait VerificationException extends BananaException {
//  type T <: AnyRef
//  val msg: String
//  val cause: List[Throwable]=Nil
//  val subject: T
}


abstract class WebIDClaimFailure extends VerificationException

class UnsupportedKeyType(val msg: String, val subject: PublicKey) extends WebIDClaimFailure { type T = PublicKey }

case class WebIDVerificationFailure(msg: String, webid: java.net.URI, failures: List[BananaException]) extends WebIDClaimFailure

abstract class SANFailure extends WebIDClaimFailure { type T = String }
case class UnsupportedProtocol(val msg: String, subject: String) extends SANFailure
case class URISyntaxError(val msg: String, val cause: List[Throwable], subject: String) extends SANFailure

//The subject could be more refined than the URL, especially in the paring error
abstract class ProfileError extends WebIDClaimFailure  { type T = URL }
case class ProfileGetError(val msg: String,  val cause: List[Throwable], subject: URL) extends ProfileError
case class ProfileParseError(val msg: String, val cause: List[Throwable], subject: URL) extends ProfileError



//it would be useful to pass the graph in
//perhaps change the WebID to the doc uri where it was fetched finally.
case class KeyMatchFailure(val msg: String, webid: String, certKey: RSAPublicKey, comparedWith: RSAPubKey ) extends VerificationException
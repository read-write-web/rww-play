package rww.ldp.auth


import java.math.BigInteger
import java.net.{MalformedURLException, URISyntaxException, URL}
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.{Principal, PublicKey}

import org.slf4j.LoggerFactory
import org.w3.banana._
import org.w3.banana.binder.RecordBinder
import play.api.libs.iteratee.{Enumerator, Enumeratee}
import rww.ldp.{CertBinder, WebResource, LDPCommand}
import rww.ldp.actor.RWWActorSystem
import utils.Iteratees

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._
import scalaz.Semigroup._
import scalaz.{Failure => _, Success => _, Validation => _, _}

object WebIDVerifier {

  /*
  * Useful when converting the bytes from a BigInteger to a hex for inclusion in rdf
  **/
  def hex(bytes: Array[Byte]): String = bytes.dropWhile(_ == 0).map("%02X" format _).mkString

  def stripSpace(hex: String): String = hex.filter(c => !Character.isWhitespace(c))

}


class CryptoUtil[Rdf <: RDF](implicit ops: RDFOps[Rdf]) {

  import WebIDVerifier._
  import ops._

  val base10Types = List(xsd("integer"),xsd("int"),xsd("positiveInteger"),xsd("decimal"))

  /**
   * transform an RDF#Node Literal to a positive Integer if possible
   * A bit heavy this implementation! Can't use asInstanceOf[T] as that info is sadly erased
   * @param node the node - as a literal - that should be the positive integer
   * @return a Validation containing and exception or the number
   */
   def toPositiveInteger(node: Rdf#Node): Try[BigInteger] =
    foldNode(node)(
      _=> Failure(FailedConversion("node must be a typed literal; it was: "+node)),
      _=> Failure(FailedConversion("node must be a typed literal; it was: "+node)),
      lit => try {
        fromLiteral(lit) match {
          case (hexStr, xsd("hexBinary"), None) => Success(new BigInteger(stripSpace(hexStr), 16))
          case (base10Str, base10Tp, None) if base10Types.contains(base10Tp) =>
            Success(new BigInteger(base10Str))
          case (_, tp, None) => Failure(
            FailedConversion("do not recognise datatype " + tp + " as one of the legal numeric ones in node: " + node)
          )
          case _ => Failure(FailedConversion("require a TypedLiteral not a LangLiteral. Received " + node))
        }
      } catch {
        case num: NumberFormatException =>
          Failure(FailedConversion("failed to convert to integer "+node+" - "+num.getMessage))
      }
    )

}


/**
 *
 */
class WebIDVerifier[Rdf <: RDF](
  web: WebResource[Rdf]
)(implicit
  ops: RDFOps[Rdf],
  sparqlOps: SparqlOps[Rdf],
  recordBinder: RecordBinder[Rdf],
  val ec: ExecutionContext
) {

  import ops._
  import org.w3.banana.diesel._

  val logger = LoggerFactory.getLogger(this.getClass)
  val crypto = new CryptoUtil[Rdf]()
  val cert = CertPrefix[Rdf]
  val certBinder = new CertBinder[Rdf]()
  import certBinder._

  import crypto._

  //todo: find a good sounding name for (String,PublicKey)
  //todo document what is going on eg: sanPair.get(1)

  def verify(x509claim: Claim[X509Certificate]): List[Future[Principal]] = {
    val listOfClaims =  webidClaims(x509claim).sequence
    for ( webidclaim <- listOfClaims) yield verifyWebIDClaim(webidclaim)
  }

  /**
   * transform a X509Cert Claim to a Claims about (san,publickyes) that can the be verified
   * @param x509claim
   * @return Claim of list of sans
   */
  protected def webidClaims(x509claim: Claim[X509Certificate]): Claim[List[(String, PublicKey)]] =
    for (x509 <- x509claim) yield {
      Option(x509.getSubjectAlternativeNames).toList.flatMap { collOfNames =>
        import scala.collection.JavaConverters.iterableAsScalaIterableConverter
        for {
          sanPair <- collOfNames.asScala
          if (sanPair.get(0) == 6)
        } yield (sanPair.get(1).asInstanceOf[String].trim,x509.getPublicKey)
      }
    }




  /**
   * function to verifyWebIDClaim that a given Subject Alternative Name referent is the owner of a public key
   * @param san
   * @param key
   * @return a Future Try of the WebIDPrincipal. The Try is not merged into the future
   */
  def verifyWebID(san: String, key: PublicKey):  Future[Principal] =  try {
    logger.debug(s"in verifyWebID for san=$san")
    import LDPCommand._
    val uri = new java.net.URI(san)
    val ir = san.split("#")(0)
    val webidProfile = new java.net.URI(ir)
    val scheme = webidProfile.getScheme
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
      Future.failed(
        UnsupportedProtocol("we only support http and https urls at present - this one was "+scheme, san)
      )
    else key match {
      case rsaKey: RSAPublicKey =>  {
        val wid = URI(san)
        val keyNPGEnum: Enumerator[Try[RSAPublicKey]] = for {
          webIdLDR <- web(wid)
          keyNPG <- web.~>(webIdLDR, cert.key)()
        } yield {  keyNPG.resource.as[RSAPublicKey] }

        val filterTo: Enumeratee[Try[RSAPublicKey],WebIDPrincipal] = Enumeratee.collect[Try[RSAPublicKey]] {
          case Success(pk) if pk ==  rsaKey => WebIDPrincipal(uri)
        }

        val result = keyNPGEnum.through(filterTo)
        Iteratees.enumeratorAsList(result).flatMap{
          case head::tail => Future.successful(head)
          case Nil => Future.failed(WebIDVerificationFailure("No matching keys",uri,List())) //todo
        }
      }
      case _ => Future.failed(
        new UnsupportedKeyType("cannot verifyWebIDClaim WebID <"+san+"> with key of type "+key.getAlgorithm,key)
      )
    }
  } catch {
    case e: URISyntaxException  =>   Future.failed(URISyntaxError(s"could not parse uri $san",List(e),san))
    case e: MalformedURLException => Future.failed(URISyntaxError(s"could not parse SAN as a URL $san",List(e),san))
    case e: Exception => Future.failed(WrappedThrowable(e))
  }


  def verifyWebIDClaim(webidClaim: Claim[(String,PublicKey)]): Future[Principal] =
    webidClaim.verify { sk => verifyWebID(sk._1,sk._2) }
}


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

  def verify[V](fn: S=> V ): V
}

object Claim {
  implicit val ClaimMonad: Monad[Claim] with Traverse[Claim] =
    new Monad[Claim] with Traverse[Claim] {

      def traverseImpl[G[_] : Applicative, A, B](fa: Claim[A])(f: A => G[B]): G[Claim[B]] =
        f(fa.statements).map(a => this.point(a))

      def point[A](a: => A) = new Claim[A]{
        protected val statements : A = a
        def verify[V](fn: A=> V ) = fn(statements)
      }

      def bind[A, B](fa: Claim[A])(f: (A) => Claim[B]) = f(fa.statements)

    }

}

case class RSAPubKey(modulus: BigInteger, exponent: BigInteger)


object VerificationException {
  implicit val bananaExceptionSemiGroup: Semigroup[VerificationException] =
    firstSemigroup[VerificationException]


}

trait VerificationException extends BananaException {
  //  type T <: AnyRef
  //  val msg: String
  //  val cause: List[Throwable]=Nil
  //  val findSubject: T
}

// TODO these exceptions should rather be refactored

trait WebIDClaimFailure extends VerificationException

class UnsupportedKeyType(val msg: String, val subject: PublicKey) extends Exception(msg) with WebIDClaimFailure { type T = PublicKey }

case class WebIDVerificationFailure(msg: String, webid: java.net.URI, failures: List[BananaException] = Nil) extends Exception(msg) with WebIDClaimFailure {
  failures.foreach( this.addSuppressed(_))
}

trait SANFailure extends WebIDClaimFailure { type T = String }
case class UnsupportedProtocol(val msg: String, subject: String) extends Exception(msg) with SANFailure
case class URISyntaxError(val msg: String, val cause: List[Throwable], subject: String) extends Exception(msg) with SANFailure {
  cause.foreach( this.addSuppressed(_))
}

//The findSubject could be more refined than the URL, especially in the paring error
trait ProfileError extends WebIDClaimFailure { type T = URL }
case class ProfileGetError(val msg: String,  val cause: List[Throwable], subject: URL) extends Exception(msg) with ProfileError {
  cause.foreach( this.addSuppressed(_))
}
case class ProfileParseError(val msg: String, val cause: List[Throwable], subject: URL) extends Exception(msg) with ProfileError {
  cause.foreach( this.addSuppressed(_))
}



//it would be useful to pass the graph in
//perhaps change the WebID to the doc uri where it was fetched finally.
case class KeyMatchFailure(val msg: String, webid: String, certKey: RSAPublicKey, comparedWith: RSAPubKey ) extends Exception(msg) with VerificationException
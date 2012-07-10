//
///*
// * Copyright (c) 2011 Henry Story (bblfish.net)
// * under the MIT licence defined
// *    http://www.opensource.org/licenses/mit-license.html
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy of
// * this software and associated documentation files (the "Software"), to deal in the
// * Software without restriction, including without limitation the rights to use, copy,
// * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// * and to permit persons to whom the Software is furnished to do so, subject to the
// * following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
// * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
// * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
// * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
// * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//
//package org.w3.webid.auth
//
//import java.security.interfaces.RSAPublicKey
//import scalaz.Scalaz
//import Scalaz._
//import java.security.PublicKey
//import com.hp.hpl.jena.rdf.model.Model
//import java.net.URL
//import com.hp.hpl.jena.query._
//import java.math.BigInteger
//import com.hp.hpl.jena.datatypes.xsd.XSDDatatype
//import org.w3.readwriteweb.CacheControl
//import scalaz.{Failure, Validation}
//
//
///**
// * One can only construct a WebID via the WebIDClaim apply
// */
//object WebIDClaim {
//  final val cert: String = "http://www.w3.org/ns/auth/cert#"
//  val numericDataTypes = List(XSDDatatype.XSDinteger, XSDDatatype.XSDint, XSDDatatype.XSDpositiveInteger, XSDDatatype.XSDdecimal)
//
//  //we cannot do the simpler ask query as Jena does not do Sparql d-entailment on xsd:hexBinary
//  //There is also the problem that most parsers will not be very lenient and fail on spaces in the hex
//  //( as required for little gain by xsd:hexBinary spec)
//  val query = QueryFactory.create("""
//      PREFIX : <http://www.w3.org/ns/auth/cert#>
//      SELECT ?m ?e
//      WHERE {
//          ?webid :key [ :modulus ?m ;
//                        :exponent ?e ].
//      }""")
//
//  /*
//   * Useful when converting the bytes from a BigInteger to a hex for inclusion in rdf
//   **/
//  def hex(bytes: Array[Byte]): String = bytes.dropWhile(_ == 0).map("%02X" format _).mkString
//
//  def stripSpace(hex: String): String = hex.filter(c => !Character.isWhitespace(c))
//
//}
//
///**
// * One has to construct a WebID using the object, that can do basic verifications
// */
//class WebIDClaim(val san: String, val key: PublicKey) {
//
//  import WebIDClaim._
//  import XSDDatatype._
//
//  private def rsaTest(webid: WebID, rsakey: RSAPublicKey): (Model) => Validation[WebIDVerificationFailure, WebID] = {
//    model =>
//      val initialBinding = new QuerySolutionMap();
//      initialBinding.add("webid", model.createResource(webid.url.toString))
//      //      initialBinding.add("m", model.createTypedLiteral(hex(rsakey.getModulus.toByteArray), XSDhexBinary))
//      //      initialBinding.add("e", model.createTypedLiteral(rsakey.getPublicExponent.toString, XSDinteger))
//      val qe: QueryExecution = QueryExecutionFactory.create(WebIDClaim.query, model, initialBinding)
//      try {
//        def matches: Boolean = {
//          import scala.collection.JavaConversions._
//          val resultset = qe.execSelect().toSet
//          resultset.exists {
//            sol: QuerySolution => try {
//              val mod = sol.getLiteral("m")
//              if (mod.getDatatype == XSDDatatype.XSDhexBinary &&
//                new BigInteger(stripSpace(mod.getLexicalForm), 16) == rsakey.getModulus) {
//                val exp = sol.getLiteral("e")
//                numericDataTypes.contains(exp.getDatatype) && new BigInteger(exp.getLexicalForm) == rsakey.getPublicExponent
//              } else false
//            } catch {
//              case _ => false
//            }
//          }
//        }
//        if (matches) webid.success
//        else new WebIDVerificationFailure("could not verify public key", None, this).fail
//      } finally {
//        qe.close()
//      }
//  }
//
//  def verify: Validation[WebIDClaimFailure, WebID] = key match {
//    case rsakey: RSAPublicKey =>
//      WebID(san).flatMap(webid=> {
//        webid.getDefiningModel(CacheControl.CacheOnly).flatMap(rsaTest(webid, rsakey)) match {
//          case Failure(_) => webid.getDefiningModel(CacheControl.NoCache).flatMap(rsaTest(webid, rsakey))
//          case o => o
//        }
//      }
//      )
//    case _ => new UnsupportedKeyType("We only support RSA keys at present", key).fail
//  }
//}
//
//
//trait Err {
//  type T <: AnyRef
//  val msg: String
//  val cause: Option[Throwable]=None
//  val subject: T
//}
//
//abstract class Fail extends Throwable with Err
//
//abstract class WebIDClaimFailure extends Fail
//
//class UnsupportedKeyType(val msg: String, val subject: PublicKey) extends WebIDClaimFailure { type T = PublicKey }
//
//
//abstract class SANFailure extends WebIDClaimFailure { type T = String }
//case class UnsupportedProtocol(val msg: String, subject: String) extends SANFailure
//case class URISyntaxError(val msg: String, subject: String) extends SANFailure
//
////The subject could be more refined than the URL, especially in the paring error
//abstract class ProfileError extends WebIDClaimFailure  { type T = URL }
//case class ProfileGetError(val msg: String,  override val cause: Option[Throwable], subject: URL) extends ProfileError
//case class ProfileParseError(val msg: String, override val cause: Option[Throwable], subject: URL) extends ProfileError
//
////it would be useful to pass the graph in
//class WebIDVerificationFailure(val msg: String, val caused: Option[Throwable], val subject: WebIDClaim)
//  extends WebIDClaimFailure { type T = WebIDClaim }

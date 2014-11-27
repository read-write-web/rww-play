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

package controllers

import java.io.{ByteArrayInputStream, IOException}
import java.math.BigInteger
import java.net.{MalformedURLException, URI, URISyntaxException}
import java.security._
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Date

import org.bouncycastle.asn1._
import org.bouncycastle.asn1.misc.{MiscObjectIdentifiers, NetscapeCertType}
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509._
import org.bouncycastle.cert.{X509CertificateHolder, X509v3CertificateBuilder}
import org.bouncycastle.crypto.params.{RSAKeyParameters, RSAPrivateCrtKeyParameters}
import org.bouncycastle.jce.netscape.NetscapeCertRequest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.{DefaultDigestAlgorithmIdentifierFinder, DefaultSignatureAlgorithmIdentifierFinder}
import org.bouncycastle.util.encoders.Base64
import play.api.Logger
import play.api.data.format.Formatter
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{Action, Controller, ResponseHeader, Result}

//import sun.security.x509._
import views.html

case class CertReq(cn: String, webids: List[URI], subjPubKey: PublicKey, validFrom: Date, validTo: Date) {//, email: List[URI], )

  /**
   * For more info on using bouncy castle to create certs see
   * https://www.mayrhofer.eu.org/create-x509-certs-in-java
   *
   * Previous code used sun.security.* which is easier to code to, but not
   * available on every platform, and prone to change between versions. If one could
   * have it on maven central that would make it really useful. see
   * http://stackoverflow.com/questions/12982595/openjdk-sun-security-libs-on-maven
   *
   * @return an X509CertificateHolder signed by the {} organisation
   */
  lazy val certificate: X509CertificateHolder = {

    val x509Builder = new X509v3CertificateBuilder(
      new X500Name(CertReq.issuer),
      BigInteger.valueOf(System.currentTimeMillis),
      validFrom,
      validTo,
      new X500Name("DN="+cn),
      new SubjectPublicKeyInfo(
        new ASN1InputStream(
          new ByteArrayInputStream(subjPubKey.getEncoded)
        ).readObject().asInstanceOf[ASN1Sequence]
      )
    )

    val ids = new GeneralNames(
      webids.toArray.collect {
          case webid if List("http","https").contains(webid.getScheme.toLowerCase) =>
            new GeneralName(
              GeneralName.uniformResourceIdentifier,
              webid.toString
            )
          case mailUri if mailUri.getScheme.equalsIgnoreCase("mailto") =>
            new GeneralName(GeneralName.rfc822Name, mailUri.getSchemeSpecificPart)
        }
    )
    x509Builder.addExtension(
      Extension.subjectAlternativeName,
      true,
      ids
    )
    x509Builder.addExtension(Extension.keyUsage, true, new KeyUsage(
      KeyUsage.digitalSignature | KeyUsage.nonRepudiation
        | KeyUsage.keyEncipherment | KeyUsage.keyAgreement
        | KeyUsage.keyCertSign));

    x509Builder.addExtension(Extension.basicConstraints, true,
      new BasicConstraints(false));

    x509Builder.addExtension(MiscObjectIdentifiers.netscapeCertType,
      false, new NetscapeCertType(NetscapeCertType.sslClient
        | NetscapeCertType.smime))

    val sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA")
    val digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
    val rsaParams = CertReq.issuerKey.getPrivate match {
      case k: RSAPrivateCrtKey =>
        new RSAPrivateCrtKeyParameters(
          k.getModulus(), k.getPublicExponent(), k.getPrivateExponent(),
          k.getPrimeP(), k.getPrimeQ(), k.getPrimeExponentP(), k.getPrimeExponentQ(),
          k.getCrtCoefficient());
      case k: RSAPrivateKey =>
        new RSAKeyParameters(true, k.getModulus(), k.getPrivateExponent());
    }


    val sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(rsaParams);
    x509Builder.build(sigGen)
  }

}
/**
 * This could be improved later by creating a class that takes a certificate chain ending in a cert
 * signed by CN=WebID,O=∅ , so that one could have intermediary agents take more direct responsibilities
 * for their members.
 */
object CertReq {
  // a special issuer the empty set of Organisations, ie the organisation of anything that self signs.
  lazy val issuer = "CN=WebID,O={}"

  //the ∅ user can have a different key every time. It is of no importance.
  lazy val issuerKey = {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    keyPairGenerator.generateKeyPair()
  }
  /**
   * create a subject DN for the given CN, by using the organisation info
   * todo: should remove any dangerous symbols from the CN
   * @param cn
   * @return
   */
  def subjectDN(cn: String) = "CN="+cn+",O=ReadWriteWeb"

}

/**
 *  Application to create client certificates
 */
object ClientCertificateApp extends Controller {
  import play.api.data.Forms._
  import play.api.data._
  //does it make sense to make the challenge random? what security benefits would be gained?
  val challenge = "AStaticallyEncodedString"
  val log = Logger("RWW").logger

  if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
    Security.addProvider(new BouncyCastleProvider());
  }

  implicit val absUriFormat: Formatter[Option[URI]] = new Formatter[Option[URI]] {
    def bind(key: String, data: Map[String, String]):  Either[Seq[FormError], Option[URI]] =
      data.get(key).map { san =>
        try {
          val trimmedSan = san.trim
          if (""==trimmedSan) Right(None)
          else {
            val uri = new URI(trimmedSan)
            if (uri.isAbsolute && null != uri.getAuthority )
              Right(Some(uri))
            else Left(Seq(FormError(key,"error.url.notabsolute",Nil)))
          }
        } catch {
          case e: URISyntaxException => Left(Seq(FormError(key,"error.url",Nil)))
          case e: MalformedURLException => Left(Seq(FormError(key,"error.url",Nil)))
        }
      }.getOrElse(Right(None))


    def unbind(key: String, value: Option[URI]) = value match {
      case Some(url) => Map(key -> value.toString)
      case None => Map()
    }
  }

  /**
   * Find the public key from a certificate request in spkac format
   *
   * @return a validation containing either a public key or an exception
   */
  val spkacFormatter: Formatter[PublicKey] =
    new Formatter[PublicKey] {
      def bind(key: String, data: Map[String, String]): Either[Seq[FormError], PublicKey] = {
        data.get(key).map { spkacB64 =>
          try {
            val ncr = new NetscapeCertRequest(Base64.decode(spkacB64))
            if (ncr.verify(challenge)) Right(ncr.getPublicKey)
            else {
              log.warn("error verifying spkac certificate request `"+spkacB64+"` against `"+challenge+"`")
              Left(Seq(FormError(key,"error verifying SPKAC certificate request", Nil)))
              //todo: log the details, it will be real useful to work out if there are bugs in certain browsers
            }
          } catch {
            case ioe:  IOException => {
              //This should never happen, but with bouncy you never know
              log.warn("io error parsing spkac certificate request?! `"+spkacB64+"`.",ioe)
              Left(Seq(FormError(key,"server problem parsing SPKAC certificate request",Nil)))
            }
            //ARghh!!! how can BC be so bad as to throw an IllegalArgumentException?!
            case conv: IllegalArgumentException => {
              log.warn("error parsing spkac certificate request `"+spkacB64+"`. Attack or browser issue?",conv)
              Left(Seq(FormError(key,"server problem parsing SPKAC certificate request",Nil)))
            }
          }
        }.getOrElse(Left(Seq(FormError(key,"missing spkac public key from request",Nil))))
     }

      def unbind(key: String, value: PublicKey) = Map() //we never unbind
    }

  //start ten minuted ago, in order to avoid problems with watch synchronisations. Should be longer
  def tenMinutesAgo = new Date(System.currentTimeMillis() - (10*1000*60).toLong)
  def yearsFromNow(years: Long) =  new Date(System.currentTimeMillis() + (years*365L*24L*60L*60L*1000L).toLong)


  val certForm = Form(
    mapping(
      "CN" -> email,
      "webids" -> list(of[Option[URI]]).
        transform[List[URI]](_.flatten,_.map(e=>Some(e))).
        verifying("require at least one WebID", _.size > 0),
      "spkac" -> of(spkacFormatter),
      "years" -> number(min=1,max=20)
    )((CN, webids, pubkey,years) => CertReq(CN,webids,pubkey,tenMinutesAgo,yearsFromNow(years)))
      ((req: CertReq) => Some(req.cn,req.webids,null,2))
  )

  def generate = Action { implicit request =>
      certForm.bindFromRequest.fold(
        errors => BadRequest(html.webid.cert.genericCertCreator(errors)),
        certreq => {
          Result(
            //https://developer.mozilla.org/en-US/docs/NSS_Certificate_Download_Specification
            header = ResponseHeader(200, Map("Content-Type" -> "application/x-x509-user-cert")),
            body   = Enumerator(certreq.certificate.getEncoded)
          )
        }
      )
  }

  def get = Action { req =>
    val webids =  req.queryString.get("webid").toList.flatten.map(new URI(_))
    Ok(views.html.webid.cert.genericCertCreator(certForm.fill(CertReq("",webids,null,null,null))))
  }


}

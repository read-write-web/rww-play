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

import play.api.mvc.{ResponseHeader, SimpleResult, Action, Controller}
import play.api.data.format.Formatter
import java.net.{MalformedURLException, URISyntaxException, URI, URL}
import views.html
import java.security.{KeyPairGenerator, SecureRandom, Security, PublicKey}
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.jce.netscape.NetscapeCertRequest
import java.io.IOException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import play.api.Logger
import java.security.cert.X509Certificate
import sun.security.x509._
import java.util.Date
import java.math.BigInteger
import play.api.libs.iteratee.Enumerator
import webid.WebID

case class CertReq(cn: String, webids: List[URL], subjPubKey: PublicKey, validFrom: Date, validTo: Date) {//, email: List[URI], )
  import CertReq._

  /**
   * We use sun.security.* for the moment in the hope that those will be placed on maven central
   * at some point http://stackoverflow.com/questions/12982595/openjdk-sun-security-libs-on-maven
   * @param req request information to build the certificate
   * @return an X509Certificate signed by the ∅ organisation
   */
  lazy val certificate: X509Certificate = {
    var info = new X509CertInfo
    val interval = new CertificateValidity(validFrom, validTo)
    val serialNumber = new BigInteger(64, new SecureRandom)
    val subjectXN = new X500Name(subjectDN(cn))
    val issuerXN = new X500Name(issuer)

    info.set(X509CertInfo.VALIDITY, interval)
    info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber))
    info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(subjectXN))
    info.set(X509CertInfo.ISSUER, new CertificateIssuerName(issuerXN))
    info.set(X509CertInfo.KEY, new CertificateX509Key(subjPubKey))
    info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3))

    //
    //extensions
    //
    val extensions = new CertificateExtensions

    val sans = new GeneralNames()
    for (webId <- webids){
      sans.add(new GeneralName(new URIName(webId.toExternalForm)))
    }
    val sanExt =
      new SubjectAlternativeNameExtension(true,sans)

    extensions.set(sanExt.getName, sanExt)

    val basicCstrExt = new BasicConstraintsExtension(false,1)
    extensions.set(basicCstrExt.getName,basicCstrExt)

    {
      import KeyUsageExtension._
      val keyUsage = new KeyUsageExtension
      val usages =
        List(DIGITAL_SIGNATURE, NON_REPUDIATION, KEY_ENCIPHERMENT, KEY_AGREEMENT)
      usages foreach { usage => keyUsage.set(usage, true) }
      extensions.set(keyUsage.getName,keyUsage)
    }

    {
      import NetscapeCertTypeExtension._
      val netscapeExt = new NetscapeCertTypeExtension
      List(SSL_CLIENT, S_MIME) foreach { ext => netscapeExt.set(ext, true) }
      extensions.set(
        netscapeExt.getName,
        new NetscapeCertTypeExtension(false, netscapeExt.getExtensionValue().clone))
    }

    val subjectKeyExt =
      new SubjectKeyIdentifierExtension(new KeyIdentifier(subjPubKey).getIdentifier)

    extensions.set(subjectKeyExt.getName, subjectKeyExt)

    info.set(X509CertInfo.EXTENSIONS, extensions)

    val algo = issuerKey.getPublic.getAlgorithm match {
      case "DSA" => new AlgorithmId(AlgorithmId.sha1WithDSA_oid )
      case "RSA" => new AlgorithmId(AlgorithmId.sha1WithRSAEncryption_oid)
      case _ => sys.error("Don't know how to sign with this type of key")
    }

    info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo))

    // Sign the cert to identify the algorithm that's used.
    val tmpCert = new X509CertImpl(info)
    tmpCert.sign(issuerKey.getPrivate, algo.getName)

    //update the algorithm and re-sign
    val sigAlgo = tmpCert.get(X509CertImpl.SIG_ALG).asInstanceOf[AlgorithmId]
    info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, sigAlgo)
    val cert = new X509CertImpl(info)
    cert.sign(issuerKey.getPrivate,algo.getName)

    //no need to verify. perhaps only in test mode
    cert.verify(issuerKey.getPublic)
    cert
  }
}
/**
 * This could be improved later by creating a class that takes a certificate chain ending in a cert
 * signed by CN=WebID,O=∅ , so that one could have intermediary agents take more direct responsibilities
 * for their members.
 */
object CertReq {
  // a special issuer the empty set of Organisations, ie the organisation of anything that self signs.
  val issuer = WebID.DnName;

  //the ∅ user can have a different key every time. It is of no importance.
  val issuerKey = {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(1024)
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
  import play.api.data._
  import play.api.data.Forms._
  //does it make sense to make the challenge random? what security benefits would be gained?
  val challenge = "AStaticallyEncodedString"
  val log = Logger("RWW").logger

  if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
    Security.addProvider(new BouncyCastleProvider());
  }

  implicit val absUrlFormat: Formatter[Option[URL]] = new Formatter[Option[URL]] {
    def bind(key: String, data: Map[String, String]):  Either[Seq[FormError], Option[URL]] =
      data.get(key).map { san =>
        try {
          val trimmedSan = san.trim
          if (""==trimmedSan) Right(None)
          else {
            val uri = new URI(trimmedSan)
            if (uri.isAbsolute && null != uri.getAuthority )
              Right(Some(uri.toURL))
            else Left(Seq(FormError(key,"error.url.notabsolute",Nil)))
          }
        } catch {
          case e: URISyntaxException => Left(Seq(FormError(key,"error.url",Nil)))
          case e: MalformedURLException => Left(Seq(FormError(key,"error.url",Nil)))
        }
      }.getOrElse(Right(None))


    def unbind(key: String, value: Option[URL]) = value match {
      case Some(url) => Map(key -> value.toString)
      case None => Map()
    }
  }

  /**
   * Find the public key from a certificate request in spkac format
   *
   * @param spkac a Base64 <a href="http://dev.w3.org/html5/spec/Overview.html#the-keygen-element">Signed Public Key
   *              And Challenge</a> as sent by the browser's
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
  def aYearFromNow =  new Date(System.currentTimeMillis() + (365*24*60*60*1000).toLong)


  val certForm = Form(
    mapping(
      "CN" -> email,
      "webids" -> list(of[Option[URL]]).
        transform[List[URL]](_.flatten,_.map(e=>Some(e))).
        verifying("require at least one WebID", _.size > 0),
      "spkac" -> of(spkacFormatter)
    )((CN, webids, pubkey) => CertReq(CN,webids,pubkey,tenMinutesAgo,aYearFromNow))
      ((req: CertReq) => Some(req.cn,req.webids,null))
  )

  def generate = Action { implicit request =>
      certForm.bindFromRequest.fold(
        errors => BadRequest(html.webid.cert.genericCertCreator(errors)),
        certreq => {
          SimpleResult(
            //https://developer.mozilla.org/en-US/docs/NSS_Certificate_Download_Specification
            header = ResponseHeader(200, Map("Content-Type" -> "application/x-x509-user-cert")),
            body   = Enumerator(certreq.certificate.getEncoded)
          )
        }
      )
  }

  def get = Action {
    Ok(views.html.webid.cert.genericCertCreator(certForm.fill(CertReq("",List(),null,null,null))))
  }


}

package rww.ldp

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

import org.w3.banana.binder.{PGBinder, RecordBinder}
import org.w3.banana.{CertPrefix, RDF, RDFOps}

/**
 * Binder for mapping certificates elements to rdf and back
 * @param ops
 * @param recordBinder
 * @tparam Rdf
 */
class CertBinder[Rdf <: RDF]()(implicit ops: RDFOps[Rdf], recordBinder: RecordBinder[Rdf]) {
    import ops._
    import recordBinder._
    import utils.CryptoUtils.BigIntOps
    val cert = CertPrefix[Rdf]

//   implicit val rsaClassUri = classUrisFor[RSAPublicKey](cert.RSAPublicKey)
    val factory = KeyFactory.getInstance("RSA")
    val exponent = property[BigInteger](cert.exponent)
    val modulus = property[Array[Byte]](cert.modulus)

    implicit val rsaPubKeybinder: PGBinder[Rdf, RSAPublicKey] =
      pgb[RSAPublicKey](modulus, exponent)(
        (m,e)=>factory.generatePublic(new RSAPublicKeySpec(new BigInteger(1,m),e)).asInstanceOf[RSAPublicKey],
        key => Some((key.getModulus.toUnsignedByteArray(),key.getPublicExponent))
      ) // withClasses rsaClassUri

  //todo: find better name for key
  val rsaPubKeybinderWithHash: PGBinder[Rdf, RSAPublicKey] =
    constructRsaPubKeybinderWithHash((key: RSAPublicKey) =>
      "#key_" + Math.abs(key.getModulus.hashCode())
    )

  //todo: find better name for key
   def constructRsaPubKeybinderWithHash(buildFragment: RSAPublicKey => String): PGBinder[Rdf, RSAPublicKey] =
    pgbWithId[RSAPublicKey](key=>URI(buildFragment(key)))(modulus, exponent)(
      (m,e)=>factory.generatePublic(new RSAPublicKeySpec(new BigInteger(1,m),e)).asInstanceOf[RSAPublicKey],
      key => Some((key.getModulus.toUnsignedByteArray(),key.getPublicExponent))
    ) // withClasses rsaClassUri


}

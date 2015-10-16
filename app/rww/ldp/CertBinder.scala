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
    val cert = CertPrefix[Rdf]

//   implicit val rsaClassUri = classUrisFor[RSAPublicKey](cert.RSAPublicKey)
    val factory = KeyFactory.getInstance("RSA")
    val exponent = property[BigInteger](cert.exponent)
    val modulus = property[Array[Byte]](cert.modulus)

    implicit val rsaPubKeybinder: PGBinder[Rdf, RSAPublicKey] =
      pgb[RSAPublicKey](modulus, exponent)(
        (m,e)=>factory.generatePublic(new RSAPublicKeySpec(new BigInteger(m),e)).asInstanceOf[RSAPublicKey],
        key => Some((key.getModulus.toByteArray,key.getPublicExponent))
      ) // withClasses rsaClassUri

    val rsaPubKeybinderWithHash: PGBinder[Rdf, RSAPublicKey] =
    pgbWithId[RSAPublicKey](key=>URI("#key_"+key.getModulus.hashCode()))(modulus, exponent)(
      (m,e)=>factory.generatePublic(new RSAPublicKeySpec(new BigInteger(m),e)).asInstanceOf[RSAPublicKey],
      key => Some((key.getModulus.toByteArray,key.getPublicExponent))
    ) // withClasses rsaClassUri


}

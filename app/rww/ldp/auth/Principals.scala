package rww.ldp.auth

import java.security.{PublicKey, Principal}
import java.util.Base64


case class WebIDPrincipal(webid: java.net.URI) extends Principal {
  val getName = webid.toString
}

case class WebKeyPrincipal(key: java.net.URI) extends Principal {
  override def getName = key.toString
}

case class PubKeyPrincipal(key: PublicKey) extends Principal {
  //todo: find a standard encoding - this was done quickly without thinking
  override def getName = Base64.getEncoder.encodeToString(key.getEncoded)
}
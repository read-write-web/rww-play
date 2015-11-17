package utils

import java.math.BigInteger

/**
  * Created by hjs on 17/11/2015.
  */
object CryptoUtils {

  implicit class BigIntOps(big: BigInteger) {

    /** the unsigned byte array for this bigInt */
    def toUnsignedByteArray(): Array[Byte] = {
      val bigBytes = big.toByteArray
      if ((big.bitLength()%8) != 0) {
        bigBytes
      } else {
        val smallerBytes = new Array[Byte](big.bitLength()/8)
        System.arraycopy(bigBytes,1,smallerBytes,0,smallerBytes.length)
        smallerBytes
      }
    }
  }


}

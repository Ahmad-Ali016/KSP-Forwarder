package com.kspay.forwarder.crypto

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Signs KPay canonical strings with SHA256withRSA using the appPrivateKey issued at KPay
 * sign-in (Base64-encoded PKCS#8). Uses java.util.Base64 rather than android.util.Base64 so
 * this stays a pure-JVM unit test with no Robolectric/device dependency.
 */
object RsaSigner {

    private const val KEY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"

    fun loadPrivateKey(base64Pkcs8PrivateKey: String): PrivateKey {
        val keyBytes = Base64.getDecoder().decode(base64Pkcs8PrivateKey)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(keySpec)
    }

    fun sign(canonicalString: String, privateKey: PrivateKey): String {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(canonicalString.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    fun sign(canonicalString: String, base64Pkcs8PrivateKey: String): String =
        sign(canonicalString, loadPrivateKey(base64Pkcs8PrivateKey))
}

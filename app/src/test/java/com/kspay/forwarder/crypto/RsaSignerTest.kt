package com.kspay.forwarder.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class RsaSignerTest {

    private fun generateBase64Pkcs8PrivateKey(): Pair<String, java.security.PublicKey> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val base64PrivateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        return base64PrivateKey to keyPair.public
    }

    @Test
    fun `sign produces a signature verifiable by the matching public key`() {
        val (base64PrivateKey, publicKey) = generateBase64Pkcs8PrivateKey()
        val canonicalString = "GET\n/v2/pos/query\n1719900000000\nabcdefghijklmnopqrstuvwxyz012345\n"

        val signatureBase64 = RsaSigner.sign(canonicalString, base64PrivateKey)

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(canonicalString.toByteArray(Charsets.UTF_8))
        assertTrue(verifier.verify(Base64.getDecoder().decode(signatureBase64)))
    }

    @Test
    fun `sign is deterministic for a fixed key and message`() {
        val (base64PrivateKey, _) = generateBase64Pkcs8PrivateKey()
        val canonicalString = "POST\n/v2/pos/sales\n1719900000000\nabcdefghijklmnopqrstuvwxyz012345\n{\"a\":1}\n"

        val signature1 = RsaSigner.sign(canonicalString, base64PrivateKey)
        val signature2 = RsaSigner.sign(canonicalString, base64PrivateKey)

        assertEquals(signature1, signature2)
    }

    @Test
    fun `different messages produce different signatures`() {
        val (base64PrivateKey, _) = generateBase64Pkcs8PrivateKey()

        val signatureA = RsaSigner.sign("GET\n/v2/pos/query\n1\nnonceA\n", base64PrivateKey)
        val signatureB = RsaSigner.sign("GET\n/v2/pos/query\n1\nnonceB\n", base64PrivateKey)

        assertTrue(signatureA != signatureB)
    }
}

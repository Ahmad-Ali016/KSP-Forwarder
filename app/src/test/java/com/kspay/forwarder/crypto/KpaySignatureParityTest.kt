package com.kspay.forwarder.crypto

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Byte-for-byte signature parity against KPay's own Signature-verification tool sample vector.
 * A pass here means our CanonicalString + RsaSigner implementation matches KPay's real signing
 * algorithm exactly — the make-or-break gate for the whole crypto approach.
 *
 * Reads the vector from a local, gitignored .env file at the repo root (see .gitignore — never
 * committed). If that file is absent (e.g. a fresh clone without the vector), this test is
 * skipped rather than failed, so `./gradlew test` stays green without the secret present.
 */
class KpaySignatureParityTest {

    private lateinit var env: Map<String, String>

    @Before
    fun loadEnv() {
        val envFile = File(System.getProperty("rootDir"), ".env")
        assumeTrue("No .env vector file present — skipping parity test", envFile.exists())
        env = envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index == -1) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
    }

    @Test
    fun `signature matches KPay's Signature-verification tool byte-for-byte`() {
        val canonicalString = CanonicalString.build(
            method = env.getValue("KPAY_SAMPLE_METHOD"),
            uri = env.getValue("KPAY_SAMPLE_URI"),
            timestamp = env.getValue("KPAY_SAMPLE_TIMESTAMP"),
            nonce = env.getValue("KPAY_SAMPLE_NONCE"),
            body = env.getValue("KPAY_SAMPLE_BODY"),
        )

        // The PEM block in KPay's docs is line-wrapped; the .env copy inherited stray spaces
        // and dropped newlines from that formatting. Base64 never contains whitespace, so
        // stripping headers/footers and all whitespace safely reconstructs the raw key bytes.
        val cleanedPrivateKey = env.getValue("KPAY_SAMPLE_APP_PRIVATE_KEY")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot { it.isWhitespace() }

        val actualSignature = RsaSigner.sign(canonicalString, cleanedPrivateKey)

        assertEquals(env.getValue("KPAY_SAMPLE_EXPECTED_SIGNATURE"), actualSignature)
    }
}

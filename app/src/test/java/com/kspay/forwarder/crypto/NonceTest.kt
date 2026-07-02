package com.kspay.forwarder.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NonceTest {

    @Test
    fun `generate produces a 32-character alphanumeric string`() {
        val nonce = Nonce.generate()

        assertEquals(32, nonce.length)
        assertTrue(nonce.all { it.isLetterOrDigit() })
    }

    @Test
    fun `generate produces distinct values across calls`() {
        val nonces = (1..100).map { Nonce.generate() }.toSet()

        assertEquals(100, nonces.size)
    }
}

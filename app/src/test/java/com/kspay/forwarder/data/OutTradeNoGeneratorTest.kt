package com.kspay.forwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutTradeNoGeneratorTest {

    @Test
    fun `generate produces a value at most 32 chars, letters and digits only`() {
        val generator = OutTradeNoGenerator(devicePrefix = "device-01")

        val outTradeNo = generator.generate()

        assertTrue(outTradeNo.length <= 32)
        assertTrue(outTradeNo.all { it.isLetterOrDigit() })
    }

    @Test
    fun `special characters in the device prefix are stripped`() {
        val generator = OutTradeNoGenerator(devicePrefix = "AB-12_34!")

        val outTradeNo = generator.generate()

        assertTrue(outTradeNo.startsWith("AB1234"))
    }

    @Test
    fun `generate produces distinct values across calls`() {
        val generator = OutTradeNoGenerator(devicePrefix = "dev")

        val values = (1..50).map { generator.generate() }.toSet()

        assertEquals(50, values.size)
    }
}

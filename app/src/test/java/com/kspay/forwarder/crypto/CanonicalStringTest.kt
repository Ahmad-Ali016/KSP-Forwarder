package com.kspay.forwarder.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalStringTest {

    @Test
    fun `GET request produces 4 lines with trailing newline`() {
        val result = CanonicalString.build(
            method = "GET",
            uri = "/v2/pos/query",
            timestamp = "1719900000000",
            nonce = "abcdefghijklmnopqrstuvwxyz012345",
        )

        val expected = "GET\n/v2/pos/query\n1719900000000\nabcdefghijklmnopqrstuvwxyz012345\n"
        assertEquals(expected, result)
        assertEquals(4, result.trimEnd('\n').split("\n").size)
        assertEquals('\n', result.last())
    }

    @Test
    fun `POST request produces 5 lines with trailing newline`() {
        val result = CanonicalString.build(
            method = "POST",
            uri = "/v2/pos/sales",
            timestamp = "7652652834398",
            nonce = "KzUETJOSySu3oSR97sp6mxEM5XS2dN5I",
            body = """{"outTradeNo":"20xxxxxxxxxxxx1","payAmount":"000000000100","tipsAmount":"000000000000","payCurrency":"036"}""",
        )

        val expected = "POST\n/v2/pos/sales\n7652652834398\nKzUETJOSySu3oSR97sp6mxEM5XS2dN5I\n" +
            """{"outTradeNo":"20xxxxxxxxxxxx1","payAmount":"000000000100","tipsAmount":"000000000000","payCurrency":"036"}""" +
            "\n"
        assertEquals(expected, result)
        assertEquals(5, result.trimEnd('\n').split("\n").size)
        assertEquals('\n', result.last())
    }

    @Test
    fun `a body ending in newline produces a double trailing newline`() {
        val result = CanonicalString.build(
            method = "POST",
            uri = "/v2/pos/sales",
            timestamp = "1",
            nonce = "nonce",
            body = "{}\n",
        )

        assertEquals("POST\n/v2/pos/sales\n1\nnonce\n{}\n\n", result)
    }
}

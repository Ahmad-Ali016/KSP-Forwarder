package com.kspay.forwarder.kpay

import org.junit.Assert.assertEquals
import org.junit.Test

class KposKeyFixupTest {

    @Test
    fun `replaces literal escaped equals at the end of a key`() {
        val raw = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKg" + "\\u003d"
        val expected = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKg="

        assertEquals(expected, KposKeyFixup.fix(raw))
    }

    @Test
    fun `replaces uppercase escaped equals`() {
        val raw = "abc" + "\\u003D" + "\\u003D"
        assertEquals("abc==", KposKeyFixup.fix(raw))
    }

    @Test
    fun `replaces multiple occurrences`() {
        val raw = "abc" + "\\u003d" + "\\u003d"
        assertEquals("abc==", KposKeyFixup.fix(raw))
    }

    @Test
    fun `leaves a clean key unchanged`() {
        val clean = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKg=="
        assertEquals(clean, KposKeyFixup.fix(clean))
    }
}

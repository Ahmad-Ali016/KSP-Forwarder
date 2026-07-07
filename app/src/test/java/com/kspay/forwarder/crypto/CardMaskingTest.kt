package com.kspay.forwarder.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardMaskingTest {

    @Test
    fun `masks a realistic 16-digit PAN to first6+last4`() {
        assertEquals("411111******1111", maskCardNo("4111111111111111"))
    }

    @Test
    fun `null cardNo stays null`() {
        assertNull(maskCardNo(null))
    }

    @Test
    fun `short value is fully masked rather than partially exposed`() {
        assertEquals("****", maskCardNo("1234"))
    }
}

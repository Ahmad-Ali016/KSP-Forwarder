package com.kspay.forwarder.crypto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `zero round-trips`() {
        val cents = Money.toKpayCents(BigDecimal("0.00"))
        assertEquals("000000000000", cents)
        assertEquals(BigDecimal("0.00"), Money.fromKpayCents(cents))
    }

    @Test
    fun `100 dollars round-trips per spec example`() {
        val cents = Money.toKpayCents(BigDecimal("100.00"))
        assertEquals("000000010000", cents)
        assertEquals(BigDecimal("100.00"), Money.fromKpayCents(cents))
    }

    @Test
    fun `1 dollar matches the KPay sample vector body`() {
        assertEquals("000000000100", Money.toKpayCents(BigDecimal("1.00")))
    }

    @Test
    fun `a value using all 12 digits round-trips`() {
        val amount = BigDecimal("9999999999.99")
        val cents = Money.toKpayCents(amount)
        assertEquals("999999999999", cents)
        assertEquals(amount, Money.fromKpayCents(cents))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative amount is rejected`() {
        Money.toKpayCents(BigDecimal("-1.00"))
    }

    @Test(expected = ArithmeticException::class)
    fun `fractional cents are rejected`() {
        Money.toKpayCents(BigDecimal("1.005"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromKpayCents rejects a string of the wrong length`() {
        Money.fromKpayCents("100")
    }
}

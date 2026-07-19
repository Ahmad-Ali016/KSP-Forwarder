package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {

    private fun transaction(payAmountCents: String = "000000012345") = LocalTransaction(
        outTradeNo = "OT-1",
        state = TransactionState.SUCCEEDED,
        payAmountCents = payAmountCents,
        currency = "036",
        paymentType = 1,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun result(
        payMethod: Int? = 2,
        transactionType: Int? = 1,
        cardNo: String? = "****0852",
        cardInputCode: String? = "C",
        aidLabel: String? = "Mastercard",
        aid: String? = "A0000000041010",
        refNo: String? = "260611000662",
        tc: String? = "D7DA3981F09680BB",
        authCode: String? = "005756",
        batchNo: String? = "000001",
        traceNo: String? = "000025",
        commitTime: Long? = 1749617165000,
        tipsAmount: String? = "000000000000",
        surchargeAmount: String? = "000000000472",
        orderAmount: String? = "000000009921",
        kpayMerchantNo: String? = "061004463100001",
        terminalType: String? = "N950",
        appVersion: String? = "V2.0.8",
    ) = QueryResponse(
        outTradeNO = "OT-1",
        payResult = 2,
        payMethod = payMethod,
        transactionType = transactionType,
        cardNo = cardNo,
        cardInputCode = cardInputCode,
        aidLabel = aidLabel,
        aid = aid,
        refNo = refNo,
        tc = tc,
        authCode = authCode,
        batchNo = batchNo,
        traceNo = traceNo,
        commitTime = commitTime,
        tipsAmount = tipsAmount,
        surchargeAmount = surchargeAmount,
        orderAmount = orderAmount,
        kpayMerchantNo = kpayMerchantNo,
        terminalType = terminalType,
        appVersion = appVersion,
    )

    private fun List<PrintStep>.allText(): String =
        joinToString(" ") { listOfNotNull(it.textContent, it.leftTextContent, it.rightTextContent).joinToString(" ") }

    @Test
    fun `includes the KSPay title, support email, and ABN`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertTrue(text.contains("KSPay"))
        assertTrue(text.contains("support@kspay.com.au"))
        assertTrue(text.contains("ABN:98642363853"))
    }

    @Test
    fun `never prints a phone number or KPay's own branding`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertFalse(text.contains("0474961115"))
        assertFalse(text.contains("kspayptyltd@gmail.com", ignoreCase = true))
        assertFalse(steps.any { it.textContent == "KPay" })
    }

    @Test
    fun `never prints fields KPay's API does not return`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertFalse(text.contains("Platform MID"))
        assertFalse(text.contains("Platform TID"))
        assertFalse(text.contains("Card SN"))
        assertFalse(text.contains("ATC"))
        assertTrue(steps.none { it.printType in setOf("QR_CODE", "BAR_CODE") })
    }

    @Test
    fun `omits the TID line when no TID is available`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = null)

        assertFalse(steps.any { it.textContent?.startsWith("TID:") == true })
    }

    @Test
    fun `includes the TID line when available`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertTrue(steps.any { it.textContent == "TID:00000917" })
    }

    @Test
    fun `renders the merchant number, scheme, and totals`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertTrue(text.contains("MID:061004463100001"))
        assertTrue(text.contains("Mastercard Sale"))
        assertTrue(steps.any { it.leftTextContent == "BASE" && it.rightTextContent == "AUD 123.45" })
        assertTrue(steps.any { it.leftTextContent == "TOTAL" && it.rightTextContent == "AUD 99.21" })
    }

    @Test
    fun `renders card number with the input code suffix`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertTrue(steps.any { it.leftTextContent == "Card No:" && it.rightTextContent == "****0852(C)" })
    }

    @Test
    fun `always prints a static XX-XX expiry placeholder, never a real value`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertTrue(steps.any { it.leftTextContent == "Expiry: XX/XX" })
    }

    @Test
    fun `omits lines entirely rather than sending an empty string when their data is missing`() {
        // KPay's docs require a minimum length of 1 for these fields -- an empty string
        // reproduced a real HTTP 500 from the terminal's print handler, so every one of these
        // must be dropped, never sent blank, when the underlying field wasn't returned.
        val steps = ReceiptFormatter.buildSteps(
            transaction(),
            result(cardNo = null, aidLabel = null, aid = null, refNo = null, tc = null, authCode = null, commitTime = null),
            tid = "00000917",
        )

        assertFalse(steps.any { it.leftTextContent == "Card No:" })
        assertFalse(steps.any { it.leftTextContent == "APP label:" })
        assertFalse(steps.any { it.leftTextContent == "AID:" })
        assertFalse(steps.any { it.leftTextContent == "Ref:" })
        assertFalse(steps.any { it.leftTextContent == "TC:" })
        assertFalse(steps.any { it.leftTextContent == "Expiry: XX/XX" })
        assertFalse(steps.any { it.leftTextContent == "Txn time:" })
    }

    @Test
    fun `omits the Batch-Trace line only when both are missing`() {
        val steps = ReceiptFormatter.buildSteps(
            transaction(),
            result(batchNo = null, traceNo = null),
            tid = "00000917",
        )

        assertFalse(steps.any { it.leftTextContent?.startsWith("Batch:") == true })
    }

    @Test
    fun `keeps the Batch-Trace line when only one side is present`() {
        val steps = ReceiptFormatter.buildSteps(
            transaction(),
            result(batchNo = "000001", traceNo = null),
            tid = "00000917",
        )

        assertTrue(steps.any { it.leftTextContent == "Batch: 000001" && it.rightTextContent == "Trace: " })
    }
}

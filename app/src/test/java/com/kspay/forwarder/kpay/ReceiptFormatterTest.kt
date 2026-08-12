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
        kpayMerchantNameEN: String? = "KSPay",
        kpayMerchantAddress: String? = "123 Example St, Sydney NSW 2000",
        receiptDisclaimersEN: String? = "This receipt is provided for your records only.",
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
        kpayMerchantNameEN = kpayMerchantNameEN,
        kpayMerchantAddress = kpayMerchantAddress,
        receiptDisclaimersEN = receiptDisclaimersEN,
    )

    private fun List<PrintStep>.allText(): String =
        joinToString(" ") { listOfNotNull(it.textContent, it.leftTextContent, it.rightTextContent).joinToString(" ") }

    @Test
    fun `includes the merchant name and address from KPay's API, support email, and ABN`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertTrue(text.contains("KSPay"))
        assertTrue(text.contains("123 Example St, Sydney NSW 2000"))
        assertTrue(text.contains("support@kspay.com.au"))
        assertTrue(text.contains("ABN:98642363853"))
    }

    @Test
    fun `omits the merchant address line when KPay does not return one`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(kpayMerchantAddress = null), tid = "00000917")

        assertFalse(steps.allText().contains("Sydney"))
    }

    @Test
    fun `never prints a phone number, and uses our own static support email rather than KPay's`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertFalse(text.contains("0474961115"))
        assertFalse(text.contains("kspayptyltd@gmail.com", ignoreCase = true))
        assertTrue(text.contains("support@kspay.com.au"))
    }

    @Test
    fun `never prints fields KPay's API does not return at all`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        val text = steps.allText()

        assertFalse(text.contains("Platform MID"))
        assertFalse(text.contains("Platform TID"))
        assertFalse(text.contains("Card SN"))
        assertFalse(text.contains("ATC"))
        assertTrue(steps.none { it.printType == "QR_CODE" })
    }

    @Test
    fun `prints the mandatory processing-detail fields KPay's compliance review requires`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertTrue(steps.any { it.leftTextContent == "APP label:" && it.rightTextContent == "Mastercard" })
        assertTrue(steps.any { it.leftTextContent == "AID:" && it.rightTextContent == "A0000000041010" })
        assertTrue(steps.any { it.leftTextContent == "TC:" && it.rightTextContent == "D7DA3981F09680BB" })
        assertTrue(steps.any { it.leftTextContent == "Expiry: XX/XX" && it.rightTextContent == "ACode: 005756" })
        assertTrue(steps.any { it.leftTextContent == "Batch: 000001" && it.rightTextContent == "Trace: 000025" })
    }

    @Test
    fun `prints the terminal model-version footer`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertTrue(steps.any { it.textContent == "N950 - V2.0.8" })
    }

    @Test
    fun `omits the footer when either terminalType or appVersion is missing`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(terminalType = null), tid = "00000917")

        assertFalse(steps.any { it.textContent?.contains("V2.0.8") == true })
    }

    @Test
    fun `always prints Trade with the forwarder's own outTradeNo`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(cardNo = null, refNo = null), tid = "00000917")

        assertTrue(steps.any { it.leftTextContent == "Trade:" && it.rightTextContent == "OT-1" })
    }

    @Test
    fun `never prints the Merchant Copy divider line`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertFalse(steps.any { it.textContent?.contains("Merchant Copy") == true })
    }

    @Test
    fun `prints TID as literal null when no TID is available, rather than omitting the line`() {
        // Deliberate, per the user: the line's presence/absence signals whether KPay actually
        // returned a TID this time, rather than silently disappearing.
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = null)

        assertTrue(steps.any { it.textContent == "TID:null" })
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
    fun `renders KPay's ref under the Ref-Tran id label`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        assertTrue(steps.any { it.leftTextContent == "Ref/Tran. id:" && it.rightTextContent == "260611000662" })
    }

    @Test
    fun `prints a barcode step encoding refNo right after the payment type line`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")

        val typeIndex = steps.indexOfFirst { it.textContent == "Mastercard Sale" }
        val barcodeIndex = steps.indexOfFirst { it.printType == "BAR_CODE" }

        assertEquals(typeIndex + 1, barcodeIndex)
        assertEquals("260611000662", steps[barcodeIndex].barcodeContent)
    }

    @Test
    fun `omits the barcode step entirely when refNo is missing`() {
        val steps = ReceiptFormatter.buildSteps(transaction(), result(refNo = null), tid = "00000917")

        assertTrue(steps.none { it.printType == "BAR_CODE" })
    }

    @Test
    fun `prints the disclaimer text when KPay returns it, omits the line when null`() {
        val withDisclaimer = ReceiptFormatter.buildSteps(transaction(), result(), tid = "00000917")
        assertTrue(withDisclaimer.allText().contains("This receipt is provided for your records only."))

        val withoutDisclaimer = ReceiptFormatter.buildSteps(
            transaction(),
            result(receiptDisclaimersEN = null),
            tid = "00000917",
        )
        assertFalse(withoutDisclaimer.allText().contains("provided for your records"))
    }

    @Test
    fun `omits Card No, Ref, Txn time, and the barcode entirely rather than sending an empty string when missing`() {
        // KPay's docs require a minimum length of 1 for these fields -- an empty string
        // reproduced a real HTTP 500 from the terminal's print handler, so every one of these
        // must be dropped, never sent blank, when the underlying field wasn't returned.
        val steps = ReceiptFormatter.buildSteps(
            transaction(),
            result(cardNo = null, refNo = null, commitTime = null),
            tid = "00000917",
        )

        assertFalse(steps.any { it.leftTextContent == "Card No:" })
        assertFalse(steps.any { it.leftTextContent == "Ref/Tran. id:" })
        assertFalse(steps.any { it.leftTextContent == "Txn time:" })
        assertTrue(steps.none { it.printType == "BAR_CODE" })
    }
}

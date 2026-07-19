package com.kspay.forwarder.kpay

import com.kspay.forwarder.crypto.Money
import com.kspay.forwarder.data.LocalTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TITLE = "KSPay"
private const val EMAIL = "support@kspay.com.au"
private const val ABN = "98642363853"
private const val DIVIDER = "------------------------------"

private val PAY_METHOD_LABELS = mapOf(
    1 to "Visa",
    2 to "Mastercard",
    3 to "China UnionPay",
    4 to "WeChat Pay",
    5 to "Alipay",
    6 to "American Express",
    7 to "Diners Club",
    8 to "JCB",
    9 to "UnionPay QuickPass",
    24 to "EFTPOS",
)

private val TRANSACTION_TYPE_LABELS = mapOf(
    1 to "Sale",
    2 to "Return",
    3 to "Reversal",
    4 to "Pre-Auth",
    5 to "Pre-Auth Completed",
    6 to "Cancellation",
    7 to "Tip Adjustment",
    8 to "Tip Cancellation",
    9 to "Pre-Auth Cancellation",
    10 to "Pre-Auth Completion Cancellation",
)

private val TXN_TIME_FORMAT = SimpleDateFormat("d/M/yyyy HH:mm:ss", Locale.US)

/**
 * Builds the /v2/pos/print step list for a passenger receipt, modeled on KPay's own printed
 * receipt (see BUILD_PROGRESS.md's 2026-07-19 receipt-printing entry for the source photos and
 * the field-availability decisions this was built against). Platform MID, Platform TID, Card SN,
 * and ATC are deliberately omitted -- KPay's API does not return them anywhere (confirmed against
 * the full documented includeReceipt field list), unlike every other line here.
 */
object ReceiptFormatter {

    fun buildSteps(transaction: LocalTransaction, result: QueryResponse, tid: String?): List<PrintStep> {
        val steps = mutableListOf<PrintStep>()

        steps += PrintStep.text(TITLE, size = "L", alignment = "CENTER")
        steps += PrintStep.text(EMAIL)
        steps += PrintStep.text("ABN:$ABN")
        result.kpayMerchantNo?.let { steps += PrintStep.text("MID:$it") }
        tid?.let { steps += PrintStep.text("TID:$it") }
        steps += PrintStep.text(DIVIDER)

        val scheme = PAY_METHOD_LABELS[result.payMethod] ?: "Card"
        val type = TRANSACTION_TYPE_LABELS[result.transactionType] ?: "Transaction"
        steps += PrintStep.text("$scheme $type", size = "L")

        // KPay's docs annotate these request fields e.g. "String (1,100)" -- a minimum length of
        // 1, not just a max. Sending an empty string for a null value violates that and appears
        // to crash this terminal's print handler (HTTP 500, reproduced live) rather than being
        // rejected cleanly -- so each of these is omitted entirely, never sent blank, whenever
        // the underlying field wasn't returned for this transaction (e.g. aid/aidLabel/tc are
        // EMV-chip fields KPay's docs note aren't populated for every card-entry method).
        result.cardNo?.let { steps += PrintStep.lrText("Card No:", formatCardNo(it, result.cardInputCode)) }
        result.aidLabel?.let { steps += PrintStep.lrText("APP label:", it) }
        result.aid?.let { steps += PrintStep.lrText("AID:", it) }
        result.refNo?.let { steps += PrintStep.lrText("Ref:", it) }
        result.tc?.let { steps += PrintStep.lrText("TC:", it) }
        result.authCode?.let { steps += PrintStep.lrText("Expiry: XX/XX", "ACode: $it") }
        if (result.batchNo != null || result.traceNo != null) {
            steps += PrintStep.lrText("Batch: ${result.batchNo.orEmpty()}", "Trace: ${result.traceNo.orEmpty()}")
        }
        result.commitTime?.let { steps += PrintStep.lrText("Txn time:", formatCommitTime(it)) }
        steps += PrintStep.text(DIVIDER)

        steps += PrintStep.lrText("BASE", "AUD " + Money.fromKpayCents(transaction.payAmountCents).setScale(2).toPlainString())
        steps += PrintStep.lrText("TIPS", "AUD " + amountOrZero(result.tipsAmount))
        steps += PrintStep.lrText("SURCHARGE", "AUD " + amountOrZero(result.surchargeAmount))
        steps += PrintStep.feed()
        steps += PrintStep.lrText("TOTAL", "AUD " + amountOrZero(result.orderAmount), size = "L")
        steps += PrintStep.text("*Total amount may include GST.")
        steps += PrintStep.text(DIVIDER)

        steps += PrintStep.text("NO SIGNATURE REQUIRED", size = "L")
        steps += PrintStep.text("I agree to pay the above total amount according to card issuer agreement.")
        steps += PrintStep.feed()
        steps += PrintStep.text("------Merchant Copy------", alignment = "CENTER")
        steps += PrintStep.text("PLEASE RETAIN RECEIPT", alignment = "CENTER")
        steps += PrintStep.text("FOR YOUR RECORDS", alignment = "CENTER")
        if (result.terminalType != null && result.appVersion != null) {
            steps += PrintStep.text("${result.terminalType} - ${result.appVersion}", alignment = "CENTER")
        }

        return steps
    }

    private fun formatCardNo(cardNo: String?, cardInputCode: String?): String {
        val no = cardNo.orEmpty()
        return if (cardInputCode != null) "$no($cardInputCode)" else no
    }

    private fun formatCommitTime(commitTime: Long): String = TXN_TIME_FORMAT.format(Date(commitTime))

    private fun amountOrZero(centString: String?): String =
        centString?.let { Money.fromKpayCents(it).setScale(2).toPlainString() } ?: "0.00"
}

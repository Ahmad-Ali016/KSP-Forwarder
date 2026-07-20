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
 * receipt (see BUILD_PROGRESS.md's 2026-07-19/2026-07-20 receipt-printing entries for the source
 * photos and the field decisions this was built against). Deliberately limited to what a
 * passenger receipt actually needs -- Platform MID, Platform TID, Card SN, and ATC are dropped
 * because KPay's API never returns them at all; AID, APP label, TC, ACode, Batch, and Trace are
 * dropped too (2026-07-20) since they're processing-detail fields a passenger doesn't need and
 * aren't always populated (e.g. some contactless taps). The terminal model/version footer
 * (terminalType/appVersion) is dropped too -- not useful to a passenger, as is the
 * "------Merchant Copy------" divider line (2026-07-20). `Trade:` (the forwarder's own
 * outTradeNo) is always printed alongside KPay's own `Ref/Tran. id:` so a transaction can be
 * looked up directly in the KSPay backend/admin console -- outTradeNo is the idempotency key
 * KSPay's own database stores the record under.
 */
object ReceiptFormatter {

    fun buildSteps(transaction: LocalTransaction, result: QueryResponse, tid: String?): List<PrintStep> {
        val steps = mutableListOf<PrintStep>()

        steps += PrintStep.text(TITLE, size = "L", alignment = "CENTER")
        steps += PrintStep.text(EMAIL)
        steps += PrintStep.text("ABN:$ABN")
        result.kpayMerchantNo?.let { steps += PrintStep.text("MID:$it") }
        // Always printed, even when null (renders literally as "TID:null") -- per the user's
        // explicit request, so the line's absence/presence itself signals whether KPay returned
        // a TID this time, rather than silently disappearing. See BUILD_PROGRESS.md's 2026-07-20
        // notes: KPay's /v2/pos/query/settlement response for this terminal has never included
        // the `extra` object (where kpayTerminalNo lives) in any capture this session, despite
        // TID visibly appearing on KPOS's own native settlement receipt -- likely a KPay-side gap
        // worth raising with their support, not a bug in this fetch.
        steps += PrintStep.text("TID:$tid")
        steps += PrintStep.text(DIVIDER)

        val scheme = PAY_METHOD_LABELS[result.payMethod] ?: "Card"
        val type = TRANSACTION_TYPE_LABELS[result.transactionType] ?: "Transaction"
        steps += PrintStep.text("$scheme $type", size = "L")

        // Only fields a passenger receipt actually needs -- aid/aidLabel/tc/authCode/batchNo/
        // traceNo (EMV/processing-detail fields, not always populated) are dropped entirely, not
        // just conditionally, per the 2026-07-20 decision to stop chasing them. Each remaining
        // line is still added conditionally, never sent blank: KPay's docs annotate print-request
        // fields e.g. "String (1,100)" -- a minimum length of 1, not just a max.
        result.cardNo?.let { steps += PrintStep.lrText("Card No:", formatCardNo(it, result.cardInputCode)) }
        result.refNo?.let { steps += PrintStep.lrText("Ref/Tran. id:", it) }
        steps += PrintStep.lrText("Trade:", transaction.outTradeNo)
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
        steps += PrintStep.text("PLEASE RETAIN RECEIPT", alignment = "CENTER")
        steps += PrintStep.text("FOR YOUR RECORDS", alignment = "CENTER")

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

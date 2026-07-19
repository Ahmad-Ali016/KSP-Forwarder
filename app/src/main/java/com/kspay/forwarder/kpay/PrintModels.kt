package com.kspay.forwarder.kpay

/** POST /v2/pos/print request body -- an ordered list of print instructions. */
data class PrintRequest(val steps: List<PrintStep>)

/**
 * One printer instruction. printType selects which fields apply (TEXT: textContent; LR_TEXT:
 * leftTextContent/rightTextContent; FEED: feedLine). Barcode/QR/image steps are not used by
 * ReceiptFormatter (no barcode on the receipt, per the 2026-07-19 decision) but the fields exist
 * to match KPay's documented request shape exactly.
 */
data class PrintStep(
    val printType: String,
    val textContent: String? = null,
    val leftTextContent: String? = null,
    val rightTextContent: String? = null,
    val textSize: String? = null,
    val alignment: String? = null,
    val qrcodeContent: String? = null,
    val barcodeContent: String? = null,
    val image: String? = null,
    val feedLine: Int? = null,
) {
    companion object {
        fun text(content: String, size: String? = null, alignment: String? = null) =
            PrintStep(printType = "TEXT", textContent = content, textSize = size, alignment = alignment)

        fun lrText(left: String, right: String, size: String? = null) =
            PrintStep(printType = "LR_TEXT", leftTextContent = left, rightTextContent = right, textSize = size)

        fun feed(lines: Int = 1) = PrintStep(printType = "FEED", feedLine = lines)
    }
}

package com.kspay.forwarder.kpay

/** POST /v2/pos/print request body -- an ordered list of print instructions. */
data class PrintRequest(val steps: List<PrintStep>)

/**
 * One printer instruction. printType selects which fields apply (TEXT: textContent; LR_TEXT:
 * leftTextContent/rightTextContent; FEED: feedLine). Barcode/QR/image steps are not used by
 * ReceiptFormatter (no barcode on the receipt, per the 2026-07-19 decision) but the fields exist
 * to match KPay's documented request shape exactly.
 *
 * `alignment`/`textSize`/`feedLine` are always sent, never left null -- confirmed live
 * (2026-07-20) that KPOS's own server unconditionally calls `PrintBody.Step.getAlignment()` and
 * `getTextSize()` while processing every step regardless of printType, and throws an unhandled
 * NullPointerException (crashing the request with an empty-body HTTP 500) if either is absent --
 * first alignment, then, once that was fixed, textSize on the very next request. The docs only
 * document these as "valid" for TEXT, but KPOS's own bug means every step needs explicit values
 * regardless. `feedLine` is defaulted defensively too, on the same suspicion (an int getter would
 * NPE identically if the underlying field is a boxed Integer).
 */
data class PrintStep(
    val printType: String,
    val textContent: String? = null,
    val leftTextContent: String? = null,
    val rightTextContent: String? = null,
    val textSize: String = "M",
    val alignment: String = "LEFT",
    val qrcodeContent: String? = null,
    val barcodeContent: String? = null,
    val image: String? = null,
    val feedLine: Int = 0,
) {
    companion object {
        fun text(content: String, size: String = "M", alignment: String = "LEFT") =
            PrintStep(printType = "TEXT", textContent = content, textSize = size, alignment = alignment)

        fun lrText(left: String, right: String, size: String = "M") =
            PrintStep(printType = "LR_TEXT", leftTextContent = left, rightTextContent = right, textSize = size)

        fun feed(lines: Int = 1) = PrintStep(printType = "FEED", feedLine = lines)
    }
}

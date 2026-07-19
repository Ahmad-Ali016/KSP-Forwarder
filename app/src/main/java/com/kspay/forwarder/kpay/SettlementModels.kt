package com.kspay.forwarder.kpay

private const val SUCCESS_CODE = 10000

/**
 * GET /v2/pos/query/settlement response shape -- distinct from KposEnvelope<T>, since the
 * response has a top-level `extra` object alongside `data` rather than the usual code/data
 * envelope. Deliberately minimal: only kpayTerminalNo (the value ReceiptFormatter needs) is
 * modeled -- Moshi's reflection adapter silently ignores every other undeclared JSON key
 * (the batch-totals `data` array, merchant names, etc.), so there is nothing to gain from
 * modeling fields nothing here reads.
 */
data class SettlementDataEnvelope(
    val code: Int,
    val extra: SettlementExtra?,
    val message: String?,
) {
    val isSuccess: Boolean get() = code == SUCCESS_CODE
}

data class SettlementExtra(val kpayTerminalNo: String?)

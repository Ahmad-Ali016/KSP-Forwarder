package com.kspay.forwarder.data

/** DRAFT -> SALE_SENT -> POLLING -> {SUCCEEDED | NON_SUCCESS} -> FORWARDING -> FORWARDED, or ABORTED via close. */
enum class TransactionState {
    DRAFT, SALE_SENT, POLLING, SUCCEEDED, NON_SUCCESS, FORWARDING, FORWARDED, ABORTED
}

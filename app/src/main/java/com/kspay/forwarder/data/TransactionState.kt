package com.kspay.forwarder.data

/**
 * DRAFT -> SALE_SENT -> POLLING -> {SUCCEEDED | NON_SUCCESS | ANOMALY} -> FORWARDING -> FORWARDED,
 * or ABORTED via close.
 *
 * ANOMALY: KPay reported payResult=2 (a successful charge) but the query result was missing
 * payAmount/orderAmount -- KSPay's ingest schema requires both on every request, so forwarding
 * this anyway would get a permanent 400 with no retry, silently losing visibility into a payment
 * that actually succeeded at the terminal. Held here instead for manual reconciliation; never
 * auto-forwarded (ForwardWorker/ReconciliationWorker only ever look for SUCCEEDED).
 */
enum class TransactionState {
    DRAFT, SALE_SENT, POLLING, SUCCEEDED, NON_SUCCESS, ANOMALY, FORWARDING, FORWARDED, ABORTED
}

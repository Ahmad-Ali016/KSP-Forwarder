package com.kspay.forwarder.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * KSPay backend ingest client.
 *
 * ⚠ UNCONFIRMED CONVENTION — "Idempotency-Key" header name: the KSPay backend doesn't exist
 * in this repo and isn't documented anywhere available here. The plan only specifies
 * "idempotency key = outTradeNo", not a header name. This uses the Stripe-style
 * "Idempotency-Key" header as a placeholder convention since we control both sides right now.
 * TODO: confirm the real header name against the KSPay backend once B3 (ingestion endpoint)
 * exists, and update this + KspayApiTest together if it differs.
 */
interface KspayApi {
    @POST("/api/v1/ingest/kpay/transactions/")
    suspend fun forward(
        @Body transaction: OutboundTransaction,
        @Header("X-Device-Token") deviceToken: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<Unit>
}

/** Derives the idempotency key from the transaction so callers can never pass a mismatched one. */
suspend fun KspayApi.forwardTransaction(transaction: OutboundTransaction, deviceToken: String): Response<Unit> =
    forward(transaction, deviceToken, idempotencyKey = transaction.outTradeNo)

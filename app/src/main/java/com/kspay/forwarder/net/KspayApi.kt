package com.kspay.forwarder.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * KSPay backend ingest client.
 *
 * "Idempotency-Key" header: confirmed with the KSPay backend team (2026-07-05) that their
 * ingest endpoint reads no idempotency header at all — dedupe is purely on the body's
 * outTradeNo. This header is a no-op on their side; kept anyway (costs nothing, and might
 * matter if their dedupe strategy ever changes) rather than removed.
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

package com.kspay.forwarder.net

import okhttp3.ResponseBody
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
 *
 * Returns the raw `ResponseBody` (not a Moshi-converted type) rather than `Response<Unit>` --
 * Retrofit passes `ResponseBody` straight through without invoking a converter, which matters
 * because their success response has a real JSON body but ForwardWorker's success check (2xx)
 * must keep working even when a response has no body at all (e.g. this codebase's own tests
 * predating the real contract). See `ForwardResponse` for how `ForwardWorker` optionally,
 * non-fatally parses `data.status` out of this body for local debug logging.
 */
interface KspayApi {
    @POST("/api/v1/ingest/kpay/transactions/")
    suspend fun forward(
        @Body transaction: OutboundTransaction,
        @Header("X-Device-Token") deviceToken: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ResponseBody>
}

/** Derives the idempotency key from the transaction so callers can never pass a mismatched one. */
suspend fun KspayApi.forwardTransaction(transaction: OutboundTransaction, deviceToken: String): Response<ResponseBody> =
    forward(transaction, deviceToken, idempotencyKey = transaction.outTradeNo)

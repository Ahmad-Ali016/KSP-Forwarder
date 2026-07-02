package com.kspay.forwarder.kpay

private const val SUCCESS_CODE = 10000

/**
 * Standard KPOS response wrapper: {code, data, message}. code == 10000 is success.
 * Parsed via Moshi's reflection-based KotlinJsonAdapterFactory (not codegen — generic
 * classes like this aren't codegen-compatible).
 */
data class KposEnvelope<T>(
    val code: Int,
    val data: T?,
    val message: String?,
) {
    val isSuccess: Boolean get() = code == SUCCESS_CODE
}

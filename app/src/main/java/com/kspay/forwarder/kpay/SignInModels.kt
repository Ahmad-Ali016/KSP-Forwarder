package com.kspay.forwarder.kpay

/** POST /v2/pos/sign body. Not signed — no working key exists yet at sign-in time. */
data class SignInRequest(
    val appId: String,
    val appSecret: String,
)

/**
 * POST /v2/pos/sign response data. Both keys are subject to KPay's known = escaping bug
 * (see KposKeyFixup) and must be cleaned before use.
 */
data class SignInResponse(
    val platformPublicKey: String,
    val appPrivateKey: String,
)

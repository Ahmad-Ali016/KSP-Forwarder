package com.kspay.forwarder.kpay

interface WorkingKeyStore {
    fun save(keys: SignInResponse)
    fun get(): SignInResponse?
    fun clear()
}

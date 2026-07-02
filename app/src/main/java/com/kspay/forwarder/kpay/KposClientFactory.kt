package com.kspay.forwarder.kpay

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object KposClientFactory {

    /** The real terminal's local API in 2-in-1 background mode. */
    const val DEFAULT_BASE_URL = "http://127.0.0.1:18080"

    fun create(baseUrl: String = DEFAULT_BASE_URL, client: OkHttpClient = OkHttpClient()): KposApi {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(KposApi::class.java)
    }
}

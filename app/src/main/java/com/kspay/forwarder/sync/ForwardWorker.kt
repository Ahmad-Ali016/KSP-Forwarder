package com.kspay.forwarder.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.net.ForwardResponse
import com.kspay.forwarder.net.KspayApi
import com.kspay.forwarder.net.OutboundTransactionMapper
import com.kspay.forwarder.net.forwardTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException

/** SUCCEEDED -> POST to KSPay -> FORWARDED. 5xx/network errors retry; other failures give up. */
class ForwardWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: TransactionRepository,
    private val api: KspayApi,
    private val appId: String,
    private val forwarderVersion: String,
    private val deviceToken: String,
) : CoroutineWorker(context, params) {

    private val forwardResponseAdapter =
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(ForwardResponse::class.java)

    override suspend fun doWork(): Result {
        val outTradeNo = inputData.getString(KEY_OUT_TRADE_NO) ?: return Result.failure()
        val transaction = repository.findByOutTradeNo(outTradeNo) ?: return Result.failure()
        if (transaction.state != TransactionState.SUCCEEDED) return Result.success()

        val outbound = OutboundTransactionMapper.map(transaction, appId, forwarderVersion)
        val response = try {
            api.forwardTransaction(outbound, deviceToken)
        } catch (e: IOException) {
            Log.w(TAG, "Forward network error for $outTradeNo: ${e.message}")
            return Result.retry()
        }

        return when {
            response.isSuccessful -> {
                logIfQuarantined(outTradeNo, response)
                repository.updateState(transaction, TransactionState.FORWARDED)
                Result.success()
            }
            response.code() in 500..599 -> Result.retry()
            else -> {
                Log.w(TAG, "Forward failed for $outTradeNo: code=${response.code()} body=${response.errorBody()?.string()}")
                Result.failure()
            }
        }
    }

    /**
     * KSPay confirmed the status code alone is a complete, correct success contract -- this is
     * purely a local debug signal (a stale device/driver mapping), never something to branch
     * forward logic on. Parsed defensively: a missing/malformed body must never fail the worker.
     */
    private fun logIfQuarantined(outTradeNo: String, response: Response<ResponseBody>) {
        val status = runCatching {
            forwardResponseAdapter.fromJson(response.body()?.string().orEmpty())?.data?.status
        }.getOrNull()
        if (status == "QUARANTINED") {
            Log.w(TAG, "Transaction $outTradeNo forwarded but KSPay marked it QUARANTINED -- check device/driver mapping")
        }
    }

    companion object {
        const val KEY_OUT_TRADE_NO = "out_trade_no"
        private const val TAG = "ForwardWorker"
    }
}

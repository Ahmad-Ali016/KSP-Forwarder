package com.kspay.forwarder.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState
import com.kspay.forwarder.net.KspayApi
import com.kspay.forwarder.net.OutboundTransactionMapper
import com.kspay.forwarder.net.forwardTransaction
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

    override suspend fun doWork(): Result {
        val outTradeNo = inputData.getString(KEY_OUT_TRADE_NO) ?: return Result.failure()
        val transaction = repository.findByOutTradeNo(outTradeNo) ?: return Result.failure()
        if (transaction.state != TransactionState.SUCCEEDED) return Result.success()

        val outbound = OutboundTransactionMapper.map(transaction, appId, forwarderVersion)
        val response = try {
            api.forwardTransaction(outbound, deviceToken)
        } catch (e: IOException) {
            return Result.retry()
        }

        return when {
            response.isSuccessful -> {
                repository.updateState(transaction, TransactionState.FORWARDED)
                Result.success()
            }
            response.code() in 500..599 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_OUT_TRADE_NO = "out_trade_no"
    }
}

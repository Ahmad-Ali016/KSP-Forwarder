package com.kspay.forwarder.net

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.kpay.QueryResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object OutboundTransactionMapper {

    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(QueryResponse::class.java)

    /**
     * @param tid The admin-entered terminal ID from TerminalInfoStore -- the driver-mapping key
     * on the backend (see BUILD_PROGRESS.md's 2026-07-20 "switch driver-mapping key" entry).
     * Null until an admin has set it via TidSettingsScreen.
     */
    fun map(transaction: LocalTransaction, appId: String, forwarderVersion: String, tid: String?): OutboundTransaction {
        val raw = requireNotNull(transaction.rawSaleResultJson) { "Cannot forward a transaction with no stored KPay result" }
        val result = requireNotNull(adapter.fromJson(raw)) { "Stored KPay result is not valid JSON" }

        return OutboundTransaction(
            outTradeNo = transaction.outTradeNo,
            kpayOutTradeNo = result.kpayOutTradeNo,
            transactionNo = result.transactionNo,
            refNo = result.refNo,
            deviceID = result.deviceID,
            kpayTerminalNo = tid,
            appId = appId,
            terminalType = result.terminalType,
            appVersion = result.appVersion,
            commitTime = result.commitTime,
            payAmount = result.payAmount,
            tipsAmount = result.tipsAmount,
            discountAmount = result.discountAmount,
            surchargeAmount = result.surchargeAmount,
            orderAmount = result.orderAmount,
            payCurrency = result.payCurrency,
            payResult = result.payResult,
            transactionType = result.transactionType,
            payMethod = result.payMethod,
            reason = result.reason,
            cardInputCode = result.cardInputCode,
            cardNo = result.cardNo,
            authCode = result.authCode,
            batchNo = result.batchNo,
            traceNo = result.traceNo,
            needSignature = result.needSignature,
            forwarderVersion = forwarderVersion,
            capturedAt = transaction.updatedAt,
            localId = transaction.id.toString(),
        )
    }
}

package com.kspay.forwarder.kpay

import com.kspay.forwarder.data.LocalTransaction
import com.kspay.forwarder.data.TransactionRepository
import com.kspay.forwarder.data.TransactionState

/** DRAFT -> POST /v2/pos/sales -> SALE_SENT. Throws on a non-10000 response. */
class SaleUseCase(
    private val api: KposApi,
    private val repository: TransactionRepository,
) {
    suspend fun execute(draft: LocalTransaction): LocalTransaction {
        val request = SaleRequest(
            outTradeNo = draft.outTradeNo,
            payAmount = draft.payAmountCents,
            payCurrency = draft.currency,
            paymentType = draft.paymentType,
        )
        val response = api.sale(request)
        check(response.isSuccess) { "Sale failed: code=${response.code} message=${response.message}" }
        return repository.updateState(draft, TransactionState.SALE_SENT)
    }
}

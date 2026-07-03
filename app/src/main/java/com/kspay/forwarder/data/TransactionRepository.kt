package com.kspay.forwarder.data

import kotlinx.coroutines.flow.Flow

/**
 * Owns timestamps and outTradeNo assignment so LocalTransaction stays a plain data holder.
 * Each state change is persisted here before the caller makes its next network call.
 */
class TransactionRepository(
    private val dao: LocalTransactionDao,
    private val outTradeNoGenerator: OutTradeNoGenerator,
) {
    /** Live updates as the sale/poll flow writes state transitions (SALE_SENT -> POLLING -> ...). */
    fun observe(outTradeNo: String): Flow<LocalTransaction?> = dao.observeByOutTradeNo(outTradeNo)

    /** Live, newest-first list of every transaction — backs the debug History screen. */
    fun observeAll(): Flow<List<LocalTransaction>> = dao.observeAll()

    suspend fun createDraft(payAmountCents: String, currency: String, paymentType: Int): LocalTransaction {
        val now = System.currentTimeMillis()
        val transaction = LocalTransaction(
            outTradeNo = outTradeNoGenerator.generate(),
            state = TransactionState.DRAFT,
            payAmountCents = payAmountCents,
            currency = currency,
            paymentType = paymentType,
            createdAt = now,
            updatedAt = now,
        )
        return transaction.copy(id = dao.insert(transaction))
    }

    suspend fun updateState(transaction: LocalTransaction, newState: TransactionState): LocalTransaction {
        val updated = transaction.copy(state = newState, updatedAt = System.currentTimeMillis())
        dao.update(updated)
        return updated
    }

    suspend fun findByOutTradeNo(outTradeNo: String): LocalTransaction? = dao.findByOutTradeNo(outTradeNo)

    suspend fun findByState(state: TransactionState): List<LocalTransaction> = dao.findByState(state)
}

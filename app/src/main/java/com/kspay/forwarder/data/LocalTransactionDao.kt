package com.kspay.forwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalTransactionDao {
    @Insert
    suspend fun insert(transaction: LocalTransaction): Long

    @Update
    suspend fun update(transaction: LocalTransaction)

    @Query("SELECT * FROM transactions WHERE outTradeNo = :outTradeNo")
    suspend fun findByOutTradeNo(outTradeNo: String): LocalTransaction?

    @Query("SELECT * FROM transactions WHERE state = :state")
    suspend fun findByState(state: TransactionState): List<LocalTransaction>

    @Query("SELECT * FROM transactions WHERE outTradeNo = :outTradeNo")
    fun observeByOutTradeNo(outTradeNo: String): Flow<LocalTransaction?>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<LocalTransaction>>
}

package com.kspay.forwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index(value = ["outTradeNo"], unique = true)])
data class LocalTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val outTradeNo: String,
    val state: TransactionState,
    val payAmountCents: String,
    val currency: String,
    val paymentType: Int,
    val rawSaleResultJson: String? = null,
    val forwardAttempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

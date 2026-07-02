package com.kspay.forwarder.data

import androidx.room.TypeConverter

class TransactionStateConverter {
    @TypeConverter
    fun fromState(state: TransactionState): String = state.name

    @TypeConverter
    fun toState(value: String): TransactionState = TransactionState.valueOf(value)
}

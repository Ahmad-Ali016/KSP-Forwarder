package com.kspay.forwarder.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [LocalTransaction::class], version = 1)
@TypeConverters(TransactionStateConverter::class)
abstract class ForwarderDatabase : RoomDatabase() {
    abstract fun localTransactionDao(): LocalTransactionDao
}

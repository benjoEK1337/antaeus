package io.pleo.antaeus.data

import io.pleo.antaeus.models.Lock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class LockDal(private val db: Database) {
    fun setLock(customerId: Int) {
        return transaction(db) {
            LockTable.insert {
                it[this.customerId] = customerId
            }
        }
    }

    fun releaseLock(customerId: Int) {
        return transaction(db) {
            LockTable.deleteWhere {
                LockTable.customerId eq customerId
            }
        }
    }
    fun getLock(customerId: Int): Lock? {
        return transaction(db) {
            LockTable
                .select { LockTable.customerId.eq(customerId) }
                .firstOrNull()
                ?.toLock()
        }
    }
}
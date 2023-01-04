/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesByStatus(statuses: Set<InvoiceStatus>): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .select { InvoiceTable.status.inList(statuses.map { it.name }) }
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun updateInvoiceCurrency(id: Int, currency: Currency): Int {
        return transaction(db) {
            InvoiceTable.update({ InvoiceTable.id eq id }) {
                it[InvoiceTable.currency] = currency.name
            }
        }
    }

    fun updateInvoice(invoice: Invoice): Int {
        return transaction(db) {
            InvoiceTable.update({ InvoiceTable.id eq invoice.id }) {
                it[this.customerId] = invoice.customerId
                it[this.currency] = invoice.amount.currency.name
                it[this.value] = invoice.amount.value
                it[this.status] = invoice.status.name
            }
        }
    }

    fun updateInvoiceStatus(id: Int, status: InvoiceStatus): Int {
        return transaction(db) {
            InvoiceTable.update({ InvoiceTable.id eq id }) {
                it[InvoiceTable.status] = status.name
            }
        }
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }

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

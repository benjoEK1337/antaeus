package io.pleo.antaeus.data

import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class InvoiceDal(private val db: Database) {

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

    fun fetchInvoicesByStatuses(statuses: Set<InvoiceStatus>): List<Invoice> {
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
}
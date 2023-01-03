/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

class InvoiceService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetchInvoicesByStatus(statuses: Set<InvoiceStatus>): List<Invoice> {
        return dal.fetchInvoicesByStatus(statuses)
    }

    fun updateInvoiceStatus(id: Int, status: InvoiceStatus): Int {
        return dal.updateInvoiceStatus(id, status)
    }
    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }
}

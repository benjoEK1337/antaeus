/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.InvoiceDal
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import mu.KotlinLogging

class InvoiceService(
    private val customerService: CustomerService,
    private val invoiceDal: InvoiceDal
) {

    private val logger = KotlinLogging.logger {}

    fun fetchAll(): List<Invoice> {
        return invoiceDal.fetchInvoices()
    }

    fun fetchInvoicesByStatuses(statuses: Set<InvoiceStatus>): List<Invoice> {
        return invoiceDal.fetchInvoicesByStatuses(statuses)
    }

    fun updateInvoiceStatus(id: Int, status: InvoiceStatus): Int {
        return invoiceDal.updateInvoiceStatus(id, status)
    }

    fun fetch(id: Int): Invoice {
        return invoiceDal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus): Invoice? {
        return invoiceDal.createInvoice(amount, customer, status)
    }

    fun handleCurrencyMismatchException(invoice: Invoice) {
        try {
            val customer = customerService.fetch(invoice.customerId)
            val updatedMoney = invoice.amount.copy(value = invoice.amount.value, currency = customer.currency)
            val updatedInvoice = invoice.copy(amount = updatedMoney, status = InvoiceStatus.FAILED)
            invoiceDal.updateInvoice(updatedInvoice)
        } catch (ex: Exception) {
            logger.error("Error occurred while handling CurrencyMismatchException for invoice ${invoice.id} and customer ${invoice.customerId}. Exception message: ${ex.localizedMessage}")
            invoiceDal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        }
    }
}

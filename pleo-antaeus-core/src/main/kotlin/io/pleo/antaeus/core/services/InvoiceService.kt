/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class InvoiceService(private val dal: AntaeusDal) {

    private val logger = KotlinLogging.logger {}

    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetchInvoicesByStatuses(statuses: Set<InvoiceStatus>): List<Invoice> {
        return dal.fetchInvoicesByStatuses(statuses)
    }

    fun updateInvoiceStatus(id: Int, status: InvoiceStatus): Int {
        return dal.updateInvoiceStatus(id, status)
    }
    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun handleCurrencyMismatchException(invoice: Invoice) {
        try {
            val customer = dal.fetchCustomer(invoice.customerId)

            if (customer == null) {
                // TODO Create alert for log below
                logger.error("Customer with ${invoice.customerId} ID provided in the invoice wasn't found in the customer table.")
                return
            }

            if (customer.currency.name == invoice.amount.currency.name) {
                // TODO Create alert for log below
                logger.error("The Currency in the invoice and customers one are same. There is an issue with the payment provider")
                return
            }

            val updatedMoney = invoice.amount.copy(value = invoice.amount.value, currency = customer.currency)
            val updatedInvoice = invoice.copy(amount = updatedMoney, status = InvoiceStatus.FAILED)
            dal.updateInvoice(updatedInvoice)

        } catch (ex: Exception) {
            // The invoice will be charged in the next iteration after we set status to FAILED
            dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        }
    }
}

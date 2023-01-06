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
                logger.error("Customer ${invoice.customerId} provided in the invoice ${invoice.id} wasn't found in the customer table.")
                return
            }

            val updatedMoney = invoice.amount.copy(value = invoice.amount.value, currency = customer.currency)
            val updatedInvoice = invoice.copy(amount = updatedMoney, status = InvoiceStatus.FAILED)
            dal.updateInvoice(updatedInvoice)

        } catch (ex: Exception) {
            logger.error("Error occurred while handling CurrencyMismatchException for invoice ${invoice.id} and customer ${invoice.customerId}. Exception message: ${ex.localizedMessage}")
            dal.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
        }
    }
}

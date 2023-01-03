package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.models.Invoice
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService
) {
    private val logger = KotlinLogging.logger {}

    // Since this is charging we don't need extra speed by making this non-blocking, async and simple
    fun chargeCustomersPendingInvoices() {
        val pendingInvoices = invoiceService.fetchPendingInvoices()
        pendingInvoices.forEach {
            chargeSingleCustomerInvoice(it)
            customerService.notifyCustomerInvoiceIsCharged(it.customerId)
        }
    }
    
    private fun chargeSingleCustomerInvoice(invoice: Invoice) {
        try {
            paymentProvider.charge(invoice)
        } catch (ex: Exception) {
            handleChargingExceptions(ex)
        }
    }
    
    private fun handleChargingExceptions(ex: Exception) {
        when (ex) {
            is CustomerNotFoundException -> {}
            is CurrencyMismatchException -> {}
            is NetworkException -> {}
            else -> {}
        }
    }
}

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.exceptions.ExternalServiceNotAvailableException
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.core.utils.retry
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService
) {

    private val logger = KotlinLogging.logger {}
    private var numberOfNetworkFailedChargings = 0;

    // Since this is charging we don't need extra speed by making this non-blocking, async and simple
    fun chargeCustomersPendingInvoices() {
        val pendingInvoices = invoiceService.fetchInvoicesByStatus(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED))
        pendingInvoices.forEach {
            chargeSingleCustomerInvoice(it)
            customerService.notifyCustomerInvoiceIsCharged(it.customerId)
        }
    }
    
    private fun chargeSingleCustomerInvoice(invoice: Invoice) {
        try {
            paymentProvider.charge(invoice)
        } catch (ex: Exception) {
            handleChargingExceptions(ex, invoice)
        }
    }
    
    private fun handleChargingExceptions(ex: Exception, invoice: Invoice) {
        when (ex) {
            is CustomerNotFoundException -> {}
            is CurrencyMismatchException -> {}
            is NetworkException -> handleNetworkException(invoice)
            else -> {}
        }
    }

    private fun handleNetworkException(invoice: Invoice) {
        try {
            retry {
                paymentProvider.charge(invoice)
            }
        } catch (ex: Exception) {

            if (ex is ExternalServiceNotAvailableException) {
                invoiceService.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)

                numberOfNetworkFailedChargings++
                logger.warn("Payment provider is currently unavailable. In the current charging iteration there are $numberOfNetworkFailedChargings failed chargings due to unavailability.")
                return
            }

            handleChargingExceptions(ex, invoice)
        }
    }
}

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
    private var numberOfNetworkFailedChargings = 0

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
            val isCustomerCharged = paymentProvider.charge(invoice)
            handleCustomerChargeResponse(invoice, isCustomerCharged)
        } catch (ex: Exception) {
            handleChargingExceptions(ex, invoice)
        }
    }

    private fun handleCustomerChargeResponse(invoice: Invoice, isCustomerCharged: Boolean) {
        if (isCustomerCharged) {
            handleSuccessfulCharge(invoice)
            return
        }
        handleFailedCharge(invoice)
    }

    private fun handleSuccessfulCharge(invoice: Invoice) {
        invoiceService.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
        logger.info("Customer with $invoice.id ID is successfully charged for monthly expenses")
        return
    }

    private fun handleFailedCharge(invoice: Invoice) {

        if (invoice.status == InvoiceStatus.PENDING) {
            invoiceService.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
            customerService.notifyCustomerToCheckTheirAccountBalance(invoice.customerId)
        }

        logger.error("Monthly charge for customer with ${invoice.customerId} ID wasn't processed due to account balance issues")
    }

    private fun handleChargingExceptions(ex: Exception, invoice: Invoice) {
        when (ex) {
            is CustomerNotFoundException -> customerService.handleCustomerNotFoundException(invoice.customerId)
            is CurrencyMismatchException -> invoiceService.handleCurrencyMismatchException(invoice)
            is NetworkException -> handleNetworkException(invoice)
            else -> {}
        }
    }

    private fun handleNetworkException(invoice: Invoice) {
        try {
            val isCustomerCharged = retry {
                paymentProvider.charge(invoice)
            }
            handleCustomerChargeResponse(invoice, isCustomerCharged)
        } catch (ex: Exception) {

            if (ex is ExternalServiceNotAvailableException) {
                numberOfNetworkFailedChargings++
                logger.warn("Payment provider is currently unavailable. In the current charging iteration there are $numberOfNetworkFailedChargings failed chargings due to unavailability.")
                return
            }

            handleChargingExceptions(ex, invoice)
        }
    }
}

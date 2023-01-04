package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.core.utils.retry
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val lockingService: LockingService
) {

    private val logger = KotlinLogging.logger {}
    private var numberOfNetworkFailedChargings = 0

    fun chargeCustomersPendingInvoices() {
        val pendingInvoices = invoiceService.fetchInvoicesByStatus(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED))

        pendingInvoices.forEach {
                chargeSingleCustomerInvoice(it)
            }
    }
    
    private fun chargeSingleCustomerInvoice(invoice: Invoice) {
        try {
            // By putting lock on the customerId we assure that only one charging will be executed in the cluster
            if (lockingService.getLock(invoice.customerId) == null) {

                lockingService.setLock(invoice.customerId)

                val isCustomerCharged = paymentProvider.charge(invoice)
                handleCustomerChargeResponse(invoice, isCustomerCharged)

                lockingService.releaseLock(invoice.customerId)
            }
        } catch (ex: Exception) {
            handleChargingExceptions(ex, invoice)
            lockingService.releaseLock(invoice.customerId)
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
        customerService.notifyCustomerInvoiceIsCharged(invoice.customerId)
        logger.info("Customer with ${invoice.id} ID is successfully charged for monthly expenses")
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
            is LockException -> lockingService.handleLockException(invoice.customerId)
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

            // The NetworkException will never be thrown here which is assured in the retry function - by that we avoid infinite loop
            handleChargingExceptions(ex, invoice)
        }
    }
}

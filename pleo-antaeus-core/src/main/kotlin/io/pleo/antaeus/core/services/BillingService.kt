package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.core.utils.retry
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.time.LocalDate

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val lockingService: LockingService
) {

    private val logger = KotlinLogging.logger {}
    private var numberOfNetworkFailedChargings = 0

    fun chargeCustomersInvoices() {
        val invoicesToCharge = invoiceService.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED))

        invoicesToCharge.forEach {
                chargeSingleCustomerInvoice(it)
            }
    }

    fun checkIfFailedOrPendingInvoicesExist() {
        val invoicesToCharge = invoiceService.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED))

        if (invoicesToCharge.isNotEmpty()) {
            val yesterdayDate = LocalDate.now().minusDays(1)

            // TODO Create alert
            logger.error("There are ${invoicesToCharge.size} uncharged invoices after charging iteration on $yesterdayDate")
        }
    }

    private fun chargeSingleCustomerInvoice(invoice: Invoice) {
        try {
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
        customerService.notifyCustomerInvoiceIsCharged(invoice.customerId)

        try {
            retry(10, 10) {
                invoiceService.updateInvoiceStatus(invoice.id, InvoiceStatus.PAID)
            }
            logger.info("Customer with ${invoice.id} ID is successfully charged for monthly expenses")
        } catch (ex: Exception) {
            // TODO High priority alert. The customer is charged, but the invoice table isn't update. Customer could be charged twice.
            logger.error("Updating invoice status with ID ${invoice.id} status failed due to Database error.")
        }
    }

    private fun handleFailedCharge(invoice: Invoice) {

        if (invoice.status == InvoiceStatus.PENDING) {
            invoiceService.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
            customerService.notifyCustomerToCheckTheirAccountBalance(invoice.customerId)
        }

        logger.warn("Monthly charge for customer with ${invoice.customerId} ID wasn't processed due to account balance issues")
    }

    private fun handleChargingExceptions(ex: Exception, invoice: Invoice) {
        when (ex) {
            is CustomerNotFoundException -> {
                customerService.handleCustomerNotFoundException(invoice.customerId)
                invoiceService.updateInvoiceStatus(invoice.id, InvoiceStatus.FAILED)
            }
            is CurrencyMismatchException -> invoiceService.handleCurrencyMismatchException(invoice)
            is NetworkException -> handleNetworkException(invoice)
            is LockException -> lockingService.handleLockException(invoice.customerId)
            else -> logger.warn("Unknown error occurred during charging. Exception message: ${ex.localizedMessage}")
        }
    }

    private fun handleNetworkException(invoice: Invoice) {
        try {
            val isCustomerCharged = retry {
                paymentProvider.charge(invoice)
            }
            handleCustomerChargeResponse(invoice, isCustomerCharged)
        } catch (ex: Exception) {
            handlePaymentProviderExceptionAfterRetry(ex, invoice)
        }
    }

    private fun handlePaymentProviderExceptionAfterRetry(ex: Exception, invoice: Invoice) {
        if (ex is ExternalServiceNotAvailableException) {
            handleExternalServiceNotAvailableException(invoice.id)
            return
        }
        handleChargingExceptions(ex, invoice)
    }

    private fun handleExternalServiceNotAvailableException(invoiceId: Int) {
        invoiceService.updateInvoiceStatus(invoiceId, InvoiceStatus.FAILED)
        numberOfNetworkFailedChargings++
        logger.warn("Payment provider is currently unavailable. In the current charging iteration there are $numberOfNetworkFailedChargings failed chargings due to unavailability.")
    }
}

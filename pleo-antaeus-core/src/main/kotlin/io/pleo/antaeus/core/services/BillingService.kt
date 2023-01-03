package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import mu.KotlinLogging

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService
) {
    private val logger = KotlinLogging.logger {}
    fun chargeMonthlyInvoices() {
        logger.info("Testing")
    }
}

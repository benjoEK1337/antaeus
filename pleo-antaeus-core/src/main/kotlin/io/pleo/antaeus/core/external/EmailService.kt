package io.pleo.antaeus.core.external

import io.pleo.antaeus.core.external.definition.NotificationProvider
import mu.KotlinLogging

class EmailService: NotificationProvider {
    private val logger = KotlinLogging.logger {}

    fun notifyCustomerInvoiceIsCharged(customerId: Int) {
        // TODO - Get Customer data and provide it to the message
        send("Hello customer. Just a message that we've charged you for previous month. Thank you for choosing us!")
    }

    override fun send(message: String) {
    // TODO - send email using email provider
    }
}
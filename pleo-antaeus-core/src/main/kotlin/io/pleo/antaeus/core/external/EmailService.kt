package io.pleo.antaeus.core.external

import io.pleo.antaeus.core.external.definition.NotificationProvider
import mu.KotlinLogging

class EmailService: NotificationProvider {
    private val logger = KotlinLogging.logger {}

    fun notifyCustomerInvoiceIsCharged(customerId: Int) {
        // TODO - Get Customer data and provide it to the message
        send("Hello customer. Just a message that we've charged you for previous month expenses. Thank you for choosing us!")
    }

    fun notifyCustomerToCheckTheirAccountBalance(customerId: Int) {
        // TODO - Get Customer data and provide it to the message
        send("Hello customer. We've tried to charge you for previous month expenses, but couldn't due to the account balance issues. + " +
                "Please update  your account so you can happily use your account. ")
    }


    override fun send(message: String) {
    // TODO - send email using email provider
    }
}
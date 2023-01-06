/*
    Implements endpoints related to customers.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.EmailService
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Customer
import mu.KotlinLogging

class CustomerService(
    private val dal: AntaeusDal,
    private val emailService: EmailService
    ) {

    private val logger = KotlinLogging.logger {}

    fun fetchAll(): List<Customer> {
        return dal.fetchCustomers()
    }

    fun fetch(id: Int): Customer {
        return dal.fetchCustomer(id) ?: throw CustomerNotFoundException(id)
    }

    fun handleCustomerNotFoundException(customerId: Int) {
        // TODO Create the P2 alert on the log bellow which will send slack/email message
        logger.error("Customer with $customerId ID wasn't found in the payment provider.")
    }

    fun notifyCustomerInvoiceIsCharged(customerId: Int) {
        emailService.notifyCustomerInvoiceIsCharged(customerId)
    }

    fun notifyCustomerToCheckTheirAccountBalance(customerId: Int) {
        emailService.notifyCustomerToCheckTheirAccountBalance(customerId)
    }

    fun notifyCustomerMultipleFailedChargingsDueToAccountBalance(customerId: Int) {
        emailService.notifyCustomerMultipleFailedChargingsDueToAccountBalance(customerId)
    }
}

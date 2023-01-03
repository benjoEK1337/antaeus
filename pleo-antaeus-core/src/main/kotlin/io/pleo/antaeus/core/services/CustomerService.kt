/*
    Implements endpoints related to customers.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.EmailService
import io.pleo.antaeus.core.external.definition.NotificationProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Customer

class CustomerService(
    private val dal: AntaeusDal,
    private val emailService: EmailService
    ) {
    fun fetchAll(): List<Customer> {
        return dal.fetchCustomers()
    }

    fun fetch(id: Int): Customer {
        return dal.fetchCustomer(id) ?: throw CustomerNotFoundException(id)
    }

    fun notifyCustomerInvoiceIsCharged(customerId: Int) {
        emailService.notifyCustomerInvoiceIsCharged(customerId)
    }

    fun notifyCustomerToCheckTheirAccountBalance(customerId: Int) {
        emailService.notifyCustomerToCheckTheirAccountBalance(customerId)
    }
}

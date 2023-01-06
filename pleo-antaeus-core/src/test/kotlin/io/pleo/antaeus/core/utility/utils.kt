package io.pleo.antaeus.core.utility

import io.pleo.antaeus.models.*
import java.math.BigDecimal
import kotlin.random.Random

internal fun mockInvoicesData(customersAndInvoiceNumber: Int): List<Invoice> {
    val customers = (1..customersAndInvoiceNumber).mapNotNull {
        Customer(id = it, currency = Currency.values()[Random.nextInt(0, Currency.values().size)])
    }

   return customers.map { customer ->
            Invoice(
                id = customer.id,
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                status = if (Random.nextBoolean()) InvoiceStatus.PENDING else InvoiceStatus.FAILED,
                customerId = customer.id
            )
    }
}

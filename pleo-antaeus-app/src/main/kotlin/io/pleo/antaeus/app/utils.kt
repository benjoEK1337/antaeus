
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import java.math.BigDecimal
import kotlin.random.Random

// This will create all schemas and setup initial data
internal fun setupInitialData(customerService: CustomerService,
                              invoiceService: InvoiceService) {
    val customers = (1..100).mapNotNull {
        customerService.createCustomer(
            currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
        )
    }
    customers.forEach { customer ->
        (1..10).forEach {
            invoiceService.createInvoice(
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                customer = customer,
                status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
            )
        }
    }
}

// This is the mocked instance of the payment provider
// Simulating a real provider where customer will be successfully charged
internal fun getPaymentProvider(): PaymentProvider {
    return object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            return when ((0..1000).random()) {
                in (0 until 5)  -> throw CustomerNotFoundException(invoice.customerId)
                in (5 until 10) -> throw CurrencyMismatchException(invoice.id, invoice.customerId)
                in (10 until 15) -> throw NetworkException()
                in (15 until 20) -> false
                else -> true
            }
        }
    }
}

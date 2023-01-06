package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.InvoiceDal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InvoiceServiceTest {
    private val invoiceDal = mockk<InvoiceDal> {
        every { fetchInvoice(404) } returns null
    }

    private val customerServiceMock = mockk<CustomerService>()

    private val invoiceService = InvoiceService(customerServiceMock, invoiceDal)

    @Test
    fun `will throw if invoice is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }
}

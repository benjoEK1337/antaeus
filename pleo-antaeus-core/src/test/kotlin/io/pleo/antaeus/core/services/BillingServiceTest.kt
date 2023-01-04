package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.core.utility.mockInvoicesData
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BillingServiceTest {
    private val dalMock = mockk<AntaeusDal>()
    private val paymentProviderMock = mockk<PaymentProvider>()
    private val invoiceServiceMock = mockk<InvoiceService>()
    private val customerServiceMock = mockk<CustomerService>()
    private val lockingServiceMock = mockk<LockingService>()

    private val billingService = BillingService(paymentProviderMock, invoiceServiceMock, customerServiceMock, lockingServiceMock)

    @BeforeEach
    fun setUp() {
        prepareLockMocks()
    }

    @Test
    fun `chargeCustomersPendingInvoices should charge every customer successfully`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 5)
        every { invoiceServiceMock.fetchInvoicesByStatus(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        every { paymentProviderMock.charge(any()) } returns true
        every { invoiceServiceMock.updateInvoiceStatus(any(), any()) } returns Random.nextInt()
        every { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) } just Runs

        billingService.chargeCustomersPendingInvoices()

        verifyLocksAreExecutedCorrectly(numberOfLocks = 5)
        verify(exactly = 5) { invoiceServiceMock.updateInvoiceStatus(any(), any()) }
        verify(exactly = 5) { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) }
    }

    private fun prepareLockMocks() {
        every { lockingServiceMock.getLock(any()) } returns null
        every { lockingServiceMock.setLock(any()) } answers { nothing }
        every { lockingServiceMock.releaseLock(any()) } answers { nothing }
    }

    private fun verifyLocksAreExecutedCorrectly(numberOfLocks: Int) {
        verify(exactly = numberOfLocks) { lockingServiceMock.getLock(any()) }
        verify(exactly = numberOfLocks) { lockingServiceMock.setLock(any()) }
        verify(exactly = numberOfLocks) { lockingServiceMock.releaseLock(any()) }
    }
}
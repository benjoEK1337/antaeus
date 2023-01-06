package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.ExternalServiceNotAvailableException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.definition.PaymentProvider
import io.pleo.antaeus.core.utility.mockInvoicesData
import io.pleo.antaeus.core.utils.retry
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Lock
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BillingServiceTest {
    private val paymentProviderMock = mockk<PaymentProvider>()
    private val invoiceServiceMock = mockk<InvoiceService>()
    private val customerServiceMock = mockk<CustomerService>()
    private val lockingServiceMock = mockk<LockingService>()

    private val billingService = BillingService(paymentProviderMock, invoiceServiceMock, customerServiceMock, lockingServiceMock)

    @Test
    fun `chargeCustomersPendingInvoices should charge every customer successfully and invoices should be marked as PAID`() {
        prepareSuccessfulLockMocks()
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 5)
        prepareSuccessfulServicesMocks(mockedInvoices)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        every { paymentProviderMock.charge(any()) } returns true
        every { invoiceServiceMock.updateInvoiceStatus(any(), any()) } returns Random.nextInt()
        every { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) } just Runs
        mockRetryTopLevelFunction(returnValue = true, exceptionToThrow = null)

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 5, setLock = 5, releaseLock = 5)
        verify(exactly = 5) { retry<Boolean>(any(), any(), any())  }
        verify(exactly = 5) { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) }
    }

    @Test
    fun `chargeCustomersPendingInvoices should not charge first two customers because lock exists on them`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 5)
        prepareSuccessfulServicesMocks(mockedInvoices)
        every { lockingServiceMock.getLock(1) } returns Lock(id = 1, customerId = 1)
        every { lockingServiceMock.getLock(2) } returns Lock(id = 2, customerId = 2)
        every { lockingServiceMock.getLock(range(3, 5)) } answers { null }

        mockRetryTopLevelFunction(returnValue = true, exceptionToThrow = null)
        every { lockingServiceMock.setLock(any()) } answers { nothing }
        every { lockingServiceMock.releaseLock(any()) } answers { nothing }

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 5, setLock = 3, releaseLock = 3)
        verify(exactly = 3) { retry<Boolean>(any(), any(), any())  }
        verify(exactly = 3) { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) }
    }

    @Test
    fun `when CustomerNotFoundException occurs billingService should call customerService to handle the exception`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 1)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        prepareSuccessfulLockMocks()

        every { paymentProviderMock.charge(any()) } throws CustomerNotFoundException(1)

        every { customerServiceMock.handleCustomerNotFoundException(1) } answers { nothing }

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 1, setLock = 1, releaseLock = 1)
        verify(exactly = 1) { customerServiceMock.handleCustomerNotFoundException(1) }
    }

    @Test
    fun `when CurrencyMismatchException occurs billingService should call retry mechanism to handle the exception`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 1)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        prepareSuccessfulLockMocks()

        every { paymentProviderMock.charge(any()) } throws CurrencyMismatchException(1, 1)
        every { invoiceServiceMock.handleCurrencyMismatchException(any()) } answers { nothing }

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 1, setLock = 1, releaseLock = 1)
        verify(exactly = 1) { invoiceServiceMock.handleCurrencyMismatchException(any()) }
    }

    @Test
    fun `billingService should properly handle NetworkException by calling retry mechanism which successfully charges`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 1)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        prepareSuccessfulLockMocks()

        every { paymentProviderMock.charge(any()) } throws NetworkException()

        mockRetryTopLevelFunction(returnValue = true, exceptionToThrow = null);

        every { invoiceServiceMock.updateInvoiceStatus(any(), any()) } returns Random.nextInt()
        every { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) } just Runs

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 1, setLock = 1, releaseLock = 1)
        verify(exactly = 2) { retry<Boolean>(any(), any(), any())  }
        verify(exactly = 1) { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) }
    }

    @Test
    fun `billingService should properly call handle CustomerNotFoundException thrown by payment provider in retry`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 1)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        prepareSuccessfulLockMocks()

        every { paymentProviderMock.charge(any()) } throws NetworkException()

        mockRetryTopLevelFunction(returnValue = null, exceptionToThrow = CustomerNotFoundException(1));

        every { customerServiceMock.handleCustomerNotFoundException(1) } answers { nothing }

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 1, setLock = 1, releaseLock = 1)
        verify(exactly = 1) { retry<Boolean>(any(), any(), any())  }
        verify(exactly = 1) { customerServiceMock.handleCustomerNotFoundException(1) }
    }


    @Test
    fun `billingService should properly call handle CurrencyMismatchException thrown by payment provider in retry`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 1)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        prepareSuccessfulLockMocks()

        every { paymentProviderMock.charge(any()) } throws NetworkException()

        mockRetryTopLevelFunction(returnValue = null, exceptionToThrow = CurrencyMismatchException(1, 1));

        every { invoiceServiceMock.handleCurrencyMismatchException(any()) } answers { nothing }

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 1, setLock = 1, releaseLock = 1)
        verify(exactly = 1) { retry<Boolean>(any(), any(), any())  }
        verify(exactly = 1) { invoiceServiceMock.handleCurrencyMismatchException(any()) }
    }

    @Test
    fun `billingService skip charging when ExternalServiceNotAvailableException is thrown by retry`() {
        val mockedInvoices = mockInvoicesData(customersAndInvoiceNumber = 1)
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        prepareSuccessfulLockMocks()

        every { paymentProviderMock.charge(any()) } throws NetworkException()

        mockRetryTopLevelFunction(returnValue = null, exceptionToThrow = ExternalServiceNotAvailableException());

        billingService.chargeCustomersInvoices()

        verifyLocksAreExecutedCorrectly(getLock = 1, setLock = 1, releaseLock = 1)
        verify(exactly = 1) { retry<Boolean>(any(), any(), any())  }
        verify(exactly = 0) { retry(any(), any()) { invoiceServiceMock.updateInvoiceStatus(any(), any()) } }
        verify(exactly = 0) { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) }
    }

    private fun prepareSuccessfulLockMocks() {
        every { lockingServiceMock.getLock(any()) } returns null
        every { lockingServiceMock.setLock(any()) } answers { nothing }
        every { lockingServiceMock.releaseLock(any()) } answers { nothing }
    }

    private fun prepareSuccessfulServicesMocks(mockedInvoices: List<Invoice>) {
        every { invoiceServiceMock.fetchInvoicesByStatuses(setOf(InvoiceStatus.PENDING, InvoiceStatus.FAILED)) } returns mockedInvoices
        every { paymentProviderMock.charge(any()) } returns true
        every { invoiceServiceMock.updateInvoiceStatus(any(), any()) } returns Random.nextInt()
        every { customerServiceMock.notifyCustomerInvoiceIsCharged(any()) } just Runs
    }

    private fun verifyLocksAreExecutedCorrectly(getLock: Int, setLock: Int, releaseLock: Int) {
        verify(exactly = getLock) { lockingServiceMock.getLock(any()) }
        verify(exactly = setLock) { lockingServiceMock.setLock(any()) }
        verify(exactly = releaseLock) { lockingServiceMock.releaseLock(any()) }
    }

    private fun<T> mockRetryTopLevelFunction(returnValue: T, exceptionToThrow: Exception?) {
        mockkStatic("io.pleo.antaeus.core.utils.ResiliencyKt")

        if (returnValue != null) {
            every { retry<T>(any(), any(), any()) } returns returnValue
            return
        }

        every { retry<T>(any(), any(), any()) } throws exceptionToThrow!!
    }
}

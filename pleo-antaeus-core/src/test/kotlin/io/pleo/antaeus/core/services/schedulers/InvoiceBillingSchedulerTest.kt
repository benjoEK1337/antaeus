package io.pleo.antaeus.core.services.schedulers

import io.mockk.*
import io.pleo.antaeus.core.schedulers.InvoiceBillingScheduler
import io.pleo.antaeus.core.services.BillingService
import org.junit.jupiter.api.Test
import java.time.LocalDateTime


/*
  - The tests could go more deeply in the scheduler, but don't have time to investigate mockk so deeply
 */
class InvoiceBillingSchedulerTest {
    private val billingServiceMock = mockk<BillingService>()
    private val invoiceBillingSchedulerMock = spyk(InvoiceBillingScheduler(billingServiceMock), recordPrivateCalls = true)

    @Test
    fun `on the every first of the month scheduler should charge in multiple iterations based on the set delay time` () {
        val mockedLocalDateTime = LocalDateTime.now().withDayOfMonth(1).withHour(1)
        mockkStatic(LocalDateTime::class)

        every { invoiceBillingSchedulerMock["getDelay"]() } returns 5000L
        every { LocalDateTime.now() } answers { mockedLocalDateTime }

        every { billingServiceMock.chargeCustomersInvoices() } just Runs
        invoiceBillingSchedulerMock.schedule()

        // To wait for the second scheduling
        Thread.sleep(12000)

        // By this we verify the scheduler is scheduled for another execution
        verify(exactly = 2) { LocalDateTime.now() }
        verify(exactly = 1) { billingServiceMock.chargeCustomersInvoices() }
        unmockkStatic(LocalDateTime::class)
    }

    @Test
    fun `scheduler should call checkIfFailedOrPendingInvoicesExist on every second day of month` () {
        val mockedLocalDateTime = LocalDateTime.now().withDayOfMonth(2).withHour(1)
        mockkStatic(LocalDateTime::class)

        every { LocalDateTime.now() } answers { mockedLocalDateTime }

        invoiceBillingSchedulerMock.schedule()

        verify(exactly = 1) { LocalDateTime.now() }
        verify(exactly = 0) { billingServiceMock.chargeCustomersInvoices() }
        verify(exactly = 1) { billingServiceMock.checkIfFailedOrPendingInvoicesExist() }

        unmockkStatic(LocalDateTime::class)
    }

    @Test
    fun `invoiceBillingScheduler should schedule charging to the first of next month` () {
        val mockedLocalDateTime = LocalDateTime.now().withDayOfMonth(3).withHour(1)
        mockkStatic(LocalDateTime::class)

        every { LocalDateTime.now() } answers { mockedLocalDateTime }

        invoiceBillingSchedulerMock.schedule()

        verify(exactly = 2) { LocalDateTime.now() }
        verify(exactly = 0) { billingServiceMock.chargeCustomersInvoices() }
        verify(exactly = 0) { billingServiceMock.checkIfFailedOrPendingInvoicesExist() }

        unmockkStatic(LocalDateTime::class)
    }
}
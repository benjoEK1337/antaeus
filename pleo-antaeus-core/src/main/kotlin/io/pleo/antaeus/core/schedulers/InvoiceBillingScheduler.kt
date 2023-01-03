package io.pleo.antaeus.core.schedulers

import io.pleo.antaeus.core.services.BillingService
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class InvoiceBillingScheduler(
    private val billingService: BillingService
) : Scheduler {

    private val scheduler = Executors.newScheduledThreadPool(10)
    private var delay = 0L

    private val chargeMonthlyInvoicesTask = Runnable {
        billingService.chargeMonthlyInvoices()
        schedule()
    }

    override fun stop() {
        scheduler.shutdown()
    }

    override fun schedule() {
        calculateSchedulerDelay()
        scheduler.schedule(chargeMonthlyInvoicesTask, delay, TimeUnit.MILLISECONDS)
    }

    private fun calculateSchedulerDelay() {
        val currentDate = LocalDateTime.now()

        if (currentDate.dayOfMonth == 1 && currentDate.hour < 6) {
            delay = Duration.between(currentDate, currentDate.withHour(6)).toMillis()
            return
        }

        val dateToScheduleNextBilling = LocalDateTime.now()
            .withMonth(currentDate.month.value + 1)
            .withDayOfMonth(1)
            .withHour(6)

        delay = Duration.between(currentDate.withHour(6), dateToScheduleNextBilling).toMillis()
    }
}
package io.pleo.antaeus.core.schedulers

import io.pleo.antaeus.core.services.BillingService
import mu.KotlinLogging
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InvoiceBillingScheduler(
    private val billingService: BillingService
) : Scheduler {
    private val logger = KotlinLogging.logger {}

    private val executor = Executors.newScheduledThreadPool(10)
    private var delay = 0L
    companion object {
        const val SHUTDOWN_TIME = 20L
    }

    private val chargeMonthlyInvoicesTask = Runnable {
        billingService.chargeMonthlyInvoices()
        schedule()
    }

    override fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(SHUTDOWN_TIME, TimeUnit.SECONDS)) {
            logger.error("Executor did not terminate in the specified time. There might be unprocessed bills.")
            val droppedTasks: List<Runnable> = executor.shutdownNow()
            logger.info("Executor was abruptly shut down. " + droppedTasks.size + " tasks will not be executed.")
        }
    }

    override fun schedule() {
        calculateSchedulerDelay()
        executor.schedule(chargeMonthlyInvoicesTask, delay, TimeUnit.MILLISECONDS)
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
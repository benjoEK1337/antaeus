package io.pleo.antaeus.core.schedulers

import io.pleo.antaeus.core.schedulers.definition.Scheduler
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
        const val CHARGE_ITERATION_MINUTES = 30
        const val SECONDS_IN_MINUTE = 60
        const val SECONDS_TO_MILLISECONDS = 1000
    }

    private val chargeMonthlyInvoicesTask = Runnable {
        billingService.chargeCustomersPendingInvoices()
        schedule()
    }

    override fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(SHUTDOWN_TIME, TimeUnit.SECONDS)) {
            logger.warn("Executor did not terminate in the specified time. There might be unprocessed bills.")
            val droppedTasks: List<Runnable> = executor.shutdownNow()
            logger.warn("Executor was abruptly shut down. " + droppedTasks.size + " tasks will not be executed.")
        }
    }

    override fun schedule() {
        calculateSchedulerDelay()
        executor.schedule(chargeMonthlyInvoicesTask, delay, TimeUnit.MILLISECONDS)
    }

    private fun calculateSchedulerDelay() {
        val currentDate = LocalDateTime.now()
        /**
         * This part of code will be executed every time the server starts (deployment, crash..) or the billing service finishes with charging
         * It could happen that someone deployed or server crashed in the middle of charging invoices
         * Even though executor will gracefully shut down, there could be still failed and pending invoices
         * The billing service will be called every half hour on the 1. to check if there are FAILED or PENDING invoices after first iteration
         * If the payment provider is unavailable, by giving the half hour of delay, there is a space for provider to recover
         * If on the second day -> there are PENDING or FAILED invoices - the admin team will be contacted
         **/
        when (currentDate.dayOfMonth) {
            1 -> {
                val halfHourInMilliseconds = (CHARGE_ITERATION_MINUTES * SECONDS_IN_MINUTE * SECONDS_TO_MILLISECONDS).toLong()
                delay = halfHourInMilliseconds
                return
            }
            2 -> {
                checkIfFailedOrPendingInvoicesExist()
            }
        }

        val dateToScheduleNextBilling = LocalDateTime.now()
            .withMonth(currentDate.month.value + 1)
            .withDayOfMonth(1)
            .withHour(1)

        delay = Duration.between(currentDate.withHour(1), dateToScheduleNextBilling).toMillis()
    }

    private fun checkIfFailedOrPendingInvoicesExist() {}
}
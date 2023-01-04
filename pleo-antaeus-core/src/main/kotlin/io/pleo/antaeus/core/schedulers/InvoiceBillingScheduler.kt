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

    /*
     * After charging iteration finishes reschedule the executor
     * If it's 1. of the month it will schedule it in half hour to process FAILED transactions
     * If not 1. of the month, schedule on the 1. of next month
     */
    private val chargeMonthlyInvoicesTask = Runnable {
        billingService.chargeCustomersInvoices()
        schedule()
    }

    override fun stop() {
        try {
            executor.shutdown()
            if (!executor.awaitTermination(SHUTDOWN_TIME, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate in the specified time. There might be unprocessed bills.")
                val droppedTasks: List<Runnable> = executor.shutdownNow()
                logger.warn("Executor was abruptly shut down. " + droppedTasks.size + " tasks will not be executed.")
            }
        } catch (ex: Exception) {
            logger.warn("Executor wasn't gracefully shut down. Tasks in the executor won't be executed.")
        }
    }

    override fun schedule() {
        try {
            calculateSchedulerDelay()
            executor.schedule(chargeMonthlyInvoicesTask, delay, TimeUnit.MILLISECONDS)
        } catch (ex: Exception) {
            // TODO Create High priority alert
            logger.error("Invoice scheduler didn't start properly. Exception message: ${ex.localizedMessage}")
        }
    }

    /*
     * This part of code will be executed every time the server starts (deployment, crash..) or the billing service finishes with charging iteration
     * It could happen that someone deployed or server crashed in the middle of charging invoices
     * Even though executor will gracefully shut down, there could be still failed and pending invoices if SHUTDOWN_TIME was exceeded
     * On every 1. The billing service will be called every half hour to check if there are FAILED or PENDING invoices after first iteration
     * If the payment provider is unavailable, by giving the half hour of delay, there is a space for a provider to recover
     * If on the second day -> there are PENDING or FAILED invoices - the admin team will be contacted
     */
    private fun calculateSchedulerDelay() {
        val currentDate = LocalDateTime.now()
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
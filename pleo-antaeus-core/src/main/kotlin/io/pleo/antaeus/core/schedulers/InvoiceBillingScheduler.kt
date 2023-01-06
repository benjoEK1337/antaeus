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
        const val HALF_HOUR_IN_MILLISECONDS = 1800000L
    }

    // In the real system we could add the property to configuration file and decide which value to use
    private fun getDelay(): Long {
        return HALF_HOUR_IN_MILLISECONDS
    }

    override fun stop() {
        try {
            executor.shutdown()

            if (!executor.awaitTermination(SHUTDOWN_TIME, TimeUnit.SECONDS)) {

                logger.error("Executor did not terminate in the specified time. There might be unprocessed bills.")

                val droppedTasks: List<Runnable> = executor.shutdownNow()
                logger.error("Executor was abruptly shut down. " + droppedTasks.size + " tasks will not be executed.")
            }
        } catch (ex: Exception) {
            logger.error("Executor wasn't gracefully shut down. Tasks in the executor won't be executed. Exception message: ${ex.localizedMessage}")
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

    private fun calculateSchedulerDelay() {
        val currentDate = LocalDateTime.now()
        when (currentDate.dayOfMonth) {
            1 -> {
                delay = getDelay()
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

        delay = Duration.between(currentDate, dateToScheduleNextBilling).toMillis()
    }

    private val chargeMonthlyInvoicesTask = Runnable {
        billingService.chargeCustomersInvoices()
        schedule()
    }

    private fun checkIfFailedOrPendingInvoicesExist() {
        billingService.checkIfFailedOrPendingInvoicesExist()
    }
}

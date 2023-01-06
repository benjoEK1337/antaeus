/*
    Defines the main() entry point of the app.
    Configures the database and sets up the REST web service.
 */

@file:JvmName("AntaeusApp")

package io.pleo.antaeus.app

import getPaymentProvider
import io.pleo.antaeus.core.external.EmailService
import io.pleo.antaeus.core.schedulers.InvoiceBillingScheduler
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.core.services.LockingService
import io.pleo.antaeus.data.*
import io.pleo.antaeus.rest.AntaeusRest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import setupInitialData
import java.io.File
import java.sql.Connection

fun main() {
    // The tables to create in the database.
    val tables = arrayOf(InvoiceTable, CustomerTable, LockTable)

    val dbFile: File = File.createTempFile("antaeus-db", ".sqlite")
    // Connect to the database and create the needed tables. Drop any existing data.
    val db = Database
        .connect(url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC",
            user = "root",
            password = "")
        .also {
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
            transaction(it) {
                addLogger(StdOutSqlLogger)
                // Drop all existing tables to ensure a clean slate on each run
                SchemaUtils.drop(*tables)
                // Create all tables
                SchemaUtils.create(*tables)
            }
        }

    // Insert example data in the database.
    val invoiceDal = InvoiceDal(db)
    val customerDal = CustomerDal(db)
    val lockDal = LockDal(db)

    // Get third parties
    val paymentProvider = getPaymentProvider()

    // Create core services
    val emailService = EmailService()
    val lockingService = LockingService(lockDal)
    val customerService = CustomerService(customerDal, emailService)
    val invoiceService = InvoiceService(customerService, invoiceDal)

    // This is _your_ billing service to be included where you see fit
    val billingService = BillingService(paymentProvider, invoiceService, customerService, lockingService)

    setupInitialData(customerService, invoiceService)

    val invoiceBillingScheduler = InvoiceBillingScheduler(billingService)
    invoiceBillingScheduler.schedule()
    registerShutdownHook(invoiceBillingScheduler)

    // Create REST web service
    AntaeusRest(
        invoiceService = invoiceService,
        customerService = customerService
    ).run()
}

// Adding this function to properly shut down the scheduler in the case instance it is terminated (deployment, crash...)
private fun registerShutdownHook(invoiceBillingScheduler: InvoiceBillingScheduler) {
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            invoiceBillingScheduler.stop()
        }
    })
}

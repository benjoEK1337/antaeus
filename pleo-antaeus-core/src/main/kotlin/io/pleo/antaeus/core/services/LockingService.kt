package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.LockException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Lock
import mu.KotlinLogging

/*
    In the real circumstances the distributed instance of database would be used
 */
class LockingService(private val dal: AntaeusDal) {

    private val logger = KotlinLogging.logger {}

    fun setLock(customerId: Int) {
        try {
            dal.setLock(customerId)
        } catch (ex: Exception) {
            throw LockException()
        }
    }

    fun releaseLock(customerId: Int) {
        try {
            dal.releaseLock(customerId)
        } catch (ex: Exception) {
            throw LockException()
        }
    }

    fun getLock(customerId: Int): Lock? {
        try {
            return dal.getLock(customerId)
        } catch (ex: Exception) {
            throw LockException()
        }
    }

    fun handleLockException(customerId: Int) {
        // TODO Create an alert if there are a lot of warnings below
        logger.warn("There are problems with handling the locking mechanism for customer with $customerId ID")
    }
}

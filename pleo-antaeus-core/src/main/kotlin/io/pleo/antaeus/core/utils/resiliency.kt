@file:JvmName("ResiliencyKt")
package io.pleo.antaeus.core.utils

import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.exceptions.ExternalServiceNotAvailableException

fun <T> retry(maxRetries: Int = 3, waitTimeInSeconds: Int = 5, block: () -> T): T {
    var retries = 0
    val waitTimeInMilliSeconds = waitTimeInSeconds.toLong() * 1000

    while (true) {
        try {
            if (retries > 0) {
                Thread.sleep(waitTimeInMilliSeconds)
            }
            return block()
        } catch (ex: Exception) {

            if (ex !is NetworkException) {
                throw ex
            }

            retries++
            if (retries == maxRetries) {
                throw ExternalServiceNotAvailableException()
            }
        }
    }
}

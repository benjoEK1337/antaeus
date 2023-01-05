package io.pleo.antaeus.core.external.definition

interface NotificationProvider {
    fun send(message: String)
}

package io.pleo.antaeus.core.schedulers


interface Scheduler {
    fun schedule()
    fun stop()
}
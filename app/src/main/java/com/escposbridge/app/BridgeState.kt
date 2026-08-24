package com.escposbridge.app

/**
 * What the bridge is actually doing, shared between the service and the UI.
 *
 * Without this the app could look healthy while doing nothing: a failed bind
 * (port already taken, most likely) only wrote a line into the rolling log
 * while the notification still read "running" and the settings screen still
 * showed the configuration as though it had taken effect. On a counter that
 * means finding out when a customer is already waiting.
 */
object BridgeState {
    enum class Status { STOPPED, RUNNING, FAILED }

    @Volatile var status: Status = Status.STOPPED
        private set
    @Volatile var boundTo: String? = null
        private set
    @Volatile var lastError: String? = null
        private set

    fun running(address: String) {
        status = Status.RUNNING; boundTo = address; lastError = null
    }

    fun failed(reason: String) {
        status = Status.FAILED; boundTo = null; lastError = reason
    }

    fun stopped() {
        status = Status.STOPPED; boundTo = null; lastError = null
    }

    /** One line for the notification and the settings screen. */
    fun summary(): String = when (status) {
        Status.RUNNING -> "Running on ${boundTo ?: "?"}"
        Status.FAILED  -> "Not running — ${lastError ?: "failed to start"}"
        Status.STOPPED -> "Stopped"
    }
}

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

    /** Set when GitHub reports a release newer than the installed build. */
    @Volatile var updateAvailable: String? = null

    /** Where prints are being sent, for the notification to show. */
    @Volatile var printerTarget: String? = null
    @Volatile var okCount: Int = 0
        private set
    @Volatile var failCount: Int = 0
        private set
    @Volatile var lastPrint: String? = null
        private set
    @Volatile private var lastPrintAt: Long = 0L

    @Synchronized fun printOk(bytes: Int, target: String) {
        okCount++; printerTarget = target
        lastPrint = "$bytes bytes to $target"
        lastPrintAt = System.currentTimeMillis()
    }

    @Synchronized fun printFailed(reason: String) {
        failCount++
        lastPrint = "failed — $reason"
        lastPrintAt = System.currentTimeMillis()
    }

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

    /**
     * The expanded notification body — everything needed to tell, without
     * unlocking the tablet or opening the app, whether the bridge is up, where
     * it is sending, and whether the last receipt actually came out.
     */
    fun details(): String = buildString {
        append(summary())
        printerTarget?.let { append("\nPrinter: ").append(it) }
        if (okCount > 0 || failCount > 0) {
            append("\nPrints: ").append(okCount).append(" ok")
            if (failCount > 0) append(", ").append(failCount).append(" failed")
        }
        lastPrint?.let { append("\nLast: ").append(it).append(ago()) }
        updateAvailable?.let { append("\nUpdate available: ").append(it) }
    }

    private fun ago(): String {
        if (lastPrintAt == 0L) return ""
        val secs = (System.currentTimeMillis() - lastPrintAt) / 1000
        return when {
            secs < 60   -> " ($secs s ago)"
            secs < 3600 -> " (${secs / 60} min ago)"
            else        -> " (${secs / 3600} h ago)"
        }
    }
}

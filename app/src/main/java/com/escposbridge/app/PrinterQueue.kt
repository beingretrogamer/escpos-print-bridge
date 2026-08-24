package com.escposbridge.app

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * The single path to the printer.
 *
 * Everything that opens a socket to the printer goes through here, because a
 * typical ESC/POS printer accepts one connection at a time — two at once and
 * the second either fails or its bytes interleave with the first, and both
 * slips come out unreadable. The bridge already serialised its own jobs, but
 * Test print opened its own socket straight from the settings screen and could
 * land in the middle of a receipt.
 */
object PrinterQueue {

    private val lock = ReentrantLock(true)

    private const val QUEUE_WAIT_MS = 15_000L
    private const val CONNECT_RETRIES = 2
    private const val RETRY_BACKOFF_MS = 700L

    /**
     * Blocking. Throws if the printer could not be reached, or if another job
     * held the queue for too long.
     */
    fun send(
        ip: String,
        port: Int,
        bytes: ByteArray,
        timeoutMs: Int = 5000,
        onLog: (String) -> Unit = {},
    ) {
        if (!lock.tryLock(QUEUE_WAIT_MS, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("printer busy with another job")
        }
        try {
            sendWithRetry(ip, port, bytes, timeoutMs, onLog)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Retries only a failure to *connect*.
     *
     * A printer that is asleep, or busy finishing the previous slip, refuses
     * the connection for a second or two, and retrying there turns a spurious
     * failure into a print. Once the connection is open and bytes have gone
     * out the printer may already hold part of the receipt, so resending would
     * produce a duplicate or a torn slip followed by a whole one — a write
     * failure is reported, never retried.
     */
    private fun sendWithRetry(
        ip: String,
        port: Int,
        bytes: ByteArray,
        timeoutMs: Int,
        onLog: (String) -> Unit,
    ) {
        var attempt = 0
        while (true) {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress(ip, port), timeoutMs)
            } catch (e: Exception) {
                try { sock.close() } catch (_: Exception) {}
                attempt++
                if (attempt > CONNECT_RETRIES) throw e
                onLog("Printer did not answer (${e.message}); retry $attempt of $CONNECT_RETRIES")
                try { Thread.sleep(RETRY_BACKOFF_MS * attempt) } catch (_: InterruptedException) {}
                continue
            }
            sock.use {
                it.soTimeout = timeoutMs
                it.getOutputStream().apply { write(bytes); flush() }
                // Give the printer a moment to drain before the FIN.
                try { Thread.sleep(120) } catch (_: InterruptedException) {}
            }
            return
        }
    }
}

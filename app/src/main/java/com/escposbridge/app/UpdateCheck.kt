package com.escposbridge.app

import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub whether a newer release exists.
 *
 * Deliberately only *checks*. Installing an APK from inside the app needs
 * REQUEST_INSTALL_PACKAGES, which Google restricts to apps whose core purpose
 * is installing software — a print bridge asking for it is a good way to be
 * refused a listing. Handing the download to the browser costs one extra tap
 * and no permission at all.
 */
object UpdateCheck {

    private const val LATEST_API =
        "https://api.github.com/repos/beingretrogamer/escpos-print-bridge/releases/latest"
    const val LATEST_PAGE =
        "https://github.com/beingretrogamer/escpos-print-bridge/releases/latest"

    data class Result(val latest: String, val newer: Boolean)

    /** Blocking; call from a background thread. Returns null if it could not tell. */
    fun fetch(currentVersion: String): Result? {
        return try {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "escpos-print-bridge")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: return null
            Result(tag, isNewer(tag, currentVersion))
        } catch (_: Exception) {
            null // offline, rate-limited, whatever — silence is the right answer here
        }
    }

    /** Numeric compare on dotted versions, tolerant of a leading "v". */
    internal fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").split('.').map { s ->
            s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
}

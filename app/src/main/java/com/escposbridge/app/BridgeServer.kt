package com.escposbridge.app

import android.util.Base64
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HTTP -> TCP print bridge.
 *
 * Same wire contract as print-bridge/print-bridge.js, so the web app talks to
 * either one without knowing the difference:
 *
 *   GET  /              -> plain text probe
 *   GET  /health        -> JSON status
 *   POST /print         -> raw ESC/POS bytes, or JSON { ip?, port?, bytes: base64 }
 *   POST /print-base64  -> JSON { ip?, port?, data: base64 }
 *
 * Written against plain sockets rather than a web framework: the request shapes
 * are few and fixed, and a till should not inherit a dependency tree it will
 * never update.
 */
class BridgeServer(
    private val printerIp: String,
    private val printerPort: Int,
    private val bridgePort: Int,
    private val loopbackOnly: Boolean,
    private val allowedOrigin: String = "*",
    private val tcpTimeoutMs: Int = 5000,
    private val onLog: (String) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun isRunning() = running.get()

    fun start() {
        if (running.getAndSet(true)) return
        pool.execute {
            try {
                // Loopback binding keeps the bridge invisible to the rest of the
                // Wi-Fi, which is what we want when the browser using it is on
                // this same device.
                val bind = if (loopbackOnly) InetAddress.getByName("127.0.0.1") else null
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(if (bind != null) InetSocketAddress(bind, bridgePort)
                            else InetSocketAddress(bridgePort))
                serverSocket = socket
                onLog("Listening on ${if (loopbackOnly) "127.0.0.1" else "0.0.0.0"}:$bridgePort -> $printerIp:$printerPort")

                while (running.get()) {
                    val client = try { socket.accept() } catch (e: Exception) { break }
                    pool.execute { handle(client) }
                }
            } catch (e: Exception) {
                onLog("Server error: ${e.message}")
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        onLog("Stopped")
    }

    // ── Request handling ──────────────────────────────────────────────

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 10_000
            val input = client.getInputStream()
            val out = BufferedOutputStream(client.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) { respond(out, 400, "text/plain", "Bad request".toByteArray()); return }
            val method = parts[0].uppercase()
            val path = parts[1].substringBefore('?')

            // Headers
            var contentLength = 0
            var contentType = ""
            var headerIp: String? = null
            var headerPort: Int? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                val name = line.substring(0, idx).trim().lowercase()
                val value = line.substring(idx + 1).trim()
                when (name) {
                    "content-length" -> contentLength = value.toIntOrNull() ?: 0
                    "content-type" -> contentType = value.lowercase()
                    "x-printer-ip" -> headerIp = value
                    "x-printer-port" -> headerPort = value.toIntOrNull()
                }
            }

            if (method == "OPTIONS") { respond(out, 204, null, ByteArray(0)); return }

            when {
                method == "GET" && path == "/" ->
                    respond(out, 200, "text/plain", "ESC/POS Print Bridge OK\n".toByteArray())

                method == "GET" && path == "/health" -> {
                    val body = """{"ok":true,"bridge":{"port":$bridgePort,"loopbackOnly":$loopbackOnly},""" +
                               """"printer":{"ip":"$printerIp","port":$printerPort},"host":"android"}"""
                    respond(out, 200, "application/json", body.toByteArray())
                }

                method == "POST" && (path == "/print" || path == "/print-base64") -> {
                    if (contentLength > MAX_BODY) {
                        respond(out, 413, "application/json", """{"ok":false,"error":"payload too large"}""".toByteArray())
                        return
                    }
                    val body = readExactly(input, contentLength)
                    var targetIp = headerIp ?: printerIp
                    var targetPort = headerPort ?: printerPort

                    val bytes: ByteArray = if (contentType.contains("application/json")) {
                        val json = String(body, Charsets.UTF_8)
                        jsonString(json, "ip")?.let { targetIp = it }
                        jsonNumber(json, "port")?.let { targetPort = it }
                        val b64 = jsonString(json, "bytes") ?: jsonString(json, "data")
                        if (b64 == null) {
                            respond(out, 400, "application/json", """{"ok":false,"error":"missing bytes/data field"}""".toByteArray())
                            return
                        }
                        try { Base64.decode(b64, Base64.DEFAULT) } catch (e: Exception) {
                            respond(out, 400, "application/json", """{"ok":false,"error":"bad base64"}""".toByteArray())
                            return
                        }
                    } else body

                    if (bytes.isEmpty()) {
                        respond(out, 400, "application/json", """{"ok":false,"error":"empty payload"}""".toByteArray())
                        return
                    }

                    try {
                        sendToPrinter(targetIp, targetPort, bytes)
                        onLog("Printed ${bytes.size} bytes -> $targetIp:$targetPort")
                        respond(out, 200, "application/json",
                            """{"ok":true,"bytesSent":${bytes.size},"printer":"$targetIp:$targetPort"}""".toByteArray())
                    } catch (e: Exception) {
                        onLog("Print failed: ${e.message}")
                        respond(out, 502, "application/json",
                            """{"ok":false,"error":"${escape(e.message ?: "printer error")}"}""".toByteArray())
                    }
                }

                else -> respond(out, 404, "text/plain", "Not found\n".toByteArray())
            }
        } catch (e: Exception) {
            onLog("Request error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun sendToPrinter(ip: String, port: Int, bytes: ByteArray) {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(ip, port), tcpTimeoutMs)
            sock.soTimeout = tcpTimeoutMs
            sock.getOutputStream().apply { write(bytes); flush() }
            // Give the printer a moment to drain before the FIN.
            try { Thread.sleep(120) } catch (_: InterruptedException) {}
        }
    }

    private fun respond(out: BufferedOutputStream, code: Int, contentType: String?, body: ByteArray) {
        val reason = when (code) {
            200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"
            404 -> "Not Found"; 413 -> "Payload Too Large"; 502 -> "Bad Gateway"
            else -> "OK"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $code $reason\r\n")
        // CORS + Private Network Access, matching the Node bridge: the page
        // calling us is served from an HTTPS origin, not from here.
        sb.append("Access-Control-Allow-Origin: $allowedOrigin\r\n")
        sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        sb.append("Access-Control-Allow-Headers: Content-Type, X-Printer-Ip, X-Printer-Port\r\n")
        sb.append("Access-Control-Allow-Private-Network: true\r\n")
        sb.append("Access-Control-Max-Age: 86400\r\n")
        if (contentType != null) sb.append("Content-Type: $contentType\r\n")
        sb.append("Content-Length: ${body.size}\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    // ── Tiny parsing helpers ──────────────────────────────────────────

    /** Reads one CRLF-terminated line without buffering past it. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(c.toChar())
            if (sb.length > 8192) return sb.toString()
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n == -1) break
            read += n
        }
        return if (read == length) buf else buf.copyOf(read)
    }

    /** Pulls "key":"value" out of the small, known-shape JSON we accept. */
    internal fun jsonString(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    internal fun jsonNumber(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object { const val MAX_BODY = 5 * 1024 * 1024 }
}

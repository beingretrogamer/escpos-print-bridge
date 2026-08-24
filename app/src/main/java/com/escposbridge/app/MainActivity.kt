package com.escposbridge.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Setup and status screen.
 *
 * Laid out so the question you actually have — is it working? — is answered by
 * the top of the screen before you read anything else. Configuration sits
 * below that, and the warning card only appears when it applies.
 */
class MainActivity : Activity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusCounts: TextView
    private lateinit var batteryCard: LinearLayout
    private lateinit var printerIp: EditText
    private lateinit var printerPort: EditText
    private lateinit var bridgePort: EditText
    private lateinit var loopback: CheckBox
    private lateinit var bridgeUrlView: TextView
    private lateinit var logView: TextView
    private lateinit var versionText: TextView

    private val ui = Handler(Looper.getMainLooper())

    /**
     * Held as a field so it can actually be cancelled — an un-cancelled
     * self-reposting Runnable keeps the Activity alive after it is closed and
     * wakes the device to redraw a screen nobody is looking at.
     */
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        statusDetail = findViewById(R.id.statusDetail)
        statusCounts = findViewById(R.id.statusCounts)
        batteryCard = findViewById(R.id.batteryCard)
        printerIp = findViewById(R.id.printerIp)
        printerPort = findViewById(R.id.printerPort)
        bridgePort = findViewById(R.id.bridgePort)
        loopback = findViewById(R.id.loopback)
        bridgeUrlView = findViewById(R.id.bridgeUrl)
        logView = findViewById(R.id.logView)
        versionText = findViewById(R.id.versionText)

        printerIp.setText(Prefs.printerIp(this))
        printerPort.setText(Prefs.printerPort(this).toString())
        bridgePort.setText(Prefs.bridgePort(this).toString())
        loopback.isChecked = Prefs.loopbackOnly(this)
        loopback.setOnCheckedChangeListener { _, _ -> bridgeUrlView.text = bridgeUrl() }

        findViewById<Button>(R.id.btnStart).setOnClickListener { saveAndStart() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopBridge() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { testPrint() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyBridgeUrl() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { openBatterySettings() }

        versionText.text = getString(R.string.version_line, appVersion())
        bridgeUrlView.text = bridgeUrl()

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(ticker)
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
    }

    /** Android 13+ will not show the foreground-service notification without this. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    // ── Actions ───────────────────────────────────────────────────────

    private fun readSettings(): Triple<String, Int, Int> = Triple(
        printerIp.text.toString().trim().ifEmpty { Prefs.DEF_PRINTER_IP },
        printerPort.text.toString().trim().toIntOrNull() ?: Prefs.DEF_PRINTER_PORT,
        bridgePort.text.toString().trim().toIntOrNull() ?: Prefs.DEF_BRIDGE_PORT,
    )

    private fun saveAndStart() {
        val (ip, port, bport) = readSettings()
        Prefs.save(this, ip, port, bport, loopback.isChecked)
        bridgeUrlView.text = bridgeUrl()
        // Restart so a changed port or bind scope actually takes effect.
        stopService(Intent(this, PrintBridgeService::class.java))
        val svc = Intent(this, PrintBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        toast(getString(R.string.toast_started, bport))
    }

    private fun stopBridge() {
        stopService(Intent(this, PrintBridgeService::class.java))
        toast(getString(R.string.toast_stopped))
    }

    /**
     * Talks straight to the printer, skipping the HTTP hop, so a failure here
     * means the printer or the network rather than the bridge.
     */
    private fun testPrint() {
        val (ip, port, _) = readSettings()
        thread {
            val result = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 5000)
                    val o: OutputStream = s.getOutputStream()
                    o.write(testSlip()); o.flush()
                    Thread.sleep(150)
                }
                getString(R.string.test_ok, ip, port)
            } catch (e: Exception) {
                getString(R.string.test_fail, ip, port, e.message ?: "unknown error")
            }
            BridgeLog.append(result)
            ui.post { toast(result) }
        }
    }

    private fun testSlip(): ByteArray {
        val esc = 0x1b.toByte()
        val out = ArrayList<Byte>()
        out.addAll(listOf(esc, 0x40))
        out.addAll(listOf(esc, 0x61, 0x01))
        out.addAll("PRINT BRIDGE TEST\n\n".toByteArray().toList())
        out.addAll(listOf(esc, 0x61, 0x00))
        out.addAll("If you can read this,\nthis device can reach the printer.\n".toByteArray().toList())
        out.addAll("\n\n\n".toByteArray().toList())
        out.addAll(listOf(0x1d.toByte(), 0x56, 0x42, 0x00))
        return out.toByteArray()
    }

    private fun bridgeUrl(): String {
        val port = bridgePort.text.toString().trim().toIntOrNull() ?: Prefs.DEF_BRIDGE_PORT
        return if (loopback.isChecked) "http://localhost:$port"
        else "http://<this device's IP>:$port"
    }

    private fun copyBridgeUrl() {
        val url = bridgeUrl()
        try {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("bridge url", url))
            toast(getString(R.string.toast_copied, url))
        } catch (_: Exception) {
            toast(url)
        }
    }

    /**
     * Battery optimisation is the one thing that silently kills a long-running
     * service, and an app cannot exempt itself. Opening the settings list
     * rather than prompting directly needs no extra permission and stays
     * inside Play Store policy.
     */
    private fun openBatterySettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )
        for (i in candidates) {
            try { startActivity(i); return } catch (_: Exception) { /* try the next */ }
        }
        toast(getString(R.string.battery_no_screen))
    }

    private fun isBatteryExempt(): Boolean = try {
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (_: Exception) { false }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    // ── Refresh ───────────────────────────────────────────────────────

    private fun refresh() {
        val colour = when (BridgeState.status) {
            BridgeState.Status.RUNNING -> getColor(R.color.ok)
            BridgeState.Status.FAILED  -> getColor(R.color.danger)
            BridgeState.Status.STOPPED -> getColor(R.color.ink_faint)
        }
        statusText.text = when (BridgeState.status) {
            BridgeState.Status.RUNNING -> getString(R.string.state_running)
            BridgeState.Status.FAILED  -> getString(R.string.state_failed)
            BridgeState.Status.STOPPED -> getString(R.string.state_stopped)
        }
        statusText.setTextColor(if (BridgeState.status == BridgeState.Status.FAILED) colour else getColor(R.color.ink))
        statusDot.background?.setColorFilter(colour, PorterDuff.Mode.SRC_IN)

        statusDetail.text = when (BridgeState.status) {
            BridgeState.Status.RUNNING ->
                getString(R.string.detail_running, BridgeState.boundTo ?: "?", BridgeState.printerTarget ?: "-")
            BridgeState.Status.FAILED ->
                BridgeState.lastError ?: getString(R.string.state_failed)
            BridgeState.Status.STOPPED -> getString(R.string.detail_stopped)
        }

        statusCounts.visibility = if (BridgeState.okCount > 0 || BridgeState.failCount > 0) View.VISIBLE else View.GONE
        statusCounts.text = getString(R.string.counts, BridgeState.okCount, BridgeState.failCount) +
            (BridgeState.lastPrint?.let { "  ·  $it" } ?: "")

        batteryCard.visibility = if (isBatteryExempt()) View.GONE else View.VISIBLE

        logView.text = BridgeLog.text().ifEmpty { getString(R.string.no_activity) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

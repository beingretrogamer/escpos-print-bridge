package com.escposbridge.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Setup screen: printer address, which port to listen on, start/stop, test print.
 *
 * Built in code rather than XML so the app carries no resource-heavy UI toolkit —
 * the whole point is a small artifact that keeps working on a till nobody updates.
 */
class MainActivity : Activity() {

    private lateinit var ipField: EditText
    private lateinit var portField: EditText
    private lateinit var bridgePortField: EditText
    private lateinit var loopbackSwitch: CheckBox
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = 13f
            setPadding(0, pad / 3, 0, pad)
        })

        ipField = labelledField(root, getString(R.string.label_printer_ip), Prefs.printerIp(this), InputType.TYPE_CLASS_TEXT)
        portField = labelledField(root, getString(R.string.label_printer_port), Prefs.printerPort(this).toString(), InputType.TYPE_CLASS_NUMBER)
        bridgePortField = labelledField(root, getString(R.string.label_bridge_port), Prefs.bridgePort(this).toString(), InputType.TYPE_CLASS_NUMBER)

        loopbackSwitch = CheckBox(this).apply {
            text = getString(R.string.label_loopback)
            isChecked = Prefs.loopbackOnly(this@MainActivity)
        }
        root.addView(loopbackSwitch)
        root.addView(TextView(this).apply {
            text = getString(R.string.hint_loopback)
            textSize = 12f
            setPadding(0, 0, 0, pad)
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = getString(R.string.btn_start)
            setOnClickListener { saveAndStart() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Button(this).apply {
            text = getString(R.string.btn_stop)
            setOnClickListener { stopBridge() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(row)

        root.addView(Button(this).apply {
            text = getString(R.string.btn_test)
            setOnClickListener { testPrint() }
        })

        status = TextView(this).apply {
            setPadding(0, pad, 0, pad / 2)
            textSize = 14f
            gravity = Gravity.START
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = getString(R.string.label_activity)
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        logView = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.DKGRAY)
            setPadding(0, pad / 3, 0, 0)
        }
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })

        requestNotificationPermissionIfNeeded()
        tick()
    }

    private fun labelledField(parent: LinearLayout, label: String, value: String, inputType: Int): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 12f
        })
        val field = EditText(this).apply {
            setText(value)
            this.inputType = inputType
            setSingleLine()
        }
        parent.addView(field)
        return field
    }

    /** Android 13+ will not show the foreground-service notification without this. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun readSettings(): Triple<String, Int, Int> {
        val ip = ipField.text.toString().trim().ifEmpty { Prefs.DEF_PRINTER_IP }
        val port = portField.text.toString().trim().toIntOrNull() ?: Prefs.DEF_PRINTER_PORT
        val bridgePort = bridgePortField.text.toString().trim().toIntOrNull() ?: Prefs.DEF_BRIDGE_PORT
        return Triple(ip, port, bridgePort)
    }

    private fun saveAndStart() {
        val (ip, port, bridgePort) = readSettings()
        Prefs.save(this, ip, port, bridgePort, loopbackSwitch.isChecked)
        // Restart so a changed port or bind scope actually takes effect.
        stopService(Intent(this, PrintBridgeService::class.java))
        val svc = Intent(this, PrintBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        toast(getString(R.string.toast_started, bridgePort))
    }

    private fun stopBridge() {
        stopService(Intent(this, PrintBridgeService::class.java))
        toast(getString(R.string.toast_stopped))
    }

    /** Talks straight to the printer, skipping the HTTP hop, so a failure here
        means the printer/network — not the bridge. */
    private fun testPrint() {
        val (ip, port, _) = readSettings()
        thread {
            val result = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 5000)
                    val o: OutputStream = s.getOutputStream()
                    o.write(testSlip())
                    o.flush()
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
        out.addAll(listOf(esc, 0x40))                      // init
        out.addAll(listOf(esc, 0x61, 0x01))                // centre
        out.addAll("PRINT BRIDGE TEST\n\n".toByteArray().toList())
        out.addAll(listOf(esc, 0x61, 0x00))                // left
        out.addAll("If you can read this,\nthis device can reach the printer.\n".toByteArray().toList())
        out.addAll("\n\n\n".toByteArray().toList())
        out.addAll(listOf(0x1d.toByte(), 0x56, 0x42, 0x00)) // partial cut
        return out.toByteArray()
    }

    private fun tick() {
        status.text = getString(R.string.status_line, Prefs.printerIp(this), Prefs.printerPort(this), Prefs.bridgePort(this))
        logView.text = BridgeLog.text().ifEmpty { getString(R.string.no_activity) }
        ui.postDelayed({ tick() }, 1500)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

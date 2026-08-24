package com.escposbridge.app

import android.content.Context

/** The handful of settings the shop actually changes. */
object Prefs {
    private const val FILE = "printbridge"

    const val DEF_PRINTER_IP = "192.168.1.200"
    const val DEF_PRINTER_PORT = 9100
    const val DEF_BRIDGE_PORT = 8080

    fun printerIp(c: Context): String = p(c).getString("printerIp", DEF_PRINTER_IP)!!
    fun printerPort(c: Context): Int = p(c).getInt("printerPort", DEF_PRINTER_PORT)
    fun bridgePort(c: Context): Int = p(c).getInt("bridgePort", DEF_BRIDGE_PORT)
    /** Loopback by default: the browser using this bridge is on this device. */
    fun loopbackOnly(c: Context): Boolean = p(c).getBoolean("loopbackOnly", true)
    /**
     * Whether the bridge should come back on its own after a reboot.
     *
     * Tracks the last deliberate choice: pressing Stop meant stop, and a
     * restart should not quietly undo that. Defaults to true so a tablet that
     * has never been touched still comes up printing.
     */
    fun autoStart(c: Context): Boolean = p(c).getBoolean("autoStart", true)

    fun setAutoStart(c: Context, on: Boolean) {
        p(c).edit().putBoolean("autoStart", on).apply()
    }

    fun save(c: Context, ip: String, port: Int, bridgePort: Int, loopbackOnly: Boolean) {
        p(c).edit()
            .putString("printerIp", ip)
            .putInt("printerPort", port)
            .putInt("bridgePort", bridgePort)
            .putBoolean("loopbackOnly", loopbackOnly)
            .apply()
    }

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

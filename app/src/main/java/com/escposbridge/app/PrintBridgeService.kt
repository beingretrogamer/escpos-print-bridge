package com.escposbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Keeps the bridge alive as a foreground service.
 *
 * This is the whole reason for building an app rather than living in Termux:
 * a foreground service with a visible notification is the arrangement Android
 * actually promises to keep running. A background thread, or a terminal app the
 * user has swiped away, is killed at the OS's discretion — which on a till
 * means receipts silently stop halfway through a shift.
 */
class PrintBridgeService : Service() {

    private var server: BridgeServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Keeps the CPU and Wi-Fi up for the duration of a print, and only then.
     *
     * The service used to hold a partial wake lock permanently, which is pure
     * drain on a device that is ever off the charger. In loopback mode it is
     * also unnecessary: the request comes from a browser on this same device,
     * so the CPU is by definition already awake when one arrives. What does
     * need protecting is the hop out to the printer — an aggressive Wi-Fi
     * sleep between accepting the request and reaching the printer is exactly
     * how a print goes missing.
     */
    private val powerGate = object : PowerGate {
        override fun acquire() = acquireLocks()
        override fun release() = releaseLocks()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_starting)))

        if (server?.isRunning() != true) {
            val s = BridgeServer(
                printerIp = Prefs.printerIp(this),
                printerPort = Prefs.printerPort(this),
                bridgePort = Prefs.bridgePort(this),
                loopbackOnly = Prefs.loopbackOnly(this),
                onLog = { line -> BridgeLog.append(line); updateNotification(line) },
                power = powerGate,
            )
            s.start()
            server = s
            // The bind happens on a worker thread; give it a moment so the
            // notification reflects the real outcome instead of "Starting…".
            android.os.Handler(mainLooper).postDelayed({ updateNotification(BridgeState.summary()) }, 400)
        }

        // In LAN mode the request arrives from another device, so the radio has
        // to stay reachable even when this one is idle. In loopback mode the
        // locks are taken per print instead.
        if (!Prefs.loopbackOnly(this)) acquireLocks()

        // START_STICKY: if Android reclaims us under memory pressure, come back.
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        releaseLocks(force = true)
        super.onDestroy()
    }

    @Synchronized
    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                // Partial: CPU stays available, screen is free to sleep.
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EscPosBridge::print")
                    .apply { setReferenceCounted(false) }
            }
            // Timeout is a backstop: if a print somehow never returns, the lock
            // still lets go rather than draining the battery until reboot.
            if (wakeLock?.isHeld != true) wakeLock?.acquire(LOCK_TIMEOUT_MS)

            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EscPosBridge::wifi")
                    .apply { setReferenceCounted(false) }
            }
            if (wifiLock?.isHeld != true) wifiLock?.acquire()
        } catch (e: Exception) {
            BridgeLog.append("Could not take wake/wifi lock: ${e.message}")
        }
    }

    @Synchronized
    private fun releaseLocks(force: Boolean = false) {
        // In LAN mode the locks are held for as long as the service runs.
        if (!force && !Prefs.loopbackOnly(this)) return
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.channel_desc); setShowBadge(false) }
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PrintBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val failed = BridgeState.status == BridgeState.Status.FAILED
        return builder
            .setContentTitle(getString(if (failed) R.string.notif_title_failed else R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(if (failed) android.R.drawable.stat_notify_error else android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_stop), stop)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            (getSystemService(NotificationManager::class.java))
                .notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "printbridge"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.escposbridge.app.STOP"
        /** Backstop so a hung print cannot hold the CPU awake indefinitely. */
        const val LOCK_TIMEOUT_MS = 2 * 60 * 1000L
    }
}

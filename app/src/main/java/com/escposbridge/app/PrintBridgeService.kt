package com.escposbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
            )
            s.start()
            server = s
        }

        acquireWakeLock()
        // START_STICKY: if Android reclaims us under memory pressure, come back.
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // Partial lock: CPU stays available while the screen is free to sleep.
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EscPosBridge::socket").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
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
        return builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(open)
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
    }
}

package com.escposbridge.app

/** Last few lines of activity, so the shop can see the bridge doing something. */
object BridgeLog {
    private const val MAX = 60
    private val lines = ArrayDeque<String>()

    @Synchronized fun append(line: String) {
        val stamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())
        lines.addLast("$stamp  $line")
        while (lines.size > MAX) lines.removeFirst()
    }

    @Synchronized fun text(): String = lines.reversed().joinToString("\n")
}

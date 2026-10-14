package com.cyberengine

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    fun isReady(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** Runs a command as the shell user (UID 2000). */
    fun exec(cmd: String): String = try {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", "$cmd 2>&1"), null, null)
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (t: Throwable) {
        "ERR: ${t.message}"
    }
}

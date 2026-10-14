package com.cyberengine

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

object ShizukuHelper {

    // Shizuku.newProcess(...) is private in some API builds — reach it via reflection.
    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java.declaredMethods
                .firstOrNull { it.name == "newProcess" && it.parameterTypes.size == 3 }
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    fun isReady(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Runs a command as the shell user (UID 2000). */
    fun exec(cmd: String): String = try {
        val m = newProcessMethod ?: return "ERR: Shizuku.newProcess not found"
        val args = arrayOf<Any?>(arrayOf("sh", "-c", "$cmd 2>&1"), null, null)
        val p = m.invoke(null, *args) as Process
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (t: Throwable) {
        "ERR: ${t.message}"
    }
}

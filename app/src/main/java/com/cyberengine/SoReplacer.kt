package com.cyberengine

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

object SoReplacer {

    private const val ASSET_DIR = "il2cpp-replace"
    private val SKIP = listOf(".txt", ".md")

    fun replace(ctx: Context, pkg: String): String {
        val srcDir = extractAssets(ctx)
        val files = srcDir.listFiles()?.filter { it.isFile && SKIP.none { e -> it.name.endsWith(e) } }
        if (files.isNullOrEmpty()) return "il2cpp-replace is empty"

        ShizukuHelper.exec("am force-stop $pkg")

        val apk = apkPath(pkg) ?: return "$pkg is not installed"
        val libDir = "${File(apk).parent}/lib/arm64"
        val libDirExists = ShizukuHelper.exec("ls \"$libDir\" 2>/dev/null").isNotBlank()

        val report = StringBuilder()
        for (f in files) {
            val r = if (libDirExists) copyToLibDir(f, libDir) else patchInApk(apk, f)
            report.append(f.name).append(": ").append(r).append('\n')
        }
        report.append("Launch the game.")
        return report.toString().trim()
    }

    private fun extractAssets(ctx: Context): File {
        val out = File(ctx.getExternalFilesDir(null), "il2cpp-replace").apply { mkdirs() }
        ctx.assets.list(ASSET_DIR)?.forEach { name ->
            if (SKIP.any { name.endsWith(it) }) return@forEach
            ctx.assets.open("$ASSET_DIR/$name").use { i ->
                File(out, name).outputStream().use { i.copyTo(it) }
            }
        }
        return out
    }

    private fun apkPath(pkg: String): String? =
        ShizukuHelper.exec("pm path $pkg")
            .lineSequence()
            .map { it.trim().removePrefix("package:") }
            .firstOrNull { it.endsWith("base.apk") }

    private fun copyToLibDir(src: File, libDir: String): String {
        val dst = "$libDir/${src.name}"
        val out = ShizukuHelper.exec(
            "cp -f \"${src.absolutePath}\" \"$dst\" && chmod 644 \"$dst\" && echo __OK__"
        )
        return if (out.contains("__OK__")) "replaced" else "failed"
    }

    /** Fallback for extractNativeLibs=false: overwrite the .so bytes inside base.apk. */
    private fun patchInApk(apk: String, src: File): String {
        val info = findZipEntry(apk, "lib/arm64/${src.name}") ?: return "not found in APK"
        if (info.size != src.length())
            return "size mismatch (${src.length()} vs ${info.size})"
        val out = ShizukuHelper.exec(
            "dd if=\"${src.absolutePath}\" of=\"$apk\" bs=1M " +
            "iflag=skip_bytes,count_bytes oflag=seek_bytes " +
            "skip=0 seek=${info.offset} count=${info.size} conv=notrunc 2>/dev/null && echo __OK__"
        )
        return if (out.contains("__OK__")) "patched in APK" else "failed"
    }

    private class EntryInfo(val offset: Long, val size: Long)

    private fun findZipEntry(apkPath: String, entry: String): EntryInfo? {
        val f = File(apkPath)
        if (!f.canRead()) return null
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            val tailLen = minOf(len, 22L + 65535L).toInt()
            val tail = ByteArray(tailLen)
            raf.seek(len - tailLen)
            raf.readFully(tail)

            var eocd = -1
            for (i in tailLen - 22 downTo 0) {
                if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() &&
                    tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()) { eocd = i; break }
            }
            if (eocd < 0) return null

            val count = le16(tail, eocd + 10)
            var p = le32(tail, eocd + 16)
            val cd = ByteArray(46)

            for (n in 0 until count) {
                raf.seek(p)
                raf.readFully(cd)
                if (le32(cd, 0) != 0x02014b50L) break
                val nameLen = le16(cd, 28)
                val extraLen = le16(cd, 30)
                val commentLen = le16(cd, 32)
                val size = le32(cd, 24)
                val localOff = le32(cd, 42)
                val nameBytes = ByteArray(nameLen)
                raf.readFully(nameBytes)

                if (String(nameBytes) == entry) {
                    val lh = ByteArray(30)
                    raf.seek(localOff)
                    raf.readFully(lh)
                    val dataOff = localOff + 30 + le16(lh, 26) + le16(lh, 28)
                    return EntryInfo(dataOff, size)
                }
                p += 46 + nameLen + extraLen + commentLen
            }
        }
        return null
    }

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
        ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}SoReplacer.kt

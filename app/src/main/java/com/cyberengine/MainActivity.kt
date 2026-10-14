package com.cyberengine

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cyberengine.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var pkg = GameTargets.FREE_FIRE

    private val prefs by lazy { getSharedPreferences("cyber", MODE_PRIVATE) }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        pkg = prefs.getString("pkg", GameTargets.FREE_FIRE) ?: GameTargets.FREE_FIRE
        if (pkg == GameTargets.FREE_FIRE_MAX) b.radioMax.isChecked = true else b.radioFF.isChecked = true

        b.radioGroup.setOnCheckedChangeListener { _, id ->
            pkg = if (id == R.id.radioMax) GameTargets.FREE_FIRE_MAX else GameTargets.FREE_FIRE
            prefs.edit().putString("pkg", pkg).apply()
            refresh()
        }

        b.btnActivate.setOnClickListener { activate() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7)
        }

        Shizuku.addRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val shizuku = if (ShizukuHelper.isReady()) "Shizuku: READY" else "Shizuku: not granted"
        val overlay = if (Settings.canDrawOverlays(this)) "Overlay: OK" else "Overlay: not granted"
        val game = if (isInstalled(pkg)) "Game: installed" else "Game: MISSING"
        b.txtStatus.text = "$shizuku\n$overlay\n$game"
    }

    private fun isInstalled(p: String) = try {
        packageManager.getPackageInfo(p, 0); true
    } catch (t: Throwable) { false }

    private fun activate() {
        if (!ShizukuHelper.isReady()) {
            toast("Grant Shizuku permission first")
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(1)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("Grant overlay permission")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return
        }
        ContextCompat.startForegroundService(
            this, Intent(this, FloatingService::class.java).putExtra("pkg", pkg)
        )
        toast("CYBER ENGINE active")
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

package com.cyberengine

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var pkg = GameTargets.FREE_FIRE

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("pkg")?.let {
            pkg = it
            getSharedPreferences("cyber", MODE_PRIVATE).edit().putString("pkg", it).apply()
        }
        return START_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startFg() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("cyber", "CYBER ENGINE", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, "cyber")
            .setContentTitle("CYBER ENGINE")
            .setContentText("Floating menu active")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(1, n)
    }

    private fun addBubble() {
        val tv = TextView(this).apply {
            text = "CE"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_bubble)
        }
        val lp = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(180)
        }
        tv.setOnTouchListener(Drag(lp))
        wm.addView(tv, lp)
        bubble = tv
    }

    private inner class Drag(private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
        private var ix = 0; private var iy = 0
        private var tx = 0f; private var ty = 0f
        private var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    lp.x = ix + dx; lp.y = iy + dy
                    wm.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            return true
        }
    }

    private fun togglePanel() {
        panel?.let { wm.removeView(it); panel = null; return }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        box.addView(TextView(this).apply {
            text = "CYBER ENGINE"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 17f
        })
        box.addView(TextView(this).apply {
            text = if (pkg == GameTargets.FREE_FIRE_MAX) "Free Fire Max" else "Free Fire"
            setTextColor(Color.WHITE)
            textSize = 13f
        })
        box.addView(Button(this).apply {
            text = "Activate aimbot + location"
            setBackgroundResource(R.drawable.bg_btn)
            setTextColor(Color.WHITE)
            setOnClickListener { runReplace() }
        })
        box.addView(Button(this).apply {
            text = "Close"
            setBackgroundResource(R.drawable.bg_btn)
            setTextColor(Color.WHITE)
            setOnClickListener { stopSelf() }
        })

        val lp = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(box, lp)
        panel = box
    }

    private fun runReplace() {
        Thread {
            val result = SoReplacer.replace(this, pkg)
            runOnUiThread { Toast.makeText(this, result, Toast.LENGTH_LONG).show() }
        }.start()
    }

    override fun onDestroy() {
        bubble?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}

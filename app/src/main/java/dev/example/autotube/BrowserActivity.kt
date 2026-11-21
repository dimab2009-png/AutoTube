package dev.example.autotube

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts

/** Сайт YouTube внутри приложения: без ключей и входа. Работает только пока авто стоит. */
class BrowserActivity : ComponentActivity() {
    private lateinit var monitor: DrivingMonitor
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var lastUrl = "https://m.youtube.com"
    private var loaded = false
    private val handler = Handler(Looper.getMainLooper())

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { monitor.start() }

    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = DrivingMonitor(this)
        window.statusBarColor = Ui.BG

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(24, 48, 24, 8)
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            setBackgroundColor(Color.BLACK)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        onBackPressedDispatcher.addCallback(this) {
            if (web.canGoBack()) web.goBack() else finish()
        }
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            monitor.start() else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        monitor.stop()
        unload()
        super.onStop()
    }

    private fun unload() {
        val u = web.url
        if (loaded && u != null && u != "about:blank") lastUrl = u
        web.loadUrl("about:blank")
        loaded = false
    }

    private fun refresh() {
        if (monitor.isParked()) {
            status.text = "Авто стоит — сайт доступен."
            web.visibility = View.VISIBLE
            if (!loaded) { loaded = true; web.loadUrl(lastUrl) }
        } else {
            status.text = "Движение или нет GPS-сигнала — сайт заблокирован."
            web.visibility = View.GONE
            if (loaded) unload()
        }
    }
}

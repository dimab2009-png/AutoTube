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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Плеер: официальный YouTube IFrame, работает только пока авто стоит. */
class PlayerActivity : ComponentActivity() {
    private lateinit var monitor: DrivingMonitor
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var videoId = ""
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
        videoId = intent.getStringExtra("id") ?: ""
        monitor = DrivingMonitor(this)

        val title = TextView(this).apply {
            text = intent.getStringExtra("title") ?: ""
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(24, 48, 24, 8)
            maxLines = 2
        }
        status = TextView(this).apply { textSize = 15f; setTextColor(Color.LTGRAY); setPadding(24, 8, 24, 8) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            setBackgroundColor(Color.BLACK)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0F0F0F.toInt())
            addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
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
        web.loadUrl("about:blank")
        loaded = false
        super.onStop()
    }

    private fun refresh() {
        if (monitor.isParked()) {
            status.text = "Авто стоит — видео доступно."
            web.visibility = View.VISIBLE
            if (!loaded && videoId.isNotEmpty()) {
                loaded = true
                web.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    "<html><body style='margin:0;background:#000'><iframe width='100%' height='100%' " +
                        "src='https://www.youtube.com/embed/$videoId?playsinline=1' frameborder='0' " +
                        "allow='autoplay; encrypted-media; fullscreen' referrerpolicy='strict-origin-when-cross-origin' " +
                        "allowfullscreen></iframe></body></html>",
                    "text/html", "utf-8", null
                )
            }
        } else {
            status.text = "Движение или нет GPS-сигнала — видео заблокировано."
            web.visibility = View.GONE
            if (loaded) { web.loadUrl("about:blank"); loaded = false }
        }
    }
}

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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Экран телефона: официальный плеер YouTube (IFrame) работает ТОЛЬКО на парковке. */
class MainActivity : ComponentActivity() {
    private lateinit var monitor: DrivingMonitor
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var videoId: String? = null
    private var loadedId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startMonitor() }

    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = DrivingMonitor(this)

        input = EditText(this).apply { hint = "Ссылка на видео YouTube"; setSingleLine() }
        val go = Button(this).apply {
            text = "Открыть"
            setOnClickListener { videoId = parseId(input.text.toString()); loadedId = null; refresh() }
        }
        status = TextView(this).apply { textSize = 18f; setPadding(24, 24, 24, 24) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            setBackgroundColor(Color.BLACK)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
            addView(input, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(go, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            startMonitor() else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        monitor.stop()
        web.loadUrl("about:blank"); loadedId = null
        super.onStop()
    }

    private fun startMonitor() = monitor.start()

    private fun refresh() {
        if (monitor.isParked()) {
            status.text = "Авто стоит — видео доступно."
            web.visibility = View.VISIBLE
            val id = videoId
            if (id != null && id != loadedId) {
                loadedId = id
                web.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    "<html><body style='margin:0;background:#000'><iframe width='100%' height='100%' " +
                        "src='https://www.youtube.com/embed/$id?playsinline=1' frameborder='0' " +
                        "allow='autoplay; encrypted-media' allowfullscreen></iframe></body></html>",
                    "text/html", "utf-8", null
                )
            }
        } else {
            status.text = "Движение или нет GPS-сигнала — видео заблокировано. Для дороги используйте аудио в Android Auto."
            web.visibility = View.GONE
            if (loadedId != null) { web.loadUrl("about:blank"); loadedId = null }
        }
    }

    private fun parseId(s: String): String? =
        Regex("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})").find(s)?.groupValues?.get(1)
            ?: s.trim().takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
}

package dev.example.autotube

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import java.util.concurrent.Executors

/** Главный экран: поиск, лайки, плейлисты аккаунта YouTube. */
class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var auth: YouTubeAuth
    private lateinit var api: YouTubeApi
    private lateinit var adapter: ItemAdapter
    private lateinit var status: TextView
    private lateinit var searchBox: EditText
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var cancelled = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        auth = YouTubeAuth(store)
        api = YouTubeApi(auth, store)
        adapter = ItemAdapter(this)

        window.statusBarColor = Ui.BG
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun pill(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(40).toFloat() }
        fun margins(lp: LinearLayout.LayoutParams) = lp.apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }

        searchBox = EditText(this).apply {
            hint = "Поиск на YouTube"
            setHintTextColor(Ui.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine()
            background = pill(Ui.CHIP)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> doSearch(); true }
        }
        fun chip(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = pill(Ui.CHIP)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(6), 0, dp(6), 0)
            textSize = 12f
            setSingleLine()
            setOnClickListener { onClick() }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, dp(4))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_logo_mark) },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(TextView(this@MainActivity).apply {
                text = "AutoTube"
                textSize = 20f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(android.view.View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(chip("Сайт YouTube") { startActivity(Intent(this@MainActivity, BrowserActivity::class.java)) },
                margins(LinearLayout.LayoutParams(WRAP_CONTENT, dp(36))))
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(searchBox, margins(LinearLayout.LayoutParams(0, dp(44), 1f)))
            addView(chip("Поиск") { doSearch() }, margins(LinearLayout.LayoutParams(WRAP_CONTENT, dp(44))))
        }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(chip("Тренды") { load { api.trending() } }, margins(LinearLayout.LayoutParams(0, dp(40), 1f)))
            addView(chip("Лайки") { load { api.liked() } }, margins(LinearLayout.LayoutParams(0, dp(40), 1f)))
            addView(chip("Плейлисты") { load { api.playlists() } }, margins(LinearLayout.LayoutParams(0, dp(40), 1f)))
            addView(chip("Аккаунт") { accountMenu() }, margins(LinearLayout.LayoutParams(0, dp(40), 1f)))
        }
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.GRAY)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            text = if (auth.isSignedIn()) "Вы вошли. Найдите видео или откройте лайки и плейлисты."
            else "Проще всего: в приложении YouTube нажмите «Поделиться» → AutoTube, или откройте «Сайт YouTube»."
        }
        val list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Ui.BG)
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> open(adapter.getItem(pos)) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(8), dp(40), dp(8), 0)
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(searchRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(tabs, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        handleShared(intent)
    }

    override fun onStart() {
        super.onStart()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onDestroy() {
        cancelled = true
        exec.shutdownNow()
        super.onDestroy()
    }

    private fun videoIdFrom(text: String): String? =
        Regex("(?:v=|youtu\\.be/|embed/|shorts/|live/)([A-Za-z0-9_-]{11})").find(text)?.groupValues?.get(1)

    /** Видео, отправленное из приложения YouTube через «Поделиться». */
    private fun handleShared(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val id = videoIdFrom(text) ?: return
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", id).putExtra("title", "Видео из YouTube"))
    }

    private fun doSearch() {
        val q = searchBox.text.toString().trim()
        if (q.isEmpty()) return
        val id = videoIdFrom(q)
        if (id != null) {
            startActivity(Intent(this, PlayerActivity::class.java).putExtra("id", id).putExtra("title", "Видео по ссылке"))
        } else {
            load { api.search(q) }
        }
    }

    private fun open(item: Item) {
        if (item.isPlaylist) load { api.playlistVideos(item.id) }
        else startActivity(
            Intent(this, PlayerActivity::class.java).putExtra("id", item.id).putExtra("title", item.title)
        )
    }

    private fun load(block: () -> List<Item>) {
        status.text = "Загрузка…"
        exec.execute {
            try {
                val result = block()
                runOnUiThread {
                    adapter.set(result)
                    status.text = if (result.isEmpty()) "Ничего не найдено" else ""
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка: ${e.message}" }
            }
        }
    }

    private fun accountMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Ввести ключи Google", "Войти в YouTube", "Выйти")) { _, which ->
                when (which) {
                    0 -> credsDialog()
                    1 -> startLogin()
                    else -> { auth.signOut(); adapter.set(emptyList()); status.text = "Вы вышли из аккаунта" }
                }
            }.show()
    }

    private fun credsDialog() {
        val key = EditText(this).apply { hint = "API-ключ (для поиска без входа)"; setText(store.apiKey); setSingleLine() }
        val id = EditText(this).apply { hint = "Client ID (для входа)"; setText(store.clientId); setSingleLine() }
        val secret = EditText(this).apply { hint = "Client secret (для входа)"; setText(store.clientSecret); setSingleLine() }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(key)
            addView(id)
            addView(secret)
        }
        AlertDialog.Builder(this)
            .setTitle("Ключи Google")
            .setView(box)
            .setPositiveButton("Сохранить") { _, _ ->
                store.apiKey = key.text.toString()
                store.clientId = id.text.toString()
                store.clientSecret = secret.text.toString()
                status.text = "Ключи сохранены."
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startLogin() {
        if (store.clientId.isBlank() || store.clientSecret.isBlank()) {
            status.text = "Сначала введите Client ID и Secret"
            credsDialog()
            return
        }
        cancelled = false
        exec.execute {
            try {
                val d = auth.requestCode()
                var dlg: AlertDialog? = null
                runOnUiThread {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", d.userCode))
                    dlg = AlertDialog.Builder(this)
                        .setTitle("Вход в YouTube")
                        .setMessage("Откройте ${d.url} и введите код (он уже скопирован):\n\n${d.userCode}")
                        .setPositiveButton("Открыть сайт", null)
                        .setNegativeButton("Отмена") { _, _ -> cancelled = true }
                        .setCancelable(false)
                        .show()
                    dlg?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
                    }
                }
                val ok = auth.pollToken(d) { cancelled }
                runOnUiThread {
                    dlg?.dismiss()
                    status.text = if (ok) "Вход выполнен" else "Вход не выполнен"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Ошибка входа: ${e.message}" }
            }
        }
    }
}

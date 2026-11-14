package com.brotik.dshcloud

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Р СћР С•Р Р…Р С”Р В°РЎРЏ Р С•Р В±РЎвЂРЎР‚РЎвЂљР С”Р В° Р Р…Р В°Р Т‘ Р С•Р В±Р В»Р В°РЎвЂЎР Р…РЎвЂ№Р С Web GUI DSH: РЎвЂљР С•РЎвЂљ Р В¶Р Вµ Р С‘Р Р…РЎвЂљР ВµРЎР‚РЎвЂћР ВµР в„–РЎРѓ, РЎвЂЎРЎвЂљР С• Р С‘ Р Р† Р В±РЎР‚Р В°РЎС“Р В·Р ВµРЎР‚Р Вµ,
 * Р Р…Р С• Р В±Р ВµР В· Р В°Р Т‘РЎР‚Р ВµРЎРѓР Р…Р С•Р в„– РЎРѓРЎвЂљРЎР‚Р С•Р С”Р С‘ Р С‘ Р Р†Р С”Р В»Р В°Р Т‘Р С•Р С”.
 *
 * Р СџР ВµРЎР‚Р Р†РЎвЂ№Р в„– Р В·Р В°Р С—РЎС“РЎРѓР С” РЎРѓР С—РЎР‚Р В°РЎв‚¬Р С‘Р Р†Р В°Р ВµРЎвЂљ Р В»Р С•Р С–Р С‘Р Р…/Р С—Р В°РЎР‚Р С•Р В»РЎРЉ basic auth Р С‘ РЎвЂ¦РЎР‚Р В°Р Р…Р С‘РЎвЂљ Р С‘РЎвЂ¦ Р Р† Р С—РЎР‚Р С‘Р Р†Р В°РЎвЂљР Р…РЎвЂ№РЎвЂ¦
 * Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р в„–Р С”Р В°РЎвЂ¦ Р С—РЎР‚Р С‘Р В»Р С•Р В¶Р ВµР Р…Р С‘РЎРЏ. Р вЂќР В°Р В»РЎРЉРЎв‚¬Р Вµ DSH РЎРѓР В°Р С РЎРѓРЎвЂљР В°Р Р†Р С‘РЎвЂљ РЎРѓР Р†Р С•РЎР‹ cookie, Р С‘ Р Р†РЎвЂ¦Р С•Р Т‘ Р Р…Р Вµ Р Р…РЎС“Р В¶Р ВµР Р…,
 * Р С—Р С•Р С”Р В° cookie Р В¶Р С‘Р Р†Р В°РЎРЏ (30 Р Т‘Р Р…Р ВµР в„–) РІР‚вЂќ РЎР‚Р С•Р Р†Р Р…Р С• Р С”Р В°Р С” Р Р† Р В±РЎР‚Р В°РЎС“Р В·Р ВµРЎР‚Р Вµ.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offline: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    private var pendingAuth: HttpAuthHandler? = null
    private var pendingFileCallback: android.webkit.ValueCallback<Array<Uri>>? = null

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val uris: Array<Uri>? = when {
            data == null -> null
            data.clipData != null -> {
                val clip = data.clipData!!
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        offline = findViewById(R.id.offline)

        applyInsets()
        setupWebView()

        findViewById<TextView>(R.id.reload).setOnClickListener { reload() }
        findViewById<TextView>(R.id.reload).setOnLongClickListener {
            Updater.checkManually(this)
            true
        }
        findViewById<TextView>(R.id.settings).setOnClickListener { showLoginDialog(firstRun = false) }
        findViewById<TextView>(R.id.title).setOnLongClickListener { showLoginDialog(firstRun = false); true }
        findViewById<View>(R.id.retry).setOnClickListener {
            offline.visibility = View.GONE
            reload()
        }

        Updater.checkQuietly(this)

        if (prefs.getString(KEY_PASS, "").isNullOrEmpty()) {
            showLoginDialog(firstRun = true)
        } else {
            loadStart()
        }
    }

    /** Полоса сверху и клавиатура не должны залезать под системные панели. */
    private fun applyInsets() {
        val root = findViewById<FrameLayout>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            root.updatePadding(
                top = bars.top,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.setBackgroundColor(0xFF101418.toInt())
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val host = url.host ?: return false
                return if (host == Uri.parse(baseUrlSafe()).host) {
                    false
                } else {
                    askExternal(url)
                    true
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String,
                realm: String,
            ) {
                val user = prefs.getString(KEY_USER, "") ?: ""
                val pass = prefs.getString(KEY_PASS, "") ?: ""
                if (user.isNotEmpty() && pass.isNotEmpty()) {
                    handler.proceed(user, pass)
                } else {
                    pendingAuth = handler
                    showLoginDialog(firstRun = true)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    offline.visibility = View.VISIBLE
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                if (newProgress >= 100) offline.visibility = View.GONE
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: android.webkit.ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                pendingFileCallback = callback
                val intent = params.createIntent()
                return try {
                    fileChooser.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    pendingFileCallback = null
                    false
                }
            }
        }

        web.setDownloadListener(DownloadListener { url, _, _, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, Uri.parse(url).lastPathSegment ?: "dsh-file")
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    addRequestHeader("User-Agent", web.settings.userAgentString)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, "Р РЋР С”Р В°РЎвЂЎР С‘Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏРІР‚В¦", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ РЎРѓР С”Р В°РЎвЂЎР В°РЎвЂљРЎРЉ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun baseUrlSafe(): String =
        prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    private fun loadStart() {
        offline.visibility = View.GONE
        web.loadUrl(baseUrlSafe())
    }

    private fun reload() {
        if (web.url == null) loadStart() else web.reload()
    }

    private fun askExternal(url: Uri) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.external_link) + "\n" + url)
            .setPositiveButton(R.string.open) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "Р СњР ВµР С”РЎС“Р Т‘Р В° Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ РЎРѓРЎРѓРЎвЂ№Р В»Р С”РЎС“", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Р вЂќР С‘Р В°Р В»Р С•Р С– Р Р†РЎвЂ¦Р С•Р Т‘Р В°. Р СџР ВµРЎР‚Р Р†РЎвЂ№Р в„– Р В·Р В°Р С—РЎС“РЎРѓР С” РЎРѓР С—РЎР‚Р В°РЎв‚¬Р С‘Р Р†Р В°Р ВµРЎвЂљ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р В»Р С•Р С–Р С‘Р Р… Р С‘ Р С—Р В°РЎР‚Р С•Р В»РЎРЉ (Р В°Р Т‘РЎР‚Р ВµРЎРѓ
     * РЎРѓР С—РЎР‚РЎРЏРЎвЂљР В°Р Р… Р Р† Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р в„–Р С”Р В°РЎвЂ¦, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р ВµР С–Р С• РЎРѓР В»РЎС“РЎвЂЎР В°Р в„–Р Р…Р С• Р Р…Р Вµ Р С‘РЎРѓР С—Р С•РЎР‚РЎвЂљР С‘РЎвЂљРЎРЉ); Р С—Р С•Р Р†РЎвЂљР С•РЎР‚Р Р…РЎвЂ№Р в„– Р Р†РЎвЂ№Р В·Р С•Р Р†
     * Р С‘Р В· Р’В«РІвЂ№Р‡Р’В» Р С—Р С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ Р Р†РЎРѓР Вµ РЎвЂљРЎР‚Р С‘ Р С—Р С•Р В»РЎРЏ.
     */
    private fun showLoginDialog(firstRun: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val urlField = EditText(this).apply {
            hint = getString(R.string.url_hint)
            setText(baseUrlSafe())
            setSelection(text.length)
            visibility = if (firstRun) View.GONE else View.VISIBLE
        }
        val userField = EditText(this).apply {
            hint = getString(R.string.login)
            setText(prefs.getString(KEY_USER, DEFAULT_USER) ?: DEFAULT_USER)
        }
        val passField = EditText(this).apply {
            hint = getString(R.string.password)
            setText(prefs.getString(KEY_PASS, "") ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(urlField)
        container.addView(userField)
        container.addView(passField)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.login_title)
            .setMessage(R.string.login_hint)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(if (firstRun) R.string.cancel else R.string.cancel) { _, _ ->
                if (firstRun && pendingAuth != null) {
                    pendingAuth?.cancel()
                    pendingAuth = null
                    offline.visibility = View.VISIBLE
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val user = userField.text.toString().trim()
                val pass = passField.text.toString()
                val url = urlField.text.toString().trim().ifEmpty { DEFAULT_URL }
                if (user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, R.string.bad_password, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!url.startsWith("http://") && !url.startsWith("https://") || !url.contains(".")) {
                    Toast.makeText(this, R.string.bad_url, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString(KEY_URL, url)
                    .putString(KEY_USER, user)
                    .putString(KEY_PASS, pass)
                    .apply()
                dialog.dismiss()
                val pending = pendingAuth
                pendingAuth = null
                if (pending != null) {
                    pending.proceed(user, pass)
                } else {
                    Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    loadStart()
                }
            }
        }
        dialog.setCancelable(!firstRun)
        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "dsh"
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val DEFAULT_USER = "rioo"
        private const val DEFAULT_URL = "https://dsh-cloud-production-706a3ba8.up.railway.app/login"
    }
}


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
import android.webkit.JavascriptInterface
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
 * Тонкая обёртка над облачным Web GUI DSH: тот же интерфейс, что и в браузере,
 * но без адресной строки и вкладок.
 *
 * Первый запуск спрашивает логин/пароль basic auth (поля пустые, чтобы ничего
 * не подставлялось заранее) и хранит их в приватных настройках приложения.
 * Дальше DSH сам ставит свою cookie, и вход не нужен, пока она живая.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offline: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    private var pendingAuth: HttpAuthHandler? = null
    private var pendingFileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private var inForeground = false
    private var lastNotified = ""
    private var lastNotifiedAt = 0L

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.notifications_blocked, Toast.LENGTH_LONG).show()
            }
        }

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

        Notifier.ensureChannels(this)
        askNotificationPermission()

        applyInsets()
        setupWebView()

        findViewById<TextView>(R.id.reload).setOnClickListener { reload() }
        findViewById<TextView>(R.id.reload).setOnLongClickListener {
            Updater.checkManually(this)
            true
        }
        findViewById<TextView>(R.id.settings).setOnClickListener { showLoginDialog(firstRun = false) }
        findViewById<TextView>(R.id.settings).setOnLongClickListener {
            dumpDomInfo()
            true
        }
        findViewById<TextView>(R.id.title).setOnLongClickListener {
            showLoginDialog(firstRun = false); true
        }
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
            root.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
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
        web.addJavascriptInterface(Bridge(), "DshBridge")

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
                injectSaver()
                injectReplyWatcher()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) offline.visibility = View.VISIBLE
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
                return try {
                    fileChooser.launch(params.createIntent())
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
                    setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                        Uri.parse(url).lastPathSegment ?: "dsh-file",
                    )
                    addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    addRequestHeader("User-Agent", web.settings.userAgentString)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.save_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        })

        // Долгое нажатие по картинке: у DSH вложения приходят как blob:-, их
        // системный загрузчик не берёт. Текст при этом выделяется как обычно.
        web.setOnLongClickListener {
            val hit = web.hitTestResult
            val url = hit.extra
            val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && (url?.contains("blob:") == true))
            if (isImage && !url.isNullOrBlank()) {
                askImageAction(url)
                true
            } else {
                false
            }
        }
    }

    private fun askImageAction(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.image_actions)
            .setItems(arrayOf(getString(R.string.save_image), getString(R.string.share_image))) { _, which ->
                saveFromUrl(url, share = which == 1)
            }
            .show()
    }

    /** Просит страницу прочитать blob/http/data и отдать байты мосту. */
    private fun saveFromUrl(url: String, share: Boolean) {
        val quoted = org.json.JSONObject.quote(url)
        web.evaluateJavascript(
            "(function(){return window.__dshFind ? window.__dshFind($quoted) : Promise.reject('no helper');})()",
            null,
        )
        val js = "window.__dshFind(" + quoted + ").then(function(p){" +
            "var c=p.data.indexOf(',');DshBridge.save(p.name,p.mime,p.data.substring(c+1)," + share + ");" +
            "}).catch(function(e){DshBridge.fail(String(e));});"
        web.evaluateJavascript(js, null)
    }

    /** JS-хелпер: читает blob/http/data и возвращает dataURL + mime + имя. */
    private fun injectSaver() {
        val js = """
            (function(){
              if (window.__dshFind) return;
              window.__dshFind = function(url){
                return fetch(url).then(function(r){
                  if (!r.ok) throw new Error('http ' + r.status);
                  return r.blob();
                }).then(function(b){
                  return new Promise(function(resolve, reject){
                    var fr = new FileReader();
                    fr.onload = function(){
                      var name = '', mime = b.type || 'application/octet-stream';
                      try { name = decodeURIComponent((url.split('?')[0].split('/').pop()) || ''); } catch (e) { name = ''; }
                      if (!name || name.length > 80) name = 'dsh-' + Date.now();
                      resolve({ name: name, mime: mime, data: String(fr.result) });
                    };
                    fr.onerror = function(){ reject('read'); };
                    fr.readAsDataURL(b);
                  });
                });
              };
              document.addEventListener('click', function(e){
                var node = e.target;
                var a = node && node.closest ? node.closest('a[download]') : null;
                if (!a) return;
                var href = a.getAttribute('href') || '';
                if (href.indexOf('blob:') !== 0 && href.indexOf('data:') !== 0) return;
                e.preventDefault();
                var label = a.getAttribute('download') || 'dsh-file';
                window.__dshFind(href).then(function(p){
                  var c = p.data.indexOf(',');
                  DshBridge.save(label || p.name, p.mime, p.data.substring(c + 1), false);
                }).catch(function(){ DshBridge.fail('download'); });
              }, true);
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    /**
     * Слежение за ответами агента. Точных селекторов DSH не публикует, поэтому
     * пробуем набор типовых, а в крайнем случае — прирост текста основного
     * контейнера. Решение «уведомлять или нет» принимает Kotlin: только когда
     * приложение не на переднем плане, с дедупом и паузой.
     */
    private fun injectReplyWatcher() {
        val js = """
            (function(){
              if (window.__dshReplyWatch) return;
              window.__dshReplyWatch = true;
              var lastText = '';
              var timer = null;
              var sels = [
                '[data-message-role="assistant"]',
                '[data-role="assistant"]',
                '[class*="assistant"]',
                '[class*="message-assistant"]',
                '[class*="bubble-assistant"]'
              ];
              function candidate(){
                for (var i = 0; i < sels.length; i++) {
                  var nodes = document.querySelectorAll(sels[i]);
                  if (nodes.length) {
                    var t = (nodes[nodes.length - 1].innerText || '').trim();
                    if (t) return t;
                  }
                }
                var main = document.querySelector('main') || document.body;
                var all = (main.innerText || '').trim();
                return all.length > 600 ? all.slice(-600) : all;
              }
              function scan(){
                var t = candidate();
                if (!t || t === lastText) return;
                var prev = lastText;
                lastText = t;
                if (!prev) return;
                if (t.length <= prev.length) return;
                var tail = t.slice(-400);
                if (tail.indexOf(prev.slice(-80)) === -1) return;
                DshBridge.replySeen(t.slice(-260));
              }
              var obs = new MutationObserver(function(){
                clearTimeout(timer);
                timer = setTimeout(scan, 1200);
              });
              obs.observe(document.body, { childList: true, subtree: true, characterData: true });
              setInterval(scan, 4000);
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    /** Диагностика селекторов: пишем в logcat по долгому нажатию «⋯». */
    private fun dumpDomInfo() {
        val js = """
            (function(){
              var out = { counts: {}, sample: '' };
              var sels = [
                '[data-message-role="assistant"]','[data-role="assistant"]',
                '[class*="assistant"]','[class*="message-assistant"]','[class*="bubble-assistant"]',
                '[class*="message"]','[class*="markdown"]'
              ];
              for (var i = 0; i < sels.length; i++) out.counts[sels[i]] = document.querySelectorAll(sels[i]).length;
              var main = document.querySelector('main') || document.body;
              out.sample = ((main.innerText || '')).slice(-400);
              return JSON.stringify(out);
            })();
        """.trimIndent()
        web.evaluateJavascript(js) { result ->
            android.util.Log.i("DshDom", "selectors: $result")
            Toast.makeText(this, "Диагностика DOM записана в logcat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun save(name: String?, mime: String?, base64: String?, share: Boolean) {
            val n = name ?: "dsh-file"
            val m = mime ?: "application/octet-stream"
            val data = base64 ?: ""
            runOnUiThread {
                if (data.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.image_not_ready, Toast.LENGTH_SHORT).show()
                } else {
                    Saver.saveBase64(this@MainActivity, n, m, data, share)
                }
            }
        }

        @JavascriptInterface
        fun fail(reason: String?) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, R.string.image_not_ready, Toast.LENGTH_SHORT).show()
            }
        }

        /** Пришёл прирост текста ответа: уведомляем, только если мы в фоне. */
        @JavascriptInterface
        fun replySeen(preview: String?) {
            val text = (preview ?: "").trim()
            if (text.length < 40) return
            val now = System.currentTimeMillis()
            if (inForeground || text == lastNotified || now - lastNotifiedAt < 6000) return
            lastNotified = text
            lastNotifiedAt = now
            runOnUiThread { Notifier.showReply(this@MainActivity, text) }
        }
    }

    private fun baseUrlSafe(): String = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

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
                    Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Диалог входа. Первый запуск спрашивает только логин и пароль, причём поля
     * пустые — ничего не подставляем. Адрес сервера живёт в настройках («⋯»),
     * чтобы его нельзя было случайно испортить.
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
            setText(prefs.getString(KEY_USER, "") ?: "")
        }
        val passField = EditText(this).apply {
            hint = getString(R.string.password)
            setText(prefs.getString(KEY_PASS, "") ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(urlField)
        container.addView(userField)
        container.addView(passField)

        // Когда вход уже сохранён — даём кнопку проверки уведомлений.
        if (!firstRun) {
            container.addView(
                android.widget.Button(this).apply {
                    text = getString(R.string.test_notification)
                    setOnClickListener {
                        Notifier.test(this@MainActivity)
                        Toast.makeText(this@MainActivity, R.string.reply_test, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.login_title)
            .setMessage(R.string.login_hint)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (firstRun && pendingAuth != null) {
                    pendingAuth?.cancel()
                    pendingAuth = null
                    offline.visibility = View.VISIBLE
                }
            }
        // Выход доступен только когда вход уже сохранён.
        if (!firstRun) builder.setNeutralButton(R.string.logout) { _, _ -> confirmLogout() }
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val user = userField.text.toString().trim()
                val pass = passField.text.toString()
                val url = urlField.text.toString().trim().ifEmpty { DEFAULT_URL }
                if (user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, R.string.bad_password, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if ((!url.startsWith("http://") && !url.startsWith("https://")) || !url.contains(".")) {
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

    /** Выход: чистим сохранённые креды, cookie DSH и кэш страницы. */
    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_hint)
            .setPositiveButton(R.string.logout) { _, _ -> doLogout() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doLogout() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASS).apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        web.clearCache(true)
        web.clearHistory()
        web.clearFormData()
        android.webkit.WebStorage.getInstance().deleteAllData()
        web.loadUrl("about:blank")
        Toast.makeText(this, R.string.logged_out, Toast.LENGTH_LONG).show()
        showLoginDialog(firstRun = true)
    }

    override fun onResume() {
        super.onResume()
        inForeground = true
        // Вернулись в приложение — постоянный индикатор больше не нужен.
        stopService(Intent(this, LinkService::class.java))
    }

    override fun onPause() {
        super.onPause()
        inForeground = false
        CookieManager.getInstance().flush()
        // Уходим в фон: держим процесс живым, чтобы наблюдение за ответами работало.
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, LinkService::class.java))
            } else {
                startService(Intent(this, LinkService::class.java))
            }
        } catch (e: Exception) {
            android.util.Log.w("DshApp", "link service did not start: ${e.message}")
        }
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
        private const val DEFAULT_URL = "https://dsh-cloud-production-706a3ba8.up.railway.app/login"
    }
}

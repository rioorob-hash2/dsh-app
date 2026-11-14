package com.brotik.dshcloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновления через GitHub Releases: спрашивает последний тег, сравнивает с
 * установленной версией, скачивает APK и передаёт его системному установщику.
 *
 * Никаких токенов: репозиторий публичный, APK без пароля бесполезен — вход в
 * DSH закрыт basic auth.
 */
object Updater {

    private const val API = "https://api.github.com/repos/rioorob-hash2/dsh-app/releases/latest"
    private val main = Handler(Looper.getMainLooper())

    private data class Release(val version: String, val apkUrl: String)

    /** Тихая проверка при запуске: диалог только если есть что ставить. */
    fun checkQuietly(activity: MainActivity) {
        Thread {
            val release = fetchLatest()
            if (release == null || !isNewer(release.version, currentVersion(activity))) return@Thread
            main.post { offerUpdate(activity, release) }
        }.start()
    }

    /** Ручная проверка из меню: всегда отвечает (обновление / актуально / ошибка). */
    fun checkManually(activity: MainActivity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val release = fetchLatest()
            main.post {
                when {
                    release == null -> toast(activity, activity.getString(R.string.update_failed))
                    isNewer(release.version, currentVersion(activity)) -> offerUpdate(activity, release)
                    else -> toast(
                        activity,
                        activity.getString(R.string.update_none, currentVersion(activity)),
                    )
                }
            }
        }.start()
    }

    private fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun toast(activity: MainActivity, text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }

    private fun fetchLatest(): Release? = try {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", "dsh-app")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode != 200) {
            conn.disconnect()
            null
        } else {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            if (tag.isBlank() || apk.isNullOrBlank()) null else Release(tag, apk)
        }
    } catch (e: Exception) {
        null
    }

    /** "1.5" новее "1.4"; сравнение по числовым частям, без семантических изысков. */
    private fun isNewer(remote: String, current: String): Boolean {
        val a = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val b = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun offerUpdate(activity: MainActivity, release: Release) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title))
            .setMessage(activity.getString(R.string.update_found, release.version, currentVersion(activity)))
            .setPositiveButton(R.string.update_now) { _, _ -> download(activity, release) }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun download(activity: MainActivity, release: Release) {
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "dsh-app")
                conn.instanceFollowRedirects = true
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "dsh-${release.version}.apk")
                conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                main.post { install(activity, target) }
            } catch (e: Exception) {
                main.post { Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun install(activity: MainActivity, apk: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
        }
    }
}

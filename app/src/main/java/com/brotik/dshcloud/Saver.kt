package com.brotik.dshcloud

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Сохранение и «поделиться» для картинок из чата.
 *
 * DSH отдаёт вложения как `blob:`-URL, которые Android-загрузчик не умеет
 * тянуть сам: страница читает blob через fetch и передаёт сюда base64.
 */
object Saver {

    fun saveBase64(context: Context, name: String, mime: String, base64: String, share: Boolean) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (share) {
                shareBytes(context, name, mime, bytes)
            } else {
                val target = saveToDownloads(context, name, mime, bytes)
                toast(context, context.getString(R.string.saved_to_downloads, target))
            }
        } catch (e: Exception) {
            toast(context, context.getString(R.string.save_failed, e.message ?: ""))
        }
    }

    private fun safeName(name: String, mime: String): String {
        val fallbackExt = when {
            mime.contains("png") -> ".png"
            mime.contains("jpeg") || mime.contains("jpg") -> ".jpg"
            mime.contains("webp") -> ".webp"
            mime.contains("gif") -> ".gif"
            mime.contains("pdf") -> ".pdf"
            else -> ".bin"
        }
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank {
            "dsh-" + System.currentTimeMillis() + fallbackExt
        }
        return if (cleaned.contains('.')) cleaned else cleaned + fallbackExt
    }

    private fun saveToDownloads(context: Context, name: String, mime: String, bytes: ByteArray): String {
        val fileName = safeName(name, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("не удалось создать файл в Загрузках")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return fileName
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return fileName
    }

    private fun shareBytes(context: Context, name: String, mime: String, bytes: ByteArray) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, safeName(name, mime))
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}

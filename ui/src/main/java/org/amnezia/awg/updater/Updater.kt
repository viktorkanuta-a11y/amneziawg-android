/*
 * ВыпейН, Ш.19: проверка и установка обновлений.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.updater

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Отдаёт скачанный APK установщику Android. */
class UpdateFileProvider : FileProvider()

object Updater {
    // ИСТОЧНИК ОБНОВЛЕНИЙ — единственная строка, которую меняют при переезде на сайт.
    // В источнике лежат два файла: version.txt (одно число — номер версии) и vipein.apk.
    private const val SOURCE = "https://github.com/bosscorp84/vipein/releases/latest/download/"

    private const val VERSION_FILE = "version.txt"
    private const val APK_FILE = "vipein.apk"

    /** Молча спрашивает номер версии. Если новее установленной — окно «Есть обновление». */
    fun check(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val remote = withContext(Dispatchers.IO) { runCatching { fetchVersion() }.getOrNull() }
            if (remote == null || remote <= BuildConfig.VERSION_CODE || activity.isFinishing) return@launch
            MaterialAlertDialogBuilder(activity)
                .setTitle("Есть обновление")
                .setMessage("Вышла новая версия ВыпейН. Установить?")
                .setPositiveButton("Обновить") { _, _ -> download(activity) }
                .setNegativeButton("Позже", null)
                .show()
        }
    }

    private fun fetchVersion(): Int? {
        val c = URL(SOURCE + VERSION_FILE).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.useCaches = false
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) return null
            return c.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()
        } finally {
            c.disconnect()
        }
    }

    /** Качает APK своим кодом в папку приложения и открывает системный установщик. */
    private fun download(activity: AppCompatActivity) {
        Toast.makeText(activity, "Загружаю обновление…", Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { fetchApk(activity) }.getOrNull() }
            if (file == null) {
                Toast.makeText(activity, "Не удалось загрузить обновление. Попробуйте позже", Toast.LENGTH_LONG).show()
                return@launch
            }
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { activity.startActivity(intent) }
        }
    }

    private fun fetchApk(activity: AppCompatActivity): File? {
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val part = File(dir, "$APK_FILE.part")
        val apk = File(dir, APK_FILE)
        val c = URL(SOURCE + APK_FILE).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.useCaches = false
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) return null
            c.inputStream.use { input -> part.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            c.disconnect()
        }
        apk.delete()
        return if (part.renameTo(apk)) apk else null
    }
}

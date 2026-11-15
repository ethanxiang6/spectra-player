package com.spectra.player.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK into the app's own storage and hands it to Android's installer.
 *
 * Android only accepts the file as an update when it is this same app signed with the same key,
 * so a tampered or foreign file is refused by the system itself. On Android 12 and later an app
 * may update itself without a confirmation prompt; on older versions (or the first time, when
 * "install unknown apps" has to be allowed for Spectra) Android shows its own prompt.
 */
class UpdateInstaller(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "updates").apply { mkdirs() }

    private fun fileFor(tag: String) = File(dir, "Spectra-${tag.filter { it.isLetterOrDigit() || it == '.' || it == '-' }}.apk")

    /** A fully downloaded APK for [info] that really is a newer Spectra, or null. */
    fun downloaded(info: UpdateInfo): File? = fileFor(info.tag).takeIf { it.isFile && isNewerSpectra(it) }

    /** The newest valid update already downloaded, whatever release it is; older leftovers are deleted. */
    fun pending(): File? {
        val files = dir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
        val good = files.filter { isNewerSpectra(it) }
        (files.toList() - good.toSet()).forEach { it.delete() }
        dir.listFiles { f -> f.name.endsWith(".part") }.orEmpty().forEach { it.delete() }
        return good.maxByOrNull { it.lastModified() }
    }

    /** Removes every downloaded update file. */
    fun clear() {
        dir.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun isNewerSpectra(file: File): Boolean = try {
        val pkg = context.packageManager.getPackageArchiveInfo(file.path, 0)
        pkg != null && pkg.packageName == context.packageName &&
            UpdateChecker.isNewer(pkg.versionName.orEmpty(), com.spectra.player.BuildConfig.VERSION_NAME)
    } catch (e: Exception) {
        false
    }

    /** Downloads the release's APK, reporting 0..100. Returns the file, or null when it failed. */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: return@withContext null
        // Only ever fetch from the project's own GitHub releases.
        if (!url.startsWith("https://github.com/ethanxiang6/spectra-player/releases/download/")) return@withContext null
        val target = fileFor(info.tag)
        val part = File(dir, target.name + ".part")
        try {
            var connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Spectra")
            // GitHub redirects the download to its file host; follow that by hand if needed.
            var hops = 0
            while (connection.responseCode in 301..308 && hops++ < 4) {
                val next = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                if (!next.startsWith("https://")) return@withContext null
                connection = URL(next).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
            }
            if (connection.responseCode != 200) return@withContext null
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != last) {
                                last = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            connection.disconnect()
            if (!part.renameTo(target)) return@withContext null
            if (isNewerSpectra(target)) target else null.also { target.delete() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed: ${e.javaClass.simpleName}")
            part.delete()
            null
        }
    }

    /** Gives the APK to Android's installer. The outcome arrives on [results]. */
    suspend fun install(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("spectra", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(
                    context, id, Intent(context, UpdateResultReceiver::class.java), flags,
                )
                session.commit(callback.intentSender)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the update: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "SpectraUpdate"

        private val _results = MutableSharedFlow<String>(extraBufferCapacity = 4)
        /** Problems reported by Android's installer, as a sentence for the user. */
        val results: SharedFlow<String> = _results.asSharedFlow()

        internal fun report(message: String) {
            _results.tryEmit(message)
        }
    }
}

/** Hears back from Android's installer: shows its prompt when it wants one, reports failures. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                try {
                    if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    UpdateInstaller.report("Android couldn't show the update prompt. Open Spectra and tap Install again.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is replaced; nothing left to do here
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstaller.report("The update was cancelled.")
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateInstaller.report("Android couldn't install the update (${why ?: "code $status"}).")
            }
        }
    }
}

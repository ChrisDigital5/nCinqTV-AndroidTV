package app.ncinq.tv.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.ncinq.tv.data.UpdateInfo
import app.ncinq.tv.data.UpdateInstallState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class UpdateInstaller(
    private val activity: ComponentActivity,
    private val onState: (UpdateInstallState) -> Unit,
) {
    private val downloadManager = activity.getSystemService(DownloadManager::class.java)
    private var downloadId: Long? = null
    private var pendingFile: File? = null
    private var progressJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
            pendingFile?.takeIf(File::exists)?.let {
                onState(UpdateInstallState(true, 100, "Download complete. Opening installer...", true))
                install(it)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun download(update: UpdateInfo) {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val file = File(directory, "ncinq-tv-${update.versionName}.apk")
        if (file.exists()) file.delete()
        pendingFile = file

        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("nCinqTV ${update.versionName}")
            .setDescription("Downloading Android TV update")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        downloadId = downloadManager.enqueue(request)
        onState(UpdateInstallState(true, 0, "Downloading nCinqTV ${update.versionName}"))
        watchProgress()
        Toast.makeText(activity, "Downloading update", Toast.LENGTH_SHORT).show()
    }

    fun onResume() {
        val file = pendingFile ?: return
        if (file.exists() && canInstallPackages()) install(file)
    }

    private fun install(file: File) {
        if (!canInstallPackages()) {
            onState(UpdateInstallState(true, 100, "Allow nCinqTV to install updates, then return to the app.", true))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startActivity(Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ))
                Toast.makeText(activity, "Allow nCinqTV to install this update", Toast.LENGTH_LONG).show()
            }
            return
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            file,
        )
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        onState(UpdateInstallState(true, 100, "Confirm the Android installation prompt.", true))
    }

    private fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
    }

    fun dispose() {
        progressJob?.cancel()
        runCatching { activity.unregisterReceiver(receiver) }
    }

    private fun watchProgress() {
        progressJob?.cancel()
        val id = downloadId ?: return
        progressJob = activity.lifecycleScope.launch {
            while (isActive) {
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
                cursor.use {
                    if (it.moveToFirst()) {
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val progress = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                        val message = when (status) {
                            DownloadManager.STATUS_PAUSED -> "Download paused. Waiting for the network..."
                            DownloadManager.STATUS_FAILED -> "The update download failed. Try again."
                            else -> "Downloading update"
                        }
                        onState(UpdateInstallState(status != DownloadManager.STATUS_FAILED, progress, message))
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) return@launch
                    }
                }
                delay(500)
            }
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

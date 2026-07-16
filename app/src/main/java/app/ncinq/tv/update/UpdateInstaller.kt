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
import java.io.File

class UpdateInstaller(private val activity: ComponentActivity) {
    private val downloadManager = activity.getSystemService(DownloadManager::class.java)
    private var downloadId: Long? = null
    private var pendingFile: File? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
            pendingFile?.takeIf(File::exists)?.let(::install)
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
        Toast.makeText(activity, "Downloading update", Toast.LENGTH_SHORT).show()
    }

    fun onResume() {
        val file = pendingFile ?: return
        if (file.exists() && canInstallPackages()) install(file)
    }

    private fun install(file: File) {
        if (!canInstallPackages()) {
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
    }

    private fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
    }

    fun dispose() {
        runCatching { activity.unregisterReceiver(receiver) }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

package com.maktas.ytconverter.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.maktas.ytconverter.MainActivity
import com.maktas.ytconverter.R
import com.maktas.ytconverter.data.AudioFormat
import com.maktas.ytconverter.data.DownloadFormat
import com.maktas.ytconverter.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Runs a download in a foreground service so it survives the app being
 * backgrounded or the screen turning off. Shows an ongoing progress notification
 * with a Cancel action; progress is published to [DownloadController] for the UI.
 * The format is passed per-download via the intent; embeds/quality come from settings.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var lastPercent = -1
    private var kindLabel = "audio"
    private var currentTitle: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> DownloadController.requestCancel() // running job winds down & stops us
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank() || job?.isActive == true) {
                    if (job?.isActive != true) stopSelf()
                } else {
                    val format = runCatching {
                        DownloadFormat.valueOf(intent.getStringExtra(EXTRA_FORMAT) ?: "")
                    }.getOrDefault(DownloadFormat.M4A)
                    startDownload(url, format, intent.getStringExtra(EXTRA_TITLE))
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, format: DownloadFormat, title: String?) {
        kindLabel = if (format == DownloadFormat.MP4) "video" else "audio"
        currentTitle = title
        createChannel()
        goForeground(buildNotification(text = "Starting…", progress = 0, indeterminate = true))
        lastPercent = -1
        val processId = UUID.randomUUID().toString()
        DownloadController.onStart(processId, title)

        job = scope.launch {
            val settings = SettingsRepository(applicationContext).settings.first()
            val result = when (format) {
                DownloadFormat.MP4 -> Downloader.downloadVideo(
                    applicationContext, url, processId,
                    settings.videoQuality, settings.embedThumbnail, settings.embedMetadata,
                    ::handleProgress,
                )
                DownloadFormat.M4A -> Downloader.downloadAudio(
                    applicationContext, url, processId,
                    AudioFormat.M4A, settings.embedThumbnail, settings.embedMetadata,
                    ::handleProgress,
                )
                DownloadFormat.MP3 -> Downloader.downloadAudio(
                    applicationContext, url, processId,
                    AudioFormat.MP3, settings.embedThumbnail, settings.embedMetadata,
                    ::handleProgress,
                )
            }
            DownloadController.onFinished(result)
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            if (!DownloadController.wasCancelled) {
                result.fold(
                    onSuccess = { notifyDone("Download complete", it.displayName) },
                    onFailure = { notifyDone("Download failed", ErrorMapper.friendly(it.message)) },
                )
            }
            stopSelf()
        }
    }

    private fun handleProgress(percent: Float, eta: Long) {
        DownloadController.onProgress(percent, eta)
        val p = percent.toInt()
        if (p != lastPercent) {
            lastPercent = p
            notifyProgress(p)
        }
    }

    private fun goForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled(); FGS runs regardless
    private fun notifyProgress(percent: Int) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            NOTIF_ID,
            buildNotification(text = "$percent%", progress = percent, indeterminate = percent <= 0)
        )
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    private fun notifyDone(title: String, text: String) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(openApp)
            .build()
        manager.notify(DONE_NOTIF_ID, notification)
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle ?: "Downloading $kindLabel")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .addAction(0, "Cancel", cancelIntent)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shows download progress" }
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.maktas.ytconverter.action.DOWNLOAD"
        const val ACTION_CANCEL = "com.maktas.ytconverter.action.CANCEL"
        const val EXTRA_URL = "com.maktas.ytconverter.extra.URL"
        const val EXTRA_FORMAT = "com.maktas.ytconverter.extra.FORMAT"
        const val EXTRA_TITLE = "com.maktas.ytconverter.extra.TITLE"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 1001
        private const val DONE_NOTIF_ID = 1002
    }
}

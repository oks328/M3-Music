@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package io.github.oks328.m3music.downloads

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import io.github.oks328.m3music.MainActivity
import io.github.oks328.m3music.R

class MusicDownloadService : DownloadService(2001, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "music_downloads", R.string.download_channel_name, 0) {
    override fun getDownloadManager(): DownloadManager = OfflineDownloads.get(this).manager
    override fun getScheduler(): Scheduler = PlatformScheduler(this, 2002)
    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return DownloadNotificationHelper(this, "music_downloads").buildProgressNotification(
            this, R.drawable.ic_download, open, "Saving music for offline listening", downloads, notMetRequirements)
    }
}

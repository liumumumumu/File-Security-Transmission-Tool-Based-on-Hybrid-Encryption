package com.filesecuritytool.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.filesecuritytool.android.FileSecurityToolApplication
import com.filesecuritytool.android.MainActivity
import com.filesecuritytool.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FileTaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator by lazy {
        (application as FileSecurityToolApplication).container.fileTasks
    }
    private lateinit var notifications: NotificationManager
    private var observation: Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "File tasks", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIFICATION_ID, notification("Preparing file task", 0, false))
        observation = scope.launch {
            coordinator.state.collectLatest(::render)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) coordinator.cancel()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observation?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun render(state: FileTaskState) {
        when (state) {
            is FileTaskState.Running -> {
                val percent = if (state.totalBytes <= 0) 0
                else ((state.processedBytes * 100) / state.totalBytes).toInt().coerceIn(0, 100)
                notifications.notify(
                    NOTIFICATION_ID,
                    notification("${state.operation.name.lowercase()}: ${state.inputName}", percent, true)
                )
            }
            is FileTaskState.Completed -> {
                notifications.notify(
                    NOTIFICATION_ID,
                    notification("Completed: ${state.outputName}", 100, false)
                )
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            is FileTaskState.Failed -> {
                val message = if (state.operation == Operation.DECRYPT) {
                    getString(com.filesecuritytool.android.R.string.decryption_failed_short)
                } else state.message
                notifications.notify(NOTIFICATION_ID, notification(message, 0, false))
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            FileTaskState.Cancelled -> {
                notifications.notify(NOTIFICATION_ID, notification("File task cancelled", 0, false))
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            FileTaskState.Idle -> Unit
        }
    }

    private fun notification(text: String, progress: Int, cancellable: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FileSecurityTool")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(cancellable)
        if (cancellable) {
            val cancelIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, FileTaskService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setProgress(100, progress, false)
                .addAction(0, "Cancel", cancelIntent)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "file_tasks"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_CANCEL = "com.filesecuritytool.android.CANCEL_FILE_TASK"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FileTaskService::class.java))
        }
    }
}

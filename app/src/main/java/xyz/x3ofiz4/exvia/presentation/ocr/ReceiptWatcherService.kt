package xyz.x3ofiz4.exvia.presentation.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import xyz.x3ofiz4.exvia.app.ExviaApplication
import xyz.x3ofiz4.exvia.data.ocr.ReceiptScanner

/**
 * Background watcher service that monitors configured SAF folders for receipt images.
 * Uses ContentObserver and periodic polling to automatically trigger ML Kit text
 * recognition and update Exvia records when new files are created.
 */
class ReceiptWatcherService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val contentObservers = mutableListOf<ContentObserver>()

    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCAN_NOW -> {
                serviceScope.launch {
                    ReceiptScanner.scanAllEnabled(applicationContext)
                }
                return START_STICKY
            }
            ACTION_START -> {
                startForegroundNotification()
                setupFolderObservers()
                startPeriodicPolling()
            }
        }

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = buildOngoingNotification("Monitoring active receipt folders...")
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // Background start restrictions on Android 14+
        }
    }

    private fun buildOngoingNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Receipt OCR Automaton")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun setupFolderObservers() {
        unregisterObservers()

        val app = applicationContext as? ExviaApplication ?: return
        val settings = app.container.settingsStore.load()
        val enabledTemplates = settings.ocrTemplates.filter { it.enabled && it.folderUri.isNotBlank() }

        if (enabledTemplates.isEmpty()) {
            stopSelf()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        for (template in enabledTemplates) {
            try {
                val uri = Uri.parse(template.folderUri)
                val observer = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean, changedUri: Uri?) {
                        serviceScope.launch {
                            ReceiptScanner.scanTemplate(applicationContext, template)
                        }
                    }
                }
                contentResolver.registerContentObserver(uri, true, observer)
                contentObservers.add(observer)
            } catch (_: Exception) {
                // Ignore observer registration failures on non-standard providers
            }
        }
    }

    private fun startPeriodicPolling() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive) {
                try {
                    ReceiptScanner.scanAllEnabled(applicationContext)
                } catch (_: Exception) {
                }
                delay(60_000L) // Poll every 60 seconds
            }
        }
    }

    private fun unregisterObservers() {
        contentObservers.forEach { observer ->
            try {
                contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
        contentObservers.clear()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Receipt OCR Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background folder monitoring for automatic receipt scanning"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterObservers()
        pollJob?.cancel()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "xyz.x3ofiz4.exvia.ocr.START_WATCHER"
        const val ACTION_STOP = "xyz.x3ofiz4.exvia.ocr.STOP_WATCHER"
        const val ACTION_SCAN_NOW = "xyz.x3ofiz4.exvia.ocr.SCAN_NOW"

        const val CHANNEL_ID = "exvia_ocr_watcher"
        const val NOTIFICATION_ID = 9182

        fun updateServiceState(context: Context) {
            val app = context.applicationContext as? ExviaApplication ?: return
            val settings = app.container.settingsStore.load()
            val hasEnabled = settings.ocrTemplates.any { it.enabled && it.folderUri.isNotBlank() }

            val intent = Intent(context, ReceiptWatcherService::class.java).apply {
                action = if (hasEnabled) ACTION_START else ACTION_STOP
            }

            try {
                if (hasEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.stopService(intent)
                }
            } catch (_: Exception) {
                // If background start is disallowed by OS, fall back gracefully
            }
        }

        fun triggerScanNow(context: Context) {
            val intent = Intent(context, ReceiptWatcherService::class.java).apply {
                action = ACTION_SCAN_NOW
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}

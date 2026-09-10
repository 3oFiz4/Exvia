package xyz.x3ofiz4.exvia.data.ocr

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import xyz.x3ofiz4.exvia.presentation.common.AppFonts

/**
 * Manages the on-demand Google Play Services OCR Module (Plug-in/Add-on).
 * Keeps the initial app installation size minimal by downloading the ~15 MB
 * Text Recognition model only when the user requests or activates OCR.
 */
object OcrPluginManager {

    enum class PluginStatus {
        UNKNOWN,
        NOT_INSTALLED,
        DOWNLOADING,
        INSTALLED,
        GMS_UNAVAILABLE,
    }

    var currentStatus: PluginStatus = PluginStatus.UNKNOWN
        private set

    fun isGmsAvailable(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }

    /**
     * Checks if the optional OCR module is already downloaded on the device.
     */
    fun checkPluginInstalled(
        context: Context,
        onResult: (isInstalled: Boolean, status: PluginStatus) -> Unit,
    ) {
        if (!isGmsAvailable(context)) {
            currentStatus = PluginStatus.GMS_UNAVAILABLE
            onResult(false, PluginStatus.GMS_UNAVAILABLE)
            return
        }

        try {
            val client = ModuleInstall.getClient(context)
            val recognizer = ReceiptOcrEngine.recognizer

            client.areModulesAvailable(recognizer)
                .addOnSuccessListener { response ->
                    val installed = response.areModulesAvailable()
                    currentStatus = if (installed) PluginStatus.INSTALLED else PluginStatus.NOT_INSTALLED
                    onResult(installed, currentStatus)
                }
                .addOnFailureListener {
                    currentStatus = PluginStatus.NOT_INSTALLED
                    onResult(false, PluginStatus.NOT_INSTALLED)
                }
        } catch (_: Exception) {
            currentStatus = PluginStatus.NOT_INSTALLED
            onResult(false, PluginStatus.NOT_INSTALLED)
        }
    }

    /**
     * Initiates the on-demand installation / download of the OCR model.
     */
    fun installPlugin(
        context: Context,
        onProgress: ((percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
        onCompleted: (success: Boolean, message: String) -> Unit,
    ) {
        if (!isGmsAvailable(context)) {
            onCompleted(false, "Google Play Services is not available on this device.")
            return
        }

        try {
            val client = ModuleInstall.getClient(context)
            val recognizer = ReceiptOcrEngine.recognizer
            currentStatus = PluginStatus.DOWNLOADING

            val listener = InstallStatusListener { update ->
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_DOWNLOADING -> {
                        val progress = update.progressInfo
                        if (progress != null && progress.totalBytesToDownload > 0) {
                            val pct = ((progress.bytesDownloaded * 100) / progress.totalBytesToDownload).toInt()
                            onProgress?.invoke(pct, progress.bytesDownloaded, progress.totalBytesToDownload)
                        }
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> {
                        currentStatus = PluginStatus.INSTALLED
                        onCompleted(true, "OCR Plug-in installed successfully!")
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> {
                        currentStatus = PluginStatus.NOT_INSTALLED
                        onCompleted(false, "Download failed (Code: ${update.errorCode})")
                    }
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED -> {
                        currentStatus = PluginStatus.NOT_INSTALLED
                        onCompleted(false, "Installation was canceled.")
                    }
                }
            }

            val request = ModuleInstallRequest.newBuilder()
                .addApi(recognizer)
                .setListener(listener)
                .build()

            client.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        currentStatus = PluginStatus.INSTALLED
                        onCompleted(true, "OCR Plug-in is already installed.")
                    }
                }
                .addOnFailureListener { e ->
                    currentStatus = PluginStatus.NOT_INSTALLED
                    onCompleted(false, e.message ?: "Failed to initiate plug-in installation.")
                }
        } catch (e: Exception) {
            currentStatus = PluginStatus.NOT_INSTALLED
            onCompleted(false, e.message ?: "Error connecting to ModuleInstall service.")
        }
    }

    /**
     * Helper to show a prompt and download progress dialog to the user.
     */
    fun showInstallDialog(
        activity: Activity,
        onInstalled: (() -> Unit)? = null,
    ) {
        if (!isGmsAvailable(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Google Play Services Required")
                .setMessage("On-demand OCR Plug-in delivery requires Google Play Services, which is not available or disabled on this device.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Install OCR Add-on / Plug-in")
            .setMessage("The Optical Character Recognition engine (~15 MB) is an optional plug-in and has not been installed yet to keep the app installation size minimal.\n\nWould you like to download and install the OCR Plug-in now?")
            .setPositiveButton("Install Plug-in") { _, _ ->
                val progressDialog = AlertDialog.Builder(activity)
                    .setTitle("Installing OCR Plug-in...")
                    .setMessage("Initiating download via Google Play Services...")
                    .setCancelable(false)
                    .create()
                progressDialog.show()

                installPlugin(
                    context = activity,
                    onProgress = { pct, downloaded, total ->
                        activity.runOnUiThread {
                            val mbDownloaded = downloaded / (1024f * 1024f)
                            val mbTotal = total / (1024f * 1024f)
                            progressDialog.setMessage("Downloading OCR Model: $pct%\n(%.1f MB / %.1f MB)".format(mbDownloaded, mbTotal))
                        }
                    },
                    onCompleted = { success, msg ->
                        activity.runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                onInstalled?.invoke()
                            }
                        }
                    },
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

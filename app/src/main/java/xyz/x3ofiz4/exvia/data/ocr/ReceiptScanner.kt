package xyz.x3ofiz4.exvia.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.x3ofiz4.exvia.app.ExviaApplication
import xyz.x3ofiz4.exvia.data.local.SettingsStore
import xyz.x3ofiz4.exvia.domain.model.custom.OcrTemplateDefinition
import xyz.x3ofiz4.exvia.domain.model.repository.RepoFile
import xyz.x3ofiz4.exvia.presentation.notification.NotificationDispatcher
import xyz.x3ofiz4.exvia.presentation.widget.ExpenseAppWidgetProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Automaton scanner that monitors folders, identifies newly added receipt images matching
 * a regex pattern, processes bounding boxes via Google ML Kit, and automatically appends
 * structured rows to the current Exvia expense file.
 */
object ReceiptScanner {

    data class ScanResult(
        val templateId: String,
        val templateName: String,
        val scannedCount: Int,
        val newFileNames: List<String>,
        val errors: List<String>,
    )

    /**
     * Executes scan across all enabled OCR templates.
     */
    suspend fun scanAllEnabled(context: Context): List<ScanResult> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? ExviaApplication ?: return@withContext emptyList()
        val settings = app.container.settingsStore.load()
        val enabledTemplates = settings.ocrTemplates.filter { it.enabled && it.folderUri.isNotBlank() }

        val results = mutableListOf<ScanResult>()
        for (template in enabledTemplates) {
            results.add(scanTemplate(context, template))
        }
        results
    }

    /**
     * Scans a single specific OCR template.
     */
    suspend fun scanTemplate(context: Context, template: OcrTemplateDefinition): ScanResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext as? ExviaApplication
            ?: return@withContext ScanResult(template.id, template.name, 0, emptyList(), listOf("Application container unavailable"))

        val treeUri = try {
            Uri.parse(template.folderUri)
        } catch (_: Exception) {
            return@withContext ScanResult(template.id, template.name, 0, emptyList(), listOf("Invalid folder URI"))
        }

        val dir = try {
            DocumentFile.fromTreeUri(context, treeUri)
        } catch (e: Exception) {
            return@withContext ScanResult(template.id, template.name, 0, emptyList(), listOf("Cannot open folder: ${e.message}"))
        }

        if (dir == null || !dir.exists() || !dir.isDirectory) {
            return@withContext ScanResult(template.id, template.name, 0, emptyList(), listOf("Folder does not exist or access expired"))
        }

        val regex = try {
            Regex(template.fileNamePattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            Regex(".*")
        }

        val allFiles = dir.listFiles()
        val matchingImages = allFiles.filter { file ->
            file.isFile &&
                regex.containsMatchIn(file.name.orEmpty()) &&
                isImageFile(file)
        }

        val processedSet = template.processedFiles.toSet()
        val newFiles = matchingImages.filterNot { processedSet.contains(it.name.orEmpty()) }
            .sortedBy { it.lastModified() }

        if (newFiles.isEmpty()) {
            return@withContext ScanResult(template.id, template.name, 0, emptyList(), emptyList())
        }

        if (OcrPluginManager.isGmsAvailable(context)) {
            val client = com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(context)
            val isAvailable = try {
                val checkTask = client.areModulesAvailable(ReceiptOcrEngine.recognizer)
                com.google.android.gms.tasks.Tasks.await(checkTask).areModulesAvailable()
            } catch (_: Exception) {
                true
            }
            if (!isAvailable) {
                return@withContext ScanResult(
                    templateId = template.id,
                    templateName = template.name,
                    scannedCount = 0,
                    newFileNames = emptyList(),
                    errors = listOf("OCR Plug-in is not installed yet (~15 MB add-on). Please install it via Settings."),
                )
            }
        }

        val settings = app.container.settingsStore.load()
        val targetPath = settings.pathFor(settings.defaultJson)
        val files: List<RepoFile> = app.container.fileCache.loadFiles(settings)?.ifEmpty { null } ?: listOf(
            RepoFile(settings.defaultJson, targetPath, "")
        )

        val newlyProcessed = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (file in newFiles) {
            val fileName = file.name.orEmpty()
            val bitmap = loadOrientedBitmap(context, file.uri)
            if (bitmap == null) {
                errors.add("Failed to decode image: $fileName")
                continue
            }

            try {
                val extracted = ReceiptOcrEngine.extractExpenseFields(bitmap, template.boundingBoxes).toMutableMap()
                bitmap.recycle()

                if (extracted.isEmpty()) {
                    errors.add("No text matched bounding boxes in $fileName")
                    // Still mark as processed so we don't scan failing image repeatedly
                    newlyProcessed.add(fileName)
                    continue
                }

                // Fill date if key exists and was not extracted
                val dateKey = settings.detectDateKey(extracted.keys.toList()) ?: "date"
                if (extracted[dateKey].isNullOrBlank()) {
                    extracted[dateKey] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))
                }

                // Append row into Exvia repository
                app.container.expenseRepository.appendRow(
                    settings = settings,
                    files = files,
                    path = targetPath,
                    values = extracted,
                )

                newlyProcessed.add(fileName)
            } catch (e: Exception) {
                errors.add("Error appending row from $fileName: ${e.message}")
            }
        }

        if (newlyProcessed.isNotEmpty()) {
            val updatedTemplate = template.copy(
                processedFiles = (template.processedFiles + newlyProcessed).distinct(),
                lastScannedTimestamp = System.currentTimeMillis(),
            )
            val updatedSettings = settings.copy(
                ocrTemplates = settings.ocrTemplates.map {
                    if (it.id == template.id) updatedTemplate else it
                }
            )
            app.container.settingsStore.save(updatedSettings)

            // Notify UI & widgets
            ExpenseAppWidgetProvider.updateAllWidgets(context, "OCR: ${newlyProcessed.size} receipts scanned")
            postScanNotification(context, template.name, newlyProcessed.size)
        }

        ScanResult(
            templateId = template.id,
            templateName = template.name,
            scannedCount = newlyProcessed.size,
            newFileNames = newlyProcessed,
            errors = errors,
        )
    }

    fun isImageFile(file: DocumentFile): Boolean {
        val name = file.name.orEmpty().lowercase()
        val type = file.type.orEmpty().lowercase()
        return type.startsWith("image/") ||
            name.endsWith(".jpg") ||
            name.endsWith(".jpeg") ||
            name.endsWith(".png") ||
            name.endsWith(".webp") ||
            name.endsWith(".bmp")
    }

    /**
     * Safely reads an image preserving EXIF rotation and downsampling if exceeding maxDimension.
     */
    fun loadOrientedBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (origW / sampleSize > maxDimension || origH / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use {
                    val exif = ExifInterface(it)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return rawBitmap
            }

            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun postScanNotification(context: Context, templateName: String, count: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    NotificationDispatcher.CHANNEL_ID,
                    "Exvia notifications",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Assistant responses and notifications generated by Exvia event scripts"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, NotificationDispatcher.CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
            }

            builder.setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("Receipt OCR Automaton")
                .setContentText("Added $count new expense row(s) from template '$templateName'")
                .setAutoCancel(true)

            notificationManager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), builder.build())
        } catch (_: Exception) {
            // Notifications may be blocked by system or permission
        }
    }
}

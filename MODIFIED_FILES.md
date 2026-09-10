# Exvia v1.13.8 modified and created files

## Modified

### `app/build.gradle.kts`
- Bumped `versionCode` to `25`.
- Bumped `versionName` to `"1.13.8"`.
- Replaced bundled ML Kit with unbundled on-demand Google Play Services ML Kit (`com.google.android.gms:play-services-mlkit-text-recognition:19.0.1`) and `com.google.android.gms:play-services-base:18.5.0` to keep the APK download size small.
- Added AndroidX DocumentFile (`androidx.documentfile:documentfile:1.0.1`).
- Added Mozilla Rhino JavaScript engine (`org.mozilla:rhino:1.7.15`).
- Added JVM test JSON dependency (`org.json:json:20240303`).

### `gradle/libs.versions.toml`
- Added library aliases and version definitions for `play-services-mlkit-text-recognition`, `play-services-base`, `androidx-documentfile`, and `rhino`.

### `app/src/main/AndroidManifest.xml`
- Added permissions: `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` (maxSdk 32), `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`.
- Registered `ReceiptOcrEditorActivity` with `@style/Theme.Exvia.NoActionBar` and orientation configuration changes handling.
- Registered `ReceiptWatcherService` foreground service with `dataSync` foreground service type.

### `app/src/main/java/xyz/x3ofiz4/exvia/core/config/RepoConfig.kt`
- Added `OCR_TEMPLATES_PATH = ".exvia/receipt-ocr-templates.json"`.

### `app/src/main/java/xyz/x3ofiz4/exvia/domain/model/custom/CustomizationModels.kt`
- Added `OcrBoundingBox` data class (`name`, `posX`, `posY`, `width`, `height`, `mapTo`, `script`, `regex`).
- Added `OcrTemplateDefinition` data class (`id`, `name`, `folderUri`, `fileNamePattern`, `boundingBoxes`, `enabled`, `targetFilePath`, `processedFiles`).

### `app/src/main/java/xyz/x3ofiz4/exvia/domain/model/settings/RepoSettings.kt`
- Added `ocrTemplates: List<OcrTemplateDefinition>` property to `RepoSettings` model.

### `app/src/main/java/xyz/x3ofiz4/exvia/data/local/SettingsStore.kt`
- Added `parseOcrTemplates(json)` and `ocrTemplatesToJson(templates)` with `script` and `regex` serialization.
- Added `ocrTemplatesFileJson(templates)` for saving to `.exvia/receipt-ocr-templates.json`.
- Integrated `ocrTemplates` into local SharedPreferences caching and remote settings sync.

### `app/src/main/java/xyz/x3ofiz4/exvia/data/remote/GitHubApi.kt`
- Added `getOcrTemplatesFile` and `updateOcrTemplatesFile` API endpoints.

### `app/src/main/java/xyz/x3ofiz4/exvia/data/ocr/ReceiptOcrEngine.kt`
- Converted to unbundled Google Play Services ML Kit text recognizer (`play-services-mlkit-text-recognition`).
- Sub-bitmap cropping and normalization.
- Integrated `ReceiptScriptEngine` execution pipeline on bounding box extraction before mapping.
- Exposed public `recognizer` property for optional module installation checks.

### `app/src/main/java/xyz/x3ofiz4/exvia/data/ocr/ReceiptScanner.kt`
- Added OCR plug-in installation verification before scanning.
- Directory scanner using SAF `DocumentFile`, regex file matching, EXIF orientation correction, bitmap downsampling, OCR processing, duplicate file tracking, and expense row appending via `ExpenseRepository.appendRow()`.

### `app/src/main/java/xyz/x3ofiz4/exvia/presentation/ocr/ReceiptBoundingBoxOverlayView.kt`
- Updated visual styling to bind to active `ThemePalette` (`primaryColor()`, `senaryColor()`, etc.).
- Ultra-thin border strokes (`dp(0.85f)` when selected).
- Zero border stroke on unselected bounding boxes, displaying only a subtle transparent background tint to leave receipt text fully unobstructed.
- Tiny unobtrusive label badges (`dp(8.5f)` font size).
- Tap-outside or explicit `clearSelection()` deselects all bounding boxes to view the receipt cleanly.

### `app/src/main/java/xyz/x3ofiz4/exvia/presentation/ocr/ReceiptOcrEditorActivity.kt`
- Full visual template editor matching active theme palette (`primaryColor()`, `senaryColor()`, `tertiaryColor()`, `quaternaryColor()`).
- Added "Deselect" control.
- Added interactive "⚙ Script & RegExp Pipeline" editor modal:
  - Default 4-stage script (`FILTERING -> PASS/NOT PASS -> MODIFY/SUBSTITUTE -> FINISH`).
  - "Insert Default" script button.
  - Syntax-highlighted `JavaScriptCodeEditor`.
  - Post-script Regular Expression pattern input.
  - Live interactive Test Sandbox with step-by-step pipeline results.
- Added plug-in availability check before live OCR testing, prompting user to install the add-on if uninstalled.

### `app/src/main/java/xyz/x3ofiz4/exvia/presentation/main/MainActivity.kt`
- Added "Receipt OCR Automaton" collapsible settings accordion section.
- Added **OCR Add-on / Plug-in status banner** (`🧩 OCR Add-on / Plug-in`):
  - Displays installation state (`● Installed (Ready)` or `○ Not Installed (~15 MB)`).
  - Includes `"📥 Install OCR Plug-in"` action with download progress dialog.
- Checks OCR plug-in installation before manual "Scan Now" action and prompts user if uninstalled.
- Template card list rendering showing template name, target folder URI, regex pattern, mapped target expense file, and box count.
- Enable/Disable switch, "Scan Now", "Edit", and "Delete" actions.

## Created

### Domain & Data / OCR
- `app/src/main/java/xyz/x3ofiz4/exvia/data/ocr/OcrPluginManager.kt`: Google Play Services `ModuleInstallClient` manager checking optional module availability, triggering on-demand background model downloads (~15 MB), tracking download progress, and providing user install dialogs.
- `app/src/main/java/xyz/x3ofiz4/exvia/data/ocr/ReceiptScriptEngine.kt`: Mozilla Rhino JavaScript engine integration and post-RegExp processor running the 4-stage pipeline (`FILTERING -> PASS/NOT PASS -> MODIFY/SUBSTITUTE -> FINISH`).
- `app/src/main/java/xyz/x3ofiz4/exvia/data/ocr/ReceiptScanner.kt`: Directory scanner using SAF `DocumentFile`, regex file matching, EXIF orientation correction, bitmap downsampling, OCR processing, duplicate file tracking, and expense row appending via `ExpenseRepository.appendRow()`.

### Presentation / OCR
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/ocr/ReceiptBoundingBoxOverlayView.kt`: Custom interactive overlay view with normalized coordinates, touch drag-and-resize, zero unselected borders, and theme palette colors.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/ocr/ReceiptOcrEditorActivity.kt`: Full visual template configuration editor with script/regex modal and live sandbox.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/ocr/ReceiptWatcherService.kt`: Foreground service monitoring designated receipt folders via `ContentObserver` with fallback periodic scanning.

### Testing
- `app/src/test/java/xyz/x3ofiz4/exvia/data/ocr/ReceiptOcrTest.kt`: JVM unit tests covering OCR template JSON serialization with scripts and regex, JavaScript execution, RegExp extraction and substitution, 4-stage pipeline evaluation, text sanitization, and plugin manager status enums.

### Specifications
- `V1.13.8_CHANGE_SPEC.md`: Full change specification and architectural breakdown for Exvia v1.13.8.

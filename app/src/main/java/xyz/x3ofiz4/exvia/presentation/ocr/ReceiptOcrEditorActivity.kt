package xyz.x3ofiz4.exvia.presentation.ocr

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.x3ofiz4.exvia.app.ExviaApplication
import xyz.x3ofiz4.exvia.data.local.SettingsStore
import xyz.x3ofiz4.exvia.data.ocr.OcrPluginManager
import xyz.x3ofiz4.exvia.data.ocr.ReceiptOcrEngine
import xyz.x3ofiz4.exvia.data.ocr.ReceiptScanner
import xyz.x3ofiz4.exvia.data.ocr.ReceiptScriptEngine
import xyz.x3ofiz4.exvia.data.remote.GitHubApi
import xyz.x3ofiz4.exvia.domain.model.custom.OcrBoundingBox
import xyz.x3ofiz4.exvia.domain.model.custom.OcrTemplateDefinition
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePalette
import xyz.x3ofiz4.exvia.domain.model.theme.ThemePreset
import xyz.x3ofiz4.exvia.presentation.common.*
import java.util.UUID

/**
 * Interactive OCR Template Editor Activity.
 * Allows selecting a receipt folder, configuring regex matching, navigating through
 * matching receipt images, drawing/resizing bounding boxes with ultra-thin borders and
 * non-intrusive transparent unselected fills, and assigning customizable JavaScript and
 * RegExp transformation pipelines for each bounding box.
 */
class ReceiptOcrEditorActivity : ComponentActivity() {

    private lateinit var palette: ThemePalette
    private lateinit var overlayView: ReceiptBoundingBoxOverlayView
    private lateinit var templateNameInput: EditText
    private lateinit var folderDisplayTextView: TextView
    private lateinit var regexPatternInput: EditText
    private lateinit var matchingImagesStatusTextView: TextView
    private lateinit var prevImageButton: Button
    private lateinit var nextImageButton: Button

    // Box editing controls
    private lateinit var boxControlsLayout: LinearLayout
    private lateinit var boxNameInput: EditText
    private lateinit var mapToSpinner: Spinner
    private lateinit var btnEditScriptAndRegex: Button
    private lateinit var deleteBoxButton: Button
    private lateinit var btnDeselect: Button

    private var selectedFolderUri: Uri? = null
    private var selectedFolderDisplayName: String = ""
    private val matchingFiles = mutableListOf<DocumentFile>()
    private var currentImageIndex = -1
    private var currentBitmap: Bitmap? = null

    private var availableFields = listOf("price", "date", "description", "category", "tags")
    private var existingTemplateId: String? = null
    private var isTemplateEnabled: Boolean = true

    // Activity Result Launchers
    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            selectedFolderUri = uri
            val dir = DocumentFile.fromTreeUri(this, uri)
            selectedFolderDisplayName = dir?.name ?: uri.lastPathSegment ?: uri.toString()
            folderDisplayTextView.text = selectedFolderDisplayName
            refreshMatchingFiles()
        }
    }

    private val pickSampleImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loadDirectImageUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemeAndFields()

        existingTemplateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        val root = buildUi()
        setContentView(root)

        if (existingTemplateId != null) {
            loadExistingTemplate(existingTemplateId!!)
        }
    }

    private fun loadThemeAndFields() {
        try {
            val app = application as? ExviaApplication
            val settings = app?.container?.settingsStore?.load() ?: SettingsStore(this).load()
            palette = settings.palette

            val cached = app?.container?.fileCache?.loadFile(settings, settings.pathFor(settings.defaultJson))
            if (cached != null) {
                val api = GitHubApi("", settings)
                val table = api.parseCachedTable(cached.text)
                if (table.keys.isNotEmpty()) {
                    availableFields = table.keys
                }
            }
        } catch (_: Exception) {
            palette = ThemePalette.preset(ThemePreset.DEFAULT)
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.tertiaryColor())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        // Top Navigation Bar
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(palette.quaternaryColor())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnBack = Button(this).apply {
            text = "← Back"
            setTextColor(palette.senaryColor())
            setBackgroundColor(Color.TRANSPARENT)
            AppFonts.apply(this, bold = true)
            setOnClickListener { finish() }
        }
        navBar.addView(btnBack, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))

        val titleView = TextView(this).apply {
            text = if (existingTemplateId != null) "Edit OCR Template" else "New OCR Template"
            setTextColor(palette.senaryColor())
            textSize = 15f
            gravity = Gravity.CENTER
            AppFonts.apply(this, bold = true)
        }
        navBar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btnSave = createButton("Save", palette.primaryColor()).apply {
            setOnClickListener { saveTemplate() }
        }
        navBar.addView(btnSave, LinearLayout.LayoutParams(dp(75), dp(36)))

        root.addView(navBar)

        // Scrollable Content
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        // 1. Template Name Input
        scrollContent.addView(createLabel("Template Name:"))
        templateNameInput = createEditText("e.g. Supermarket Receipts").apply {
            setText(if (existingTemplateId != null) "" else "Receipt Automaton")
        }
        scrollContent.addView(templateNameInput)

        // 2. Folder Location Picker
        scrollContent.addView(
            createLabel("Folder Location:"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        )
        val folderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnSelectFolder = createButton("📁 Select Folder", palette.primaryColor()).apply {
            setOnClickListener { openDocumentTreeLauncher.launch(null) }
        }
        folderRow.addView(btnSelectFolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))

        val btnPickImage = createButton("🖼️ Sample", palette.quaternaryColor(), strokeColor = palette.quinaryColor()).apply {
            setOnClickListener { pickSampleImageLauncher.launch("image/*") }
        }
        folderRow.addView(btnPickImage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
            marginStart = dp(6)
        })

        folderDisplayTextView = TextView(this).apply {
            text = "No folder selected"
            setTextColor(palette.senaryColor())
            alpha = 0.7f
            textSize = 12f
            setSingleLine(true)
            AppFonts.apply(this)
            setPadding(dp(8), 0, 0, 0)
        }
        folderRow.addView(folderDisplayTextView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        scrollContent.addView(folderRow)

        // 3. File Name Pattern (Regex)
        scrollContent.addView(
            createLabel("File Name Pattern (Regex):"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        )
        regexPatternInput = createEditText(".*\\.(jpg|jpeg|png)").apply {
            setText(".*\\.(jpg|jpeg|png)")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    refreshMatchingFiles()
                }
            })
        }
        scrollContent.addView(regexPatternInput)

        // Image Pager & Status Header
        val pagerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(4))
        }

        prevImageButton = createButton("< Prev", palette.quaternaryColor(), strokeColor = palette.quinaryColor()).apply {
            isEnabled = false
            setOnClickListener { showPreviousImage() }
        }
        pagerRow.addView(prevImageButton, LinearLayout.LayoutParams(dp(70), dp(32)))

        matchingImagesStatusTextView = TextView(this).apply {
            text = "0 images matching pattern"
            setTextColor(palette.senaryColor())
            alpha = 0.7f
            textSize = 11f
            gravity = Gravity.CENTER
            AppFonts.apply(this)
        }
        pagerRow.addView(matchingImagesStatusTextView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        nextImageButton = createButton("Next >", palette.quaternaryColor(), strokeColor = palette.quinaryColor()).apply {
            isEnabled = false
            setOnClickListener { showNextImage() }
        }
        pagerRow.addView(nextImageButton, LinearLayout.LayoutParams(dp(70), dp(32)))

        scrollContent.addView(pagerRow)

        // Instructions Hint
        val hintText = TextView(this).apply {
            text = "💡 Tap & drag to create a box. Unselected boxes show zero borders to keep text visible."
            setTextColor(palette.primaryColor())
            textSize = 11f
            AppFonts.apply(this)
            setPadding(0, 0, 0, dp(6))
        }
        scrollContent.addView(hintText)

        // Interactive Bounding Box Overlay Canvas
        overlayView = ReceiptBoundingBoxOverlayView(this).apply {
            setThemePalette(palette)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(340),
            )
            onSelectionChangedListener = { box, _ ->
                updateBoxControlsUi(box)
            }
            onBoxCreatedListener = { box ->
                updateBoxControlsUi(box)
            }
        }
        scrollContent.addView(overlayView)

        // Bounding Box Controls Section
        boxControlsLayout = buildBoxControlsUi()
        scrollContent.addView(boxControlsLayout)

        // Action Row
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(16))
        }

        val btnAddDefaultBox = createButton("+ Add Box", palette.quaternaryColor(), strokeColor = palette.primaryColor()).apply {
            setOnClickListener {
                val boxNum = overlayView.boxes.size + 1
                val targetKey = availableFields.getOrElse(overlayView.boxes.size % availableFields.size) { "price" }
                val newBox = OcrBoundingBox(
                    name = "Field $boxNum",
                    posX = 0.2f,
                    posY = (0.15f + (overlayView.boxes.size * 0.08f)).coerceAtMost(0.75f),
                    width = 0.6f,
                    height = 0.06f,
                    mapTo = targetKey,
                    script = ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT,
                    regex = "",
                )
                overlayView.addBox(newBox)
            }
        }
        actionRow.addView(btnAddDefaultBox, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })

        val btnTestOcr = createButton("🔍 Test OCR", palette.primaryColor()).apply {
            setOnClickListener { testOcrOnCurrentImage() }
        }
        actionRow.addView(btnTestOcr, LinearLayout.LayoutParams(0, dp(38), 1f))

        scrollContent.addView(actionRow)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(palette.tertiaryColor())
            addView(scrollContent)
        }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        return root
    }

    private fun buildBoxControlsUi(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.quaternaryColor())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            val border = GradientDrawable().apply {
                setColor(palette.quaternaryColor())
                setStroke(dp(1), palette.quinaryColor())
                cornerRadius = dp(4).toFloat()
            }
            background = border
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sectionTitle = TextView(this).apply {
            text = "Bounding Box Controls:"
            setTextColor(palette.senaryColor())
            textSize = 12f
            AppFonts.apply(this, bold = true)
        }
        headerRow.addView(sectionTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        btnDeselect = createButton("Deselect", palette.tertiaryColor(), strokeColor = palette.quinaryColor()).apply {
            setOnClickListener {
                overlayView.clearSelection()
            }
        }
        headerRow.addView(btnDeselect, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)).apply {
            marginEnd = dp(6)
        })

        deleteBoxButton = createButton("🗑️ Delete", Color.parseColor("#7F1D1D")).apply {
            setOnClickListener {
                overlayView.deleteSelectedBox()
            }
        }
        headerRow.addView(deleteBoxButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)))
        layout.addView(headerRow)

        // Inputs: Name and MapTo
        val inputsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            gravity = Gravity.CENTER_VERTICAL
        }

        boxNameInput = createEditText("Box Name").apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val mapTo = availableFields.getOrNull(mapToSpinner.selectedItemPosition) ?: "price"
                    overlayView.updateSelectedBox(s?.toString().orEmpty(), mapTo)
                }
            })
        }
        inputsRow.addView(boxNameInput, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(8) })

        mapToSpinner = Spinner(this).apply {
            val adapter = ArrayAdapter(
                this@ReceiptOcrEditorActivity,
                android.R.layout.simple_spinner_dropdown_item,
                availableFields,
            )
            this.adapter = adapter
            setBackgroundColor(palette.tertiaryColor())
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    (view as? TextView)?.setTextColor(palette.senaryColor())
                    val mapTo = availableFields.getOrElse(position) { "price" }
                    overlayView.updateSelectedBox(boxNameInput.text.toString(), mapTo)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        inputsRow.addView(mapToSpinner, LinearLayout.LayoutParams(0, dp(38), 1f))
        layout.addView(inputsRow)

        // Script & RegExp Action Button
        btnEditScriptAndRegex = createButton("⚙ Script & RegExp Pipeline", palette.quaternaryColor(), strokeColor = palette.primaryColor()).apply {
            setOnClickListener {
                val box = overlayView.selectedBox
                if (box != null) {
                    openScriptAndRegexDialog(box)
                }
            }
        }
        layout.addView(btnEditScriptAndRegex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)))

        return layout
    }

    private fun updateBoxControlsUi(box: OcrBoundingBox?) {
        if (box == null) {
            boxNameInput.setText("")
            boxNameInput.isEnabled = false
            mapToSpinner.isEnabled = false
            deleteBoxButton.isEnabled = false
            btnDeselect.isEnabled = false
            btnEditScriptAndRegex.isEnabled = false
            btnEditScriptAndRegex.text = "⚙ Script & RegExp (No box selected)"
        } else {
            boxNameInput.isEnabled = true
            mapToSpinner.isEnabled = true
            deleteBoxButton.isEnabled = true
            btnDeselect.isEnabled = true
            btnEditScriptAndRegex.isEnabled = true

            if (boxNameInput.text.toString() != box.name) {
                boxNameInput.setText(box.name)
            }
            val pos = availableFields.indexOfFirst { it.equals(box.mapTo, ignoreCase = true) }
            if (pos >= 0) {
                mapToSpinner.setSelection(pos)
            }

            val hasCustomScript = box.script.isNotBlank() && box.script != ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT
            val hasRegex = box.regex.isNotBlank()
            btnEditScriptAndRegex.text = when {
                hasCustomScript && hasRegex -> "⚙ Script ✓ | RegExp ✓ (${box.mapTo})"
                hasCustomScript -> "⚙ Script ✓ (${box.mapTo})"
                hasRegex -> "⚙ RegExp ✓ (${box.mapTo})"
                else -> "⚙ Script & RegExp (${box.mapTo})"
            }
        }
    }

    /**
     * Opens the interactive JavaScript and RegExp configuration dialog for [box].
     * Includes syntax-highlighted editor, pipeline explanation, regex input, and a live testing sandbox.
     */
    private fun openScriptAndRegexDialog(box: OcrBoundingBox) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.tertiaryColor())
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        val titleView = TextView(this).apply {
            text = "${box.name} [→ ${box.mapTo}]"
            setTextColor(palette.senaryColor())
            textSize = 15f
            AppFonts.apply(this, bold = true)
        }
        dialogLayout.addView(titleView)

        val pipelineNote = TextView(this).apply {
            text = "Pipeline: FILTERING -> PASS/NOT PASS -> MODIFY/SUBSTITUTE -> FINISH"
            setTextColor(palette.primaryColor())
            textSize = 11f
            AppFonts.apply(this, bold = true)
            setPadding(0, dp(2), 0, dp(8))
        }
        dialogLayout.addView(pipelineNote)

        // Script Header & "Insert Default Script"
        val scriptHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val lblJs = TextView(this).apply {
            text = "JavaScript Processor (ES5):"
            setTextColor(palette.senaryColor())
            textSize = 12f
            AppFonts.apply(this)
        }
        scriptHeader.addView(lblJs, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val btnResetScript = createButton("Insert Default", palette.quaternaryColor(), strokeColor = palette.quinaryColor()).apply {
            textSize = 10f
        }
        scriptHeader.addView(btnResetScript, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
        dialogLayout.addView(scriptHeader)

        // JavaScript Code Editor
        val scriptEditor = JavaScriptCodeEditor(this).apply {
            val initialScript = if (box.script.isNotBlank()) box.script else ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT
            setText(initialScript)
            minLines = 8
            maxLines = 14
        }
        dialogLayout.addView(scriptEditor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)))

        btnResetScript.setOnClickListener {
            scriptEditor.setText(ReceiptScriptEngine.DEFAULT_RECEIPT_JS_SCRIPT)
        }

        // Post-script Regex Input
        val lblRegex = TextView(this).apply {
            text = "Post-Script Regular Expression (Optional):"
            setTextColor(palette.senaryColor())
            textSize = 12f
            AppFonts.apply(this)
            setPadding(0, dp(10), 0, dp(2))
        }
        dialogLayout.addView(lblRegex)

        val regexHint = TextView(this).apply {
            text = "e.g. \\d+(\\.\\d{2})? to extract or s/,/./g for substitution"
            setTextColor(palette.senaryColor())
            alpha = 0.6f
            textSize = 10f
            AppFonts.apply(this)
            setPadding(0, 0, 0, dp(4))
        }
        dialogLayout.addView(regexHint)

        val regexInput = createEditText("e.g. \\d+(\\.\\d{2})? or s/[^0-9.]//g").apply {
            setText(box.regex)
        }
        dialogLayout.addView(regexInput)

        // Live Test Sandbox
        val sandboxHeader = TextView(this).apply {
            text = "🧪 Test Sandbox:"
            setTextColor(palette.senaryColor())
            textSize = 12f
            AppFonts.apply(this, bold = true)
            setPadding(0, dp(12), 0, dp(4))
        }
        dialogLayout.addView(sandboxHeader)

        val testRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val testValueInput = createEditText("Test Input Value").apply {
            setText("$ 49.99")
        }
        testRow.addView(testValueInput, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) })

        val btnRunTest = createButton("Run Test", palette.primaryColor()).apply {
            textSize = 11f
        }
        testRow.addView(btnRunTest, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)))
        dialogLayout.addView(testRow)

        val testResultView = TextView(this).apply {
            text = "Click 'Run Test' to evaluate value pipeline."
            setTextColor(palette.senaryColor())
            alpha = 0.8f
            textSize = 11f
            typeface = AppFonts.jetBrains(this@ReceiptOcrEditorActivity)
            setBackgroundColor(palette.quaternaryColor())
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        dialogLayout.addView(testResultView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        btnRunTest.setOnClickListener {
            val inputVal = testValueInput.text.toString()
            val scr = scriptEditor.text.toString()
            val reg = regexInput.text.toString()
            val proc = ReceiptScriptEngine.processValue(inputVal, scr, reg)

            val sb = StringBuilder()
            sb.append("1. Raw Input:   \"${proc.rawValue}\"\n")
            if (proc.scriptError != null) {
                sb.append("2. JS Error:    ${proc.scriptError}\n")
            } else {
                sb.append("2. After JS:    \"${proc.afterScript}\"\n")
            }
            if (proc.regexError != null) {
                sb.append("3. Regex Error: ${proc.regexError}\n")
            } else {
                sb.append("3. Final Value: \"${proc.finalValue}\" [→ ${box.mapTo}]")
            }
            testResultView.text = sb.toString()
        }

        // If current image has OCR for this box, prefill testValueInput
        val bmp = currentBitmap
        if (bmp != null) {
            lifecycleScope.launch {
                val singleResult = ReceiptOcrEngine.processBoundingBoxes(bmp, listOf(box))
                val rawText = singleResult.firstOrNull()?.rawText
                if (!rawText.isNullOrBlank()) {
                    testValueInput.setText(rawText)
                }
            }
        }

        val scrollContainer = ScrollView(this).apply {
            addView(dialogLayout)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(scrollContainer)
            .setPositiveButton("Apply") { _, _ ->
                val newScript = scriptEditor.text.toString()
                val newRegex = regexInput.text.toString().trim()
                overlayView.updateSelectedBox(box.name, box.mapTo, script = newScript, regex = newRegex)
                updateBoxControlsUi(overlayView.selectedBox)
                Toast.makeText(this, "Script & RegExp updated for ${box.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun refreshMatchingFiles() {
        val uri = selectedFolderUri ?: return
        val dir = DocumentFile.fromTreeUri(this, uri) ?: return

        val pattern = regexPatternInput.text.toString()
        val regex = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            Regex(".*")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val files = dir.listFiles().filter { file ->
                file.isFile &&
                    regex.containsMatchIn(file.name.orEmpty()) &&
                    ReceiptScanner.isImageFile(file)
            }

            withContext(Dispatchers.Main) {
                matchingFiles.clear()
                matchingFiles.addAll(files)

                matchingImagesStatusTextView.text = "${files.size} image(s) match regex"
                if (files.isNotEmpty()) {
                    currentImageIndex = 0
                    loadImageAtIndex(0)
                } else {
                    currentImageIndex = -1
                    prevImageButton.isEnabled = false
                    nextImageButton.isEnabled = false
                    matchingImagesStatusTextView.text = "0 images match regex in folder"
                }
            }
        }
    }

    private fun showPreviousImage() {
        if (matchingFiles.isEmpty()) return
        currentImageIndex = (currentImageIndex - 1).coerceAtLeast(0)
        loadImageAtIndex(currentImageIndex)
    }

    private fun showNextImage() {
        if (matchingFiles.isEmpty()) return
        currentImageIndex = (currentImageIndex + 1).coerceAtMost(matchingFiles.lastIndex)
        loadImageAtIndex(currentImageIndex)
    }

    private fun loadImageAtIndex(index: Int) {
        if (index !in matchingFiles.indices) return
        val file = matchingFiles[index]
        prevImageButton.isEnabled = index > 0
        nextImageButton.isEnabled = index < matchingFiles.lastIndex
        matchingImagesStatusTextView.text = "Image ${index + 1}/${matchingFiles.size}: ${file.name}"

        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = ReceiptScanner.loadOrientedBitmap(this@ReceiptOcrEditorActivity, file.uri)
            withContext(Dispatchers.Main) {
                currentBitmap?.recycle()
                currentBitmap = bmp
                overlayView.bitmap = bmp
            }
        }
    }

    private fun loadDirectImageUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = ReceiptScanner.loadOrientedBitmap(this@ReceiptOcrEditorActivity, uri)
            withContext(Dispatchers.Main) {
                currentBitmap?.recycle()
                currentBitmap = bmp
                overlayView.bitmap = bmp
                matchingImagesStatusTextView.text = "Loaded sample image"
            }
        }
    }

    private fun testOcrOnCurrentImage() {
        val bmp = currentBitmap
        if (bmp == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }
        val boxes = overlayView.boxes
        if (boxes.isEmpty()) {
            Toast.makeText(this, "Draw at least one bounding box to test", Toast.LENGTH_SHORT).show()
            return
        }

        OcrPluginManager.checkPluginInstalled(this) { isInstalled, _ ->
            if (!isInstalled) {
                runOnUiThread {
                    OcrPluginManager.showInstallDialog(this) {
                        testOcrOnCurrentImage()
                    }
                }
                return@checkPluginInstalled
            }

            runOnUiThread {
                runOcrTest(bmp, boxes)
            }
        }
    }

    private fun runOcrTest(bmp: Bitmap, boxes: List<OcrBoundingBox>) {
        lifecycleScope.launch {
            val progress = AlertDialog.Builder(this@ReceiptOcrEditorActivity)
                .setTitle("Running ML Kit OCR...")
                .setMessage("Processing text in ${boxes.size} bounding boxes...")
                .setCancelable(false)
                .create()
            progress.show()

            val results = ReceiptOcrEngine.processBoundingBoxes(bmp, boxes)
            progress.dismiss()

            val sb = StringBuilder()
            results.forEachIndexed { i, res ->
                sb.append("${i + 1}. [${res.name}] → ${res.mapTo}\n")
                sb.append("   • Raw OCR:      \"${res.rawText}\"\n")
                if (res.rawText != res.afterScriptText || res.scriptError != null) {
                    sb.append("   • After JS:     \"${res.afterScriptText}\"${if (res.scriptError != null) " (Error: ${res.scriptError})" else ""}\n")
                }
                sb.append("   • Final Mapped: \"${res.cleanedText}\"${if (res.regexError != null) " (Error: ${res.regexError})" else ""}\n\n")
            }

            AlertDialog.Builder(this@ReceiptOcrEditorActivity)
                .setTitle("OCR Pipeline Live Test")
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun saveTemplate() {
        val name = templateNameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Please provide a Template Name", Toast.LENGTH_SHORT).show()
            return
        }

        val folderUri = selectedFolderUri?.toString().orEmpty()
        if (folderUri.isBlank()) {
            Toast.makeText(this, "Please select a Folder Location", Toast.LENGTH_SHORT).show()
            return
        }

        val pattern = regexPatternInput.text.toString().trim().ifBlank { ".*\\.(jpg|jpeg|png)" }
        val boxes = overlayView.boxes
        if (boxes.isEmpty()) {
            Toast.makeText(this, "Please define at least one Bounding Box", Toast.LENGTH_SHORT).show()
            return
        }

        val app = application as? ExviaApplication
        val settingsStore = app?.container?.settingsStore ?: SettingsStore(this)
        val currentSettings = settingsStore.load()

        val templateId = existingTemplateId ?: UUID.randomUUID().toString()
        val template = OcrTemplateDefinition(
            id = templateId,
            name = name,
            folderUri = folderUri,
            folderDisplayName = selectedFolderDisplayName,
            fileNamePattern = pattern,
            boundingBoxes = boxes,
            enabled = isTemplateEnabled,
        )

        val updatedList = if (existingTemplateId != null) {
            currentSettings.ocrTemplates.map { if (it.id == existingTemplateId) template else it }
        } else {
            currentSettings.ocrTemplates + template
        }

        val nextSettings = currentSettings.copy(ocrTemplates = updatedList)
        settingsStore.save(nextSettings)

        // Update watcher service
        ReceiptWatcherService.updateServiceState(this)

        Toast.makeText(this, "Template '$name' saved successfully", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun loadExistingTemplate(id: String) {
        val app = application as? ExviaApplication
        val settings = app?.container?.settingsStore?.load() ?: SettingsStore(this).load()
        val template = settings.ocrTemplates.firstOrNull { it.id == id } ?: return

        templateNameInput.setText(template.name)
        regexPatternInput.setText(template.fileNamePattern)
        selectedFolderDisplayName = template.folderDisplayName
        folderDisplayTextView.text = template.folderDisplayName
        isTemplateEnabled = template.enabled

        if (template.folderUri.isNotBlank()) {
            selectedFolderUri = Uri.parse(template.folderUri)
            refreshMatchingFiles()
        }

        overlayView.setBoxes(template.boundingBoxes)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentBitmap?.recycle()
    }

    private fun createLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(palette.senaryColor())
        textSize = 12f
        AppFonts.apply(this, bold = true)
        setPadding(0, dp(4), 0, dp(2))
    }

    private fun createEditText(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(palette.senaryColor())
        setHintTextColor(Color.parseColor("#666666"))
        setBackgroundColor(palette.quaternaryColor())
        textSize = 13f
        isSingleLine = true
        AppFonts.apply(this)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    private fun createButton(title: String, bgColor: Int, strokeColor: Int? = null): Button = Button(this).apply {
        text = title
        setTextColor(Color.WHITE)
        textSize = 12f
        val shape = GradientDrawable().apply {
            setColor(bgColor)
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
            cornerRadius = dp(4).toFloat()
        }
        background = shape
        AppFonts.apply(this, bold = true)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TEMPLATE_ID = "xyz.x3ofiz4.exvia.EXTRA_OCR_TEMPLATE_ID"
    }
}

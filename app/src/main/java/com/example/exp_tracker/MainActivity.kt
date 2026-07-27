package com.example.exp_tracker

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val PREFS = "exp_tracker_ui"
        private const val PREF_SELECTED_PATH = "selected_path"
        private val GREEN = Color.rgb(52, 199, 89)
    }

    private enum class Tab { TABLE, STAT, FILES }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var tokenStore: TokenStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var settings: RepoSettings

    private val palette get() = settings.palette
    private val PRIMARY get() = palette.primaryColor()
    private val SECONDARY get() = palette.secondaryColor()
    private val BLACK get() = palette.tertiaryColor()
    private val SURFACE get() = palette.quaternaryColor()
    private val MUTED get() = palette.quinaryColor()
    private val WHITE get() = palette.senaryColor()
    private val RED = Color.rgb(247, 35, 35)
    private val STAT_MEAN = Color.rgb(255, 176, 0)
    private val STAT_MEDIAN = Color.rgb(0, 194, 255)
    private val STAT_QUARTILE = Color.rgb(169, 112, 255)
    private val STAT_SPREAD = Color.rgb(255, 140, 66)
    private val STAT_SHAPE = Color.rgb(76, 201, 240)

    private lateinit var drawerRoot: SwipeSettingsLayout
    private lateinit var settingsDrawer: ScrollView
    private lateinit var statusText: TextView
    private lateinit var selectedFileText: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var tableScreen: LinearLayout
    private lateinit var statScreen: ScrollView
    private lateinit var statContent: LinearLayout
    private lateinit var filesScreen: LinearLayout
    private lateinit var dynamicForm: LinearLayout
    private lateinit var table: TableLayout
    private lateinit var filesList: LinearLayout
    private lateinit var filterInput: EditText
    private lateinit var filterToggle: TextView
    private lateinit var amendButton: Button
    private lateinit var createFileButton: Button
    private lateinit var removeFileButton: Button
    private lateinit var tableTabButton: Button
    private lateinit var statTabButton: Button
    private lateinit var filesTabButton: Button

    private lateinit var ownerSetting: EditText
    private lateinit var repoSetting: EditText
    private lateinit var branchSetting: EditText
    private lateinit var folderSetting: EditText
    private lateinit var defaultFileSetting: EditText
    private lateinit var arrayKeySetting: EditText
    private lateinit var dateKeySetting: EditText
    private lateinit var moneyKeySetting: EditText
    private lateinit var tickerKeySetting: EditText
    private lateinit var tagsKeySetting: EditText
    private lateinit var tokenSetting: EditText
    private lateinit var tickerColorsSetting: EditText
    private lateinit var themeSpinner: Spinner
    private lateinit var primarySetting: EditText
    private lateinit var secondarySetting: EditText
    private lateinit var tertiarySetting: EditText
    private lateinit var quaternarySetting: EditText
    private lateinit var quinarySetting: EditText
    private lateinit var senarySetting: EditText

    private val formInputs = linkedMapOf<String, EditText>()
    private var selectedPath: String? = null
    private var files: List<RepoFile> = emptyList()
    private var currentData = TableData(emptyList(), emptyList(), null, null, null, null)
    private var activeTab = Tab.TABLE
    private var busy = false
    private var filterEnabled = false
    private var filterQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        settingsStore = SettingsStore(this)
        settings = settingsStore.load()
        val lightPalette = isLightPalette(settings.palette)
        setTheme(if (lightPalette) R.style.AppTheme_Light else R.style.AppTheme_Dark)
        window.statusBarColor = BLACK
        window.navigationBarColor = BLACK
        window.decorView.systemUiVisibility = if (lightPalette) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else 0
        setContentView(buildUi())

        if (!settings.isConfigured()) {
            statusText.text = "Configure GitHub in Settings. Swipe right from the left edge."
            drawerRoot.openDrawer()
        } else if (tokenStore.load() == null) {
            statusText.text = "GitHub PAT is required. Add it in Settings."
            drawerRoot.openDrawer()
        } else {
            refreshFilesAndTable()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): SwipeSettingsLayout {
        drawerRoot = SwipeSettingsLayout(this).apply { setBackgroundColor(BLACK) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
            setBackgroundColor(BLACK)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "exp_tracker"
            textSize = 22f
            setTextColor(WHITE)
            setPadding(0, dp(2), 0, dp(4))
            AppFonts.apply(this, bold = true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(TextView(this).apply {
            text = "Settings ›"
            setTextColor(PRIMARY)
            setPadding(dp(12), dp(8), 0, dp(8))
            AppFonts.apply(this)
            setOnClickListener { drawerRoot.openDrawer() }
        })
        content.addView(titleRow, matchWidth())

        statusText = TextView(this).apply {
            setTextColor(MUTED)
            setPadding(0, dp(4), 0, dp(10))
            AppFonts.apply(this)
        }
        content.addView(statusText, matchWidth())

        contentHost = FrameLayout(this).apply { setBackgroundColor(BLACK) }
        tableScreen = buildTableScreen()
        statScreen = buildStatScreen().apply { visibility = View.GONE }
        filesScreen = buildFilesScreen().apply { visibility = View.GONE }
        contentHost.addView(tableScreen, frameMatch())
        contentHost.addView(statScreen, frameMatch())
        contentHost.addView(filesScreen, frameMatch())
        content.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tableTabButton = styledButton("Table").apply { setOnClickListener { showTab(Tab.TABLE) } }
        statTabButton = styledButton("Stat").apply { setOnClickListener { showTab(Tab.STAT) } }
        filesTabButton = styledButton("Files").apply { setOnClickListener { showTab(Tab.FILES) } }
        tabs.addView(tableTabButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(2) })
        tabs.addView(statTabButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(2) })
        tabs.addView(filesTabButton, LinearLayout.LayoutParams(0, dp(40), 1f))
        content.addView(tabs, matchWidth())
        drawerRoot.addView(content, frameMatch())

        val scrim = View(this).apply { visibility = View.GONE }
        drawerRoot.addView(scrim, frameMatch())
        settingsDrawer = buildSettingsDrawer()
        val drawerWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        drawerRoot.addView(settingsDrawer, FrameLayout.LayoutParams(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        drawerRoot.attachDrawer(settingsDrawer, scrim)
        updateTabButtons()
        AppFonts.applyToTree(drawerRoot)
        return drawerRoot
    }

    private fun buildTableScreen(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BLACK)

        selectedFileText = TextView(this@MainActivity).apply {
            text = "No file selected"
            setTextColor(WHITE)
            setPadding(0, dp(2), 0, dp(8))
            AppFonts.apply(this, bold = true)
        }
        addView(selectedFileText, matchWidth())

        dynamicForm = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        addView(dynamicForm, matchWidth())

        val addField = TextView(this@MainActivity).apply {
            text = "+ Add field"
            setTextColor(PRIMARY)
            setPadding(dp(8), dp(9), dp(8), dp(9))
            AppFonts.apply(this)
            setOnClickListener { promptAddField() }
        }
        addView(addField, matchWidth())

        amendButton = styledButton("Amend").apply { setOnClickListener { amend() } }
        addView(amendButton, spacedMatchWidth(4))

        val filterRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(6))
        }
        filterInput = styledInput("SELECT * WHERE …").apply {
            minHeight = dp(30)
            maxHeight = dp(30)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            textSize = 12f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterQuery = s?.toString().orEmpty()
                    if (filterEnabled) applyFilterAndRender(showStatus = false)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        filterToggle = TextView(this@MainActivity).apply {
            text = "Filter OFF"
            gravity = Gravity.CENTER
            setTextColor(MUTED)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            minHeight = dp(30)
            AppFonts.apply(this, bold = true)
            background = inactiveActionBackground(PRIMARY)
            setOnClickListener {
                filterEnabled = !filterEnabled
                updateFilterToggle()
                applyFilterAndRender(showStatus = true)
            }
        }
        filterRow.addView(filterInput, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(5) })
        filterRow.addView(filterToggle, LinearLayout.LayoutParams(dp(94), dp(30)))
        addView(filterRow, matchWidth())

        table = TableLayout(this@MainActivity).apply {
            isStretchAllColumns = false
            setBackgroundColor(BLACK)
        }
        val horizontal = HorizontalScrollView(this@MainActivity).apply {
            setBackgroundColor(BLACK)
            addView(table, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val vertical = ScrollView(this@MainActivity).apply {
            setBackgroundColor(BLACK)
            addView(horizontal, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(vertical, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        renderTable(currentData)
    }

    private fun buildStatScreen(): ScrollView {
        statContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            setPadding(0, 0, 0, dp(8))
        }
        return ScrollView(this).apply {
            setBackgroundColor(BLACK)
            addView(statContent, matchWidth())
        }
    }

    private fun buildFilesScreen(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BLACK)

        val folderTitle = TextView(this@MainActivity).apply {
            tag = "folder_title"
            setTextColor(WHITE)
            setPadding(0, dp(2), 0, dp(10))
            AppFonts.apply(this, bold = true)
        }
        addView(folderTitle, matchWidth())

        val actions = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        createFileButton = styledButton("Create").apply { setOnClickListener { promptCreateFile() } }
        removeFileButton = styledButton("Remove selected").apply { setOnClickListener { confirmRemoveSelectedFile() } }
        actions.addView(createFileButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(3) })
        actions.addView(removeFileButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3) })
        addView(actions, matchWidth())

        filesList = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        val scroll = ScrollView(this@MainActivity).apply {
            setBackgroundColor(BLACK)
            addView(filesList, matchWidth())
        }
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun buildSettingsDrawer(): ScrollView {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
            setBackgroundColor(BLACK)
        }
        body.addView(TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTextColor(WHITE)
            setPadding(0, 0, 0, dp(6))
            AppFonts.apply(this, bold = true)
        }, matchWidth())
        body.addView(TextView(this).apply {
            text = "Swipe left to close. Settings are saved locally; the GitHub PAT remains encrypted in Android Keystore."
            setTextColor(MUTED)
            setPadding(0, 0, 0, dp(14))
            AppFonts.apply(this)
        }, matchWidth())

        ownerSetting = settingInput("GitHub owner / org", settings.owner)
        repoSetting = settingInput("Repository", settings.repo)
        branchSetting = settingInput("Branch", settings.branch)
        folderSetting = settingInput("JSON folder", settings.folder)
        defaultFileSetting = settingInput("Default JSON file", settings.defaultJson)
        tokenSetting = settingInput("GitHub PAT — leave blank to keep current", "", password = true).apply {
            hint = if (tokenStore.load() == null) "GitHub PAT" else "${"*".repeat(12)} (stored; leave blank to keep)"
        }

        arrayKeySetting = settingInput("Object array key (fallback)", settings.arrayKey)
        dateKeySetting = settingInput("Date key override (optional)", settings.dateKeyOverride)
        moneyKeySetting = settingInput("Money key override (optional)", settings.moneyKeyOverride)
        tickerKeySetting = settingInput("Ticker/category key override (optional)", settings.tickerKeyOverride)
        tagsKeySetting = settingInput("Tags key override (optional)", settings.tagsKeyOverride)
        tickerColorsSetting = settingInput(
            "Ticker color mapping: FD=#FFB300",
            SettingsStore.colorsToText(settings.tickerColors),
            multiline = true,
        )

        primarySetting = settingInput("Primary", settings.palette.primary)
        secondarySetting = settingInput("Secondary", settings.palette.secondary)
        tertiarySetting = settingInput("Tertiary / background", settings.palette.tertiary)
        quaternarySetting = settingInput("Quaternary / surface", settings.palette.quaternary)
        quinarySetting = settingInput("Quinary / muted", settings.palette.quinary)
        senarySetting = settingInput("Senary / text", settings.palette.senary)

        val themeNames = ThemePreset.entries.map { it.displayName }
        val themeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, themeNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(WHITE)
                    setBackgroundColor(BLACK)
                    setPadding(dp(10), dp(12), dp(10), dp(12))
                    AppFonts.apply(this)
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(WHITE)
                    setBackgroundColor(SURFACE)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    AppFonts.apply(this)
                }
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        themeSpinner = Spinner(this).apply {
            adapter = themeAdapter
            background = outlinedBackground(false, SECONDARY)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        var suppressThemeSelection = true
        themeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressThemeSelection) applyThemeFields(ThemePreset.entries[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        themeSpinner.setSelection(ThemePreset.entries.indexOf(settings.themePreset).coerceAtLeast(0), false)
        themeSpinner.post { suppressThemeSelection = false }

        body.addView(accordion("GitHub", initiallyOpen = true) { container ->
            listOf(ownerSetting, repoSetting, branchSetting, folderSetting, defaultFileSetting, tokenSetting)
                .forEach { container.addView(it, spacedMatchWidth(6)) }
            container.addView(styledButton("Clear stored PAT", accent = SECONDARY).apply {
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Clear GitHub PAT?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear") { _, _ ->
                            tokenStore.clear()
                            tokenSetting.text.clear()
                            tokenSetting.hint = "GitHub PAT"
                            statusText.text = "Stored PAT cleared."
                        }.create().also { showDialog(it) }
                }
            }, spacedMatchWidth(8))
        }, spacedMatchWidth(10))

        body.addView(accordion("Color", initiallyOpen = false) { container ->
            container.addView(infoText("Theme").apply { AppFonts.apply(this, bold = true) }, spacedMatchWidth(4))
            container.addView(themeSpinner, spacedMatchWidth(8))
            container.addView(infoText("Preset selection fills the six palette values below. You can then override any color with #RRGGBB (or #AARRGGBB)."), spacedMatchWidth(8))
            listOf(primarySetting, secondarySetting, tertiarySetting, quaternarySetting, quinarySetting, senarySetting)
                .forEach { container.addView(it, spacedMatchWidth(6)) }
        }, spacedMatchWidth(10))

        body.addView(accordion("Schema & Display", initiallyOpen = false) { container ->
            listOf(arrayKeySetting, dateKeySetting, moneyKeySetting, tickerKeySetting, tagsKeySetting, tickerColorsSetting)
                .forEach { container.addView(it, spacedMatchWidth(6)) }
        }, spacedMatchWidth(12))

        body.addView(styledButton("Save settings and reload").apply { setOnClickListener { saveSettings() } }, spacedMatchWidth(8))
        body.addView(styledButton("Close Settings", accent = SECONDARY).apply { setOnClickListener { drawerRoot.closeDrawer() } }, spacedMatchWidth(8))

        return ScrollView(this).apply {
            setBackgroundColor(BLACK)
            addView(body, matchWidth())
        }
    }

    private fun applyThemeFields(preset: ThemePreset) {
        val p = ThemePalette.preset(preset)
        primarySetting.setText(p.primary)
        secondarySetting.setText(p.secondary)
        tertiarySetting.setText(p.tertiary)
        quaternarySetting.setText(p.quaternary)
        quinarySetting.setText(p.quinary)
        senarySetting.setText(p.senary)
    }

    private fun settingInput(hintText: String, value: String, password: Boolean = false, multiline: Boolean = false): EditText =
        styledInput(hintText).apply {
            setText(value)
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (multiline) {
                isSingleLine = false
                minLines = 4
                gravity = Gravity.TOP
            }
        }

    private fun saveSettings() {
        val paletteInputs = listOf(
            primarySetting to "Primary",
            secondarySetting to "Secondary",
            tertiarySetting to "Tertiary",
            quaternarySetting to "Quaternary",
            quinarySetting to "Quinary",
            senarySetting to "Senary",
        )
        var invalid = false
        paletteInputs.forEach { (input, label) ->
            val value = input.text.toString().trim()
            if (!ThemePalette.isValidHex(value)) {
                input.error = "$label must be #RRGGBB or #AARRGGBB"
                invalid = true
            }
        }
        if (invalid) return

        val selectedTheme = ThemePreset.entries.getOrElse(themeSpinner.selectedItemPosition) { ThemePreset.DEFAULT }
        val next = RepoSettings(
            owner = ownerSetting.text.toString().trim(),
            repo = repoSetting.text.toString().trim(),
            branch = branchSetting.text.toString().trim().ifBlank { "main" },
            folder = folderSetting.text.toString().trim().trim('/'),
            defaultJson = defaultFileSetting.text.toString().trim(),
            arrayKey = arrayKeySetting.text.toString().trim(),
            dateKeyOverride = dateKeySetting.text.toString().trim(),
            moneyKeyOverride = moneyKeySetting.text.toString().trim(),
            tickerKeyOverride = tickerKeySetting.text.toString().trim(),
            tagsKeyOverride = tagsKeySetting.text.toString().trim(),
            tickerColors = SettingsStore.parseTickerColors(tickerColorsSetting.text.toString()),
            themePreset = selectedTheme,
            palette = ThemePalette(
                primary = primarySetting.text.toString().trim(),
                secondary = secondarySetting.text.toString().trim(),
                tertiary = tertiarySetting.text.toString().trim(),
                quaternary = quaternarySetting.text.toString().trim(),
                quinary = quinarySetting.text.toString().trim(),
                senary = senarySetting.text.toString().trim(),
            ),
        )
        settingsStore.save(next)
        val newToken = tokenSetting.text.toString().trim()
        if (newToken.isNotBlank()) tokenStore.save(newToken)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_SELECTED_PATH).apply()
        recreate()
    }

    private fun refreshFilesAndTable() {
        val token = requireToken() ?: return
        setBusy(true, "Loading ${settings.folder.ifBlank { "/" }}/…")
        executor.execute {
            try {
                val api = GitHubApi(token, settings)
                val loadedFiles = api.listExpenseFiles()
                val savedPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SELECTED_PATH, null)
                val defaultPath = settings.pathFor(settings.defaultJson)
                val selected = loadedFiles.firstOrNull { it.path == savedPath }
                    ?: loadedFiles.firstOrNull { it.path == defaultPath }
                    ?: loadedFiles.firstOrNull()
                val data = selected?.let { api.fetchTable(it.path) } ?: TableData(emptyList(), emptyList(), null, null, null, null)
                runOnUiThread {
                    files = loadedFiles
                    setSelectedPath(selected?.path)
                    renderFiles()
                    applyTableData(data)
                    statusText.text = if (selected == null) "No .json files found in ${settings.folder}/." else "Loaded ${data.rows.size} row(s) from ${selected.name}."
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Could not load files") }
            }
        }
    }

    private fun refreshSelected(successMessage: String? = null) {
        val path = selectedPath ?: return
        val token = requireToken() ?: return
        setBusy(true, "Loading ${path.substringAfterLast('/')}…")
        executor.execute {
            try {
                val data = GitHubApi(token, settings).fetchTable(path)
                runOnUiThread {
                    applyTableData(data)
                    statusText.text = successMessage ?: "Loaded ${data.rows.size} row(s) from ${path.substringAfterLast('/')}."
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Could not load selected file") }
            }
        }
    }

    private fun applyTableData(data: TableData) {
        currentData = data
        renderDynamicForm(data)
        applyFilterAndRender(showStatus = false)
        if (::drawerRoot.isInitialized) AppFonts.applyToTree(drawerRoot)
    }

    private fun applyFilterAndRender(showStatus: Boolean) {
        val result = if (filterEnabled && filterQuery.isNotBlank()) SqlLikeFilter.apply(currentData, filterQuery) else SqlLikeFilter.FilterResult(currentData.rows)
        val data = if (result.error == null) currentData.copy(rows = result.rows) else currentData.copy(rows = emptyList())
        renderTable(data)
        renderStats(data)
        if (::filterInput.isInitialized) {
            filterInput.setTextColor(if (result.error == null) WHITE else RED)
        }
        if (showStatus || result.error != null) {
            statusText.text = when {
                result.error != null -> "Filter error: ${result.error}"
                filterEnabled -> "Filter enabled: showing ${data.rows.size}/${currentData.rows.size} row(s)."
                else -> "Filter disabled: showing ${currentData.rows.size} row(s)."
            }
        }
    }

    private fun updateFilterToggle() {
        if (!::filterToggle.isInitialized) return
        filterToggle.text = if (filterEnabled) "Filter ON" else "Filter OFF"
        filterToggle.setTextColor(if (filterEnabled) PRIMARY else MUTED)
        filterToggle.background = if (filterEnabled) activeButtonBackground(PRIMARY) else inactiveActionBackground(PRIMARY)
    }

    private fun renderDynamicForm(data: TableData, preserved: Map<String, String> = emptyMap()) {
        dynamicForm.removeAllViews()
        formInputs.clear()
        if (data.keys.isEmpty()) {
            dynamicForm.addView(TextView(this).apply {
                text = "No fields inferred yet. Use + Add field, or select a JSON file containing at least one object."
                setTextColor(MUTED)
                setPadding(dp(8), dp(8), dp(8), dp(10))
                AppFonts.apply(this)
            }, matchWidth())
            return
        }
        for (key in data.keys) {
            val initial = preserved[key] ?: if (key == data.dateKey) currentDateTime() else ""
            val input = inputForKey(key, initial, data)
            formInputs[key] = input
            dynamicForm.addView(input, spacedMatchWidth(4))
        }
    }

    private fun inputForKey(key: String, value: String, data: TableData): EditText {
        val suggestions = suggestionsForKey(key, data)
        return if (key == data.tagsKey) {
            MultiAutoCompleteTextView(this).apply {
                hint = "$key (optional)"
                inputType = InputType.TYPE_CLASS_TEXT
                isSingleLine = true
                threshold = 1
                setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
                setTextColor(WHITE)
                setHintTextColor(MUTED)
                backgroundTintList = inputTint()
                setPadding(dp(8), dp(5), dp(8), dp(5))
                minHeight = dp(46)
                AppFonts.apply(this)
                setAdapter(suggestionAdapter(suggestions))
                setText(value)
            }
        } else {
            AutoCompleteTextView(this).apply {
                hint = "$key (optional)"
                inputType = InputType.TYPE_CLASS_TEXT
                isSingleLine = true
                threshold = 1
                setTextColor(WHITE)
                setHintTextColor(MUTED)
                backgroundTintList = inputTint()
                setPadding(dp(8), dp(5), dp(8), dp(5))
                minHeight = dp(46)
                AppFonts.apply(this)
                setAdapter(suggestionAdapter(suggestions))
                setText(value)
            }
        }
    }

    private fun suggestionAdapter(items: List<String>): ArrayAdapter<String> = object : ArrayAdapter<String>(
        this, android.R.layout.simple_dropdown_item_1line, items,
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return (super.getView(position, convertView, parent) as TextView).apply {
                setTextColor(WHITE)
                setBackgroundColor(SURFACE)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                AppFonts.apply(this)
            }
        }
    }

    private fun suggestionsForKey(key: String, data: TableData): List<String> {
        val values = if (key == data.tagsKey) {
            data.rows.flatMap { it.values[key].orEmpty().split(',').map(String::trim).filter(String::isNotBlank) }
        } else {
            data.rows.map { it.values[key].orEmpty().trim() }.filter { it.isNotBlank() }
        }
        val counts = linkedMapOf<String, Pair<String, Int>>()
        for (value in values) {
            val normalized = value.lowercase()
            val old = counts[normalized]
            counts[normalized] = value to ((old?.second ?: 0) + 1)
        }
        return counts.values.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() }).map { it.first }
    }

    private fun promptAddField() {
        val input = styledInput("Field key, e.g. merchant")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add field")
            .setMessage("Adds a field to the current form. It becomes part of the JSON schema after a row is committed with a value.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = input.text.toString().trim()
                if (key.isBlank()) { input.error = "Key is required"; return@setOnClickListener }
                if (currentData.keys.any { it.equals(key, true) }) { input.error = "Field already exists"; return@setOnClickListener }
                val preserved = collectFormValues()
                val newData = currentData.copy(keys = currentData.keys + key)
                currentData = newData.copy(
                    dateKey = settings.detectDateKey(newData.keys),
                    moneyKey = settings.detectMoneyKey(newData.keys),
                    tickerKey = settings.detectTickerKey(newData.keys),
                    tagsKey = settings.detectTagsKey(newData.keys),
                )
                renderDynamicForm(currentData, preserved)
                applyFilterAndRender(showStatus = false)
                dialog.dismiss()
            }
        }
        showDialog(dialog)
    }

    private fun amend() {
        val path = selectedPath ?: run { statusText.text = "Select or create a JSON file first."; showTab(Tab.FILES); return }
        val token = requireToken() ?: return
        val values = collectFormValues()
        val preview = values.entries.filter { it.value.isNotBlank() }.take(8).joinToString("\n") { "${it.key}: ${it.value}" }
            .ifBlank { "All fields are blank; the repository writer may only add an inferred date." }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Amend expense?")
            .setMessage("$preview\n\nCommit this change to ${path.substringAfterLast('/')}?")
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                setBusy(true, "Committing to ${path.substringAfterLast('/')}…")
                executor.execute {
                    try {
                        val api = GitHubApi(token, settings)
                        val date = api.appendRow(path, values)
                        val data = api.fetchTable(path)
                        runOnUiThread {
                            applyTableData(data)
                            statusText.text = "Committed at $date."
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Amend failed") }
                    }
                }
            }
            .create()
        showDialog(dialog)
    }

    private fun collectFormValues(): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
        formInputs.forEach { (key, view) -> put(key, view.text.toString()) }
    }

    private fun renderTable(data: TableData) {
        table.removeAllViews()
        if (data.keys.isEmpty()) return
        val header = TableRow(this).apply {
            setBackgroundColor(BLACK)
            data.keys.forEach { addView(cell(it.uppercase(), header = true)) }
            addView(cell("", header = true))
        }
        addTableRow(header)
        data.rows.forEach { row ->
            val tr = TableRow(this).apply { setBackgroundColor(BLACK) }
            data.keys.forEach { key ->
                var textColor = WHITE
                val value = row.values[key].orEmpty()
                if (key == data.moneyKey) textColor = if (value.trim().startsWith("+")) GREEN else RED
                if (key == data.tickerKey) textColor = tickerColor(value)
                tr.addView(cell(value, textColor = textColor, onClick = { editRow(row) }))
            }
            tr.addView(cell("×", textColor = PRIMARY, onClick = { confirmDeleteRow(row) }).apply {
                gravity = Gravity.CENTER
                AppFonts.apply(this, bold = true)
            })
            addTableRow(tr)
        }
    }

    private fun editRow(row: DynamicRow) {
        val path = selectedPath ?: return
        val token = requireToken() ?: return
        val editInputs = linkedMapOf<String, EditText>()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(BLACK)
        }
        currentData.keys.forEach { key ->
            val input = inputForKey(key, row.values[key].orEmpty(), currentData)
            editInputs[key] = input
            form.addView(input, matchWidth())
        }
        val scroll = ScrollView(this).apply { addView(form, matchWidth()) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit row")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val values = linkedMapOf<String, String>().apply {
                    editInputs.forEach { (key, view) -> put(key, view.text.toString()) }
                }
                dialog.dismiss()
                setBusy(true, "Updating row…")
                executor.execute {
                    try {
                        val api = GitHubApi(token, settings)
                        api.updateRow(path, row, values)
                        val data = api.fetchTable(path)
                        runOnUiThread { applyTableData(data); statusText.text = "Row updated."; setBusy(false) }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Update failed") }
                    }
                }
            }
        }
        showDialog(dialog)
    }

    private fun confirmDeleteRow(row: DynamicRow) {
        val path = selectedPath ?: return
        val token = requireToken() ?: return
        val preview = currentData.keys.take(4).joinToString("\n") { "$it: ${row.values[it].orEmpty()}" }
        AlertDialog.Builder(this)
            .setTitle("Delete row?")
            .setMessage("$preview\n\nThis commits the deletion to ${settings.branch}.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                setBusy(true, "Deleting row…")
                executor.execute {
                    try {
                        val api = GitHubApi(token, settings)
                        api.deleteRow(path, row)
                        val data = api.fetchTable(path)
                        runOnUiThread { applyTableData(data); statusText.text = "Row deleted."; setBusy(false) }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Delete failed") }
                    }
                }
            }.create().also { showDialog(it) }
    }

    private fun renderStats(data: TableData) {
        statContent.removeAllViews()
        if (selectedPath == null) {
            statContent.addView(infoText("Select a JSON file first."), matchWidth())
            return
        }
        val finance = Statistics.financeStats(data)
        if (finance != null) {
            statContent.addView(accordion("Personal finance") { container ->
                addMetric(container, "Money key", finance.moneyKey, valueColor = WHITE)
                addMetric(container, "Total income", fmt(finance.totalIncome), nameColor = GREEN, valueColor = GREEN)
                addMetric(container, "Total expenses", fmt(finance.totalExpenses), nameColor = RED, valueColor = RED)
                addSignedMetric(container, "Net cash flow", finance.netCashFlow)
                addSignedMetric(container, "Savings rate", finance.savingsRate, suffix = "%")
                addMetric(container, "Average expense", finance.averageExpense?.let(::fmt) ?: "N/A", nameColor = RED, valueColor = if (finance.averageExpense != null) RED else MUTED)
                addMetric(container, "Average income", finance.averageIncome?.let(::fmt) ?: "N/A", nameColor = GREEN, valueColor = if (finance.averageIncome != null) GREEN else MUTED)
                addMetric(container, "Largest expense", finance.largestExpense?.let(::fmt) ?: "N/A", nameColor = RED, valueColor = if (finance.largestExpense != null) RED else MUTED)
                addMetric(container, "Largest income", finance.largestIncome?.let(::fmt) ?: "N/A", nameColor = GREEN, valueColor = if (finance.largestIncome != null) GREEN else MUTED)
                addMetric(container, "Transaction count", finance.transactionCount.toString(), nameColor = MUTED)
                addMetric(container, "Period", listOfNotNull(finance.firstDate, finance.lastDate).joinToString(" → ").ifBlank { "N/A" }, nameColor = MUTED)
                if (finance.categorySpending.isNotEmpty()) {
                    container.addView(infoText("Spending by ${data.tickerKey ?: "category"}").apply { AppFonts.apply(this, bold = true) }, matchWidth())
                    finance.categorySpending.forEach { (name, amount) -> addMetric(container, name, fmt(amount), valueColor = RED) }
                }
            }, matchWidth())
        }

        if (data.keys.isEmpty()) {
            statContent.addView(infoText("No inferred keys in this file."), matchWidth())
            return
        }
        data.keys.forEach { key ->
            val values = data.rows.map { it.values[key].orEmpty() }
            val stats = Statistics.keyStats(values)
            statContent.addView(accordion(key) { container ->
                val graph = buildGraph(data, key)
                graph.view?.let { container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340))) }
                graph.legend?.let { container.addView(infoText(it).apply { setTextColor(MUTED) }, matchWidth()) }

                if (key == data.moneyKey) {
                    container.addView(infoText("Cumulative $key").apply {
                        setTextColor(STAT_MEDIAN)
                        setPadding(dp(6), dp(12), dp(6), dp(5))
                        AppFonts.apply(this, bold = true)
                    }, matchWidth())
                    val accumulation = buildAccumulationPlot(data, key)
                    accumulation.view?.let { container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230))) }
                    accumulation.legend?.let { container.addView(infoText(it).apply { setTextColor(MUTED) }, matchWidth()) }

                    container.addView(infoText("Normal distribution of $key").apply {
                        setTextColor(STAT_QUARTILE)
                        setPadding(dp(6), dp(12), dp(6), dp(5))
                        AppFonts.apply(this, bold = true)
                    }, matchWidth())
                    val normal = buildNormalPlot(data, key)
                    normal.view?.let { container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230))) }
                    normal.legend?.let { container.addView(infoText(it).apply { setTextColor(MUTED) }, matchWidth()) }
                }

                addStatisticMetric(container, "Mean", stats.mean, STAT_MEAN)
                addStatisticMetric(container, "Median", stats.median, STAT_MEDIAN)
                addStatisticMetric(container, "Mean − Median gap", stats.meanMedianGap, if ((stats.meanMedianGap ?: 0.0) < 0.0) RED else GREEN)
                val modeNumber = stats.mode?.let { Statistics.parseNumber(it) }
                addMetric(
                    container,
                    "Mode",
                    stats.mode ?: "N/A",
                    nameColor = STAT_MEDIAN,
                    valueColor = when {
                        stats.mode == null -> MUTED
                        modeNumber != null && modeNumber < 0.0 -> RED
                        else -> STAT_MEDIAN
                    },
                )
                addStatisticMetric(container, "Sum", stats.sum, GREEN)
                addStatisticMetric(container, "STDV", stats.stdv, STAT_SPREAD)
                addStatisticMetric(container, "Minimum", stats.minimum, STAT_QUARTILE)
                addStatisticMetric(container, "Maximum", stats.maximum, STAT_QUARTILE)
                addStatisticMetric(container, "Range", stats.range, STAT_SPREAD)
                addStatisticMetric(container, "Q1", stats.q1, STAT_QUARTILE)
                addStatisticMetric(container, "Q2", stats.q2, STAT_MEDIAN)
                addStatisticMetric(container, "Q3", stats.q3, STAT_QUARTILE)
                addStatisticMetric(container, "IQR", stats.iqr, STAT_SPREAD)
                addStatisticMetric(container, "Skew", stats.skew, STAT_SHAPE)
                addStatisticMetric(container, "Kurtosis", stats.kurtosis, STAT_SHAPE)
                addMetric(container, "n", stats.n.toString(), nameColor = MUTED, valueColor = WHITE)
                addMetric(container, "n unique", stats.nUnique.toString(), nameColor = MUTED, valueColor = WHITE)
                addStatisticMetric(container, "Variance", stats.variance, STAT_SPREAD)
                if (stats.numericN != stats.n) addMetric(container, "Numeric n", stats.numericN.toString(), nameColor = MUTED, valueColor = WHITE)
            }, matchWidth())
        }
    }

    private data class GraphResult(val view: CumulativeBoxPlotView?, val legend: String?)

    private fun buildGraph(data: TableData, key: String): GraphResult {
        val dateKey = data.dateKey ?: return GraphResult(null, "No date key detected; set a Date key override in Settings if needed.")
        if (key == dateKey) return GraphResult(null, "The date key is the time axis; a distribution box plot is not meaningful for the date values themselves.")

        val datedNumeric = data.rows.mapNotNull { row ->
            val x = Statistics.parseDate(row.values[dateKey].orEmpty()) ?: return@mapNotNull null
            val y = Statistics.parseNumber(row.values[key].orEmpty()) ?: return@mapNotNull null
            x to y
        }.sortedBy { it.first }

        if (datedNumeric.isEmpty()) {
            return GraphResult(null, "Cumulative box timeline requires dated numeric values for this key.")
        }

        val series = Statistics.cumulativeBoxSeries(datedNumeric)
        val view = CumulativeBoxPlotView(this).apply {
            setPalette(BLACK, MUTED, WHITE, SURFACE)
            setSeries(series)
        }
        val totalDated = data.rows.count { Statistics.parseDate(it.values[dateKey].orEmpty()) != null }
        val missingDates = data.rows.size - totalDated
        val nonNumericDated = totalDated - datedNumeric.size
        val omitted = buildList {
            if (missingDates > 0) add("$missingDates row(s) have missing/unparseable dates")
            if (nonNumericDated > 0) add("$nonNumericDated dated row(s) are non-numeric for this key")
        }.joinToString("; ")
        val omissionText = if (omitted.isBlank()) "" else " Omitted: $omitted."
        return GraphResult(
            view,
            "Timestamp totals: rows sharing an exact datetime are summed before statistics; ${datedNumeric.size} numeric row(s) became ${series.size} timestamp observation(s). Real gaps in dates are preserved. Q1–Q3 = solid box · green when current total > previous total, otherwise red · first box neutral · median = solid contrast line · mean = dotted contrast line · whiskers = mean ± 1σ and follow box color · tiny red hollow ○ = Tukey outlier · blue ◆ = total at that datetime · translucent blue path = timestamp totals.$omissionText Tap a box to inspect; pinch to zoom; drag to pan; double-tap to reset.",
        )
    }

    private data class SimplePlotResult(val view: InteractiveLinePlotView?, val legend: String?)

    private fun buildAccumulationPlot(data: TableData, key: String): SimplePlotResult {
        val dateKey = data.dateKey ?: return SimplePlotResult(null, "No date key detected for cumulative timeline.")
        val dated = data.rows.mapNotNull { row ->
            val x = Statistics.parseDate(row.values[dateKey].orEmpty()) ?: return@mapNotNull null
            val y = Statistics.parseNumber(row.values[key].orEmpty()) ?: return@mapNotNull null
            x to y
        }
        if (dated.isEmpty()) return SimplePlotResult(null, "No dated numeric $key values in the current dataset.")
        val series = Statistics.cumulativeTotalSeries(dated)
        val view = InteractiveLinePlotView(this).apply {
            setPalette(BLACK, MUTED, WHITE, SURFACE, Color.rgb(61, 139, 255))
            setSeries(series.map { it.first.toDouble() to it.second }, InteractiveLinePlotView.AxisKind.TIME)
        }
        return SimplePlotResult(view, "Running sum of timestamp-level $key totals. Filtering changes this plot. Pinch to zoom, drag to pan, double-tap to reset.")
    }

    private fun buildNormalPlot(data: TableData, key: String): SimplePlotResult {
        val values = data.rows.mapNotNull { Statistics.parseNumber(it.values[key].orEmpty()) }
        if (values.isEmpty()) return SimplePlotResult(null, "No numeric $key values in the current dataset.")
        val curve = Statistics.normalDistribution(values)
        val view = InteractiveLinePlotView(this).apply {
            setPalette(BLACK, MUTED, WHITE, SURFACE, STAT_QUARTILE)
            setSeries(curve, InteractiveLinePlotView.AxisKind.NUMBER, rugSamples = values)
        }
        val note = if (values.distinct().size <= 1) " All values are identical, so σ = 0 and the fitted distribution collapses to one point." else ""
        return SimplePlotResult(view, "Fitted normal PDF from the current $key subset; bottom ticks show observed values.$note Pinch to zoom, drag to pan, double-tap to reset.")
    }

    private fun accordion(title: String, initiallyOpen: Boolean = false, build: (LinearLayout) -> Unit): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (initiallyOpen) View.VISIBLE else View.GONE
            setPadding(dp(10), dp(8), dp(10), dp(12))
            setBackgroundColor(BLACK)
        }
        build(content)
        val header = TextView(this).apply {
            text = if (initiallyOpen) "▾ $title" else "▸ $title"
            textSize = 17f
            setTextColor(if (initiallyOpen) PRIMARY else WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minHeight = dp(38)
            background = if (initiallyOpen) activeButtonBackground(PRIMARY) else noBorderBackground()
            AppFonts.apply(this, bold = true)
            setOnClickListener {
                val opening = content.visibility != View.VISIBLE
                content.visibility = if (opening) View.VISIBLE else View.GONE
                text = if (opening) "▾ $title" else "▸ $title"
                setTextColor(if (opening) PRIMARY else WHITE)
                background = if (opening) activeButtonBackground(PRIMARY) else noBorderBackground()
            }
        }
        wrapper.addView(header, matchWidth())
        wrapper.addView(content, matchWidth())
        return wrapper
    }

    private fun addMetric(
        container: LinearLayout,
        name: String,
        value: String,
        nameColor: Int = MUTED,
        valueColor: Int = WHITE,
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(infoText(name).apply { setTextColor(nameColor) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(infoText(value).apply {
            gravity = Gravity.END
            setTextColor(valueColor)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(row, matchWidth())
    }

    private fun addStatisticMetric(container: LinearLayout, name: String, value: Double?, metricColor: Int) {
        val color = when {
            value == null || value.isNaN() || value.isInfinite() -> MUTED
            value < 0.0 -> RED
            else -> metricColor
        }
        addMetric(container, name, value?.let(::fmt) ?: "N/A", nameColor = metricColor, valueColor = color)
    }

    private fun addSignedMetric(container: LinearLayout, name: String, value: Double?, suffix: String = "") {
        val color = when {
            value == null || value.isNaN() || value.isInfinite() -> MUTED
            value < 0.0 -> RED
            value > 0.0 -> GREEN
            else -> MUTED
        }
        addMetric(container, name, value?.let { fmt(it) + suffix } ?: "N/A", nameColor = color, valueColor = color)
    }

    private fun infoText(textValue: String): TextView = TextView(this).apply {
        text = textValue
        setTextColor(WHITE)
        setPadding(dp(6), dp(5), dp(6), dp(5))
        AppFonts.apply(this)
    }

    private fun fmt(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> "N/A"
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }

    private fun promptCreateFile() {
        val token = requireToken() ?: return
        val suggested = YearMonth.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".json"
        val input = styledInput("File name").apply { setText(suggested); selectAll() }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Create JSON file")
            .setMessage("Creates an empty JSON array under ${settings.folder}/. Use + Add field in Table to seed a new schema.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().trim().isBlank()) { input.error = "File name is required"; return@setOnClickListener }
                val name = input.text.toString().trim()
                dialog.dismiss()
                setBusy(true, "Creating file…")
                executor.execute {
                    try {
                        val api = GitHubApi(token, settings)
                        val created = api.createExpenseFile(name)
                        val loadedFiles = api.listExpenseFiles()
                        val data = api.fetchTable(created.path)
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(created.path)
                            renderFiles()
                            applyTableData(data)
                            showTab(Tab.TABLE)
                            statusText.text = "Created and selected ${created.name}."
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Create file failed") }
                    }
                }
            }
        }
        showDialog(dialog)
    }

    private fun confirmRemoveSelectedFile() {
        val path = selectedPath ?: run { statusText.text = "No file is selected."; return }
        val file = files.firstOrNull { it.path == path } ?: return
        val token = requireToken() ?: return
        AlertDialog.Builder(this)
            .setTitle("Remove ${file.name}?")
            .setMessage("This deletes the entire JSON file from ${settings.branch} and commits the change.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                setBusy(true, "Removing ${file.name}…")
                executor.execute {
                    try {
                        val api = GitHubApi(token, settings)
                        api.deleteExpenseFile(file)
                        val loadedFiles = api.listExpenseFiles()
                        val next = loadedFiles.firstOrNull()
                        val data = next?.let { api.fetchTable(it.path) } ?: TableData(emptyList(), emptyList(), null, null, null, null)
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(next?.path)
                            renderFiles()
                            applyTableData(data)
                            statusText.text = if (next == null) "Removed ${file.name}. No JSON files remain." else "Removed ${file.name}. Selected ${next.name}."
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Remove file failed") }
                    }
                }
            }.create().also { showDialog(it) }
    }

    private fun renderFiles() {
        val title = filesScreen.findViewWithTag<TextView>("folder_title")
        title?.text = "${settings.folder.trimEnd('/')}/"
        filesList.removeAllViews()
        if (files.isEmpty()) {
            filesList.addView(infoText("No .json files"), matchWidth())
            removeFileButton.isEnabled = false
            return
        }
        removeFileButton.isEnabled = !busy && selectedPath != null
        files.forEach { file ->
            val selected = file.path == selectedPath
            filesList.addView(TextView(this).apply {
                text = file.name
                textSize = 17f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setTextColor(if (selected) PRIMARY else WHITE)
                background = outlinedBackground(selected)
                AppFonts.apply(this, bold = selected)
                setOnClickListener {
                    if (busy) return@setOnClickListener
                    setSelectedPath(file.path)
                    renderFiles()
                    showTab(Tab.TABLE)
                    refreshSelected()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 1 })
        }
    }

    private fun addTableRow(row: TableRow) {
        table.addView(row, TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 1 // one physical pixel
        })
    }

    private fun cell(textValue: String, header: Boolean = false, textColor: Int = WHITE, onClick: (() -> Unit)? = null): TextView =
        TextView(this).apply {
            text = textValue
            setTextColor(if (header) MUTED else textColor)
            setBackgroundColor(BLACK)
            setPadding(dp(8), 3, dp(8), 3)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            AppFonts.apply(this, bold = header)
            if (onClick != null) setOnClickListener { if (!busy) onClick() }
        }

    private fun tickerColor(value: String): Int {
        val configured = settings.tickerColors.entries.firstOrNull { it.key.equals(value.trim(), true) }?.value
            ?: return WHITE
        return try { Color.parseColor(configured) } catch (_: IllegalArgumentException) { WHITE }
    }

    private fun showTab(tab: Tab) {
        activeTab = tab
        tableScreen.visibility = if (tab == Tab.TABLE) View.VISIBLE else View.GONE
        statScreen.visibility = if (tab == Tab.STAT) View.VISIBLE else View.GONE
        filesScreen.visibility = if (tab == Tab.FILES) View.VISIBLE else View.GONE
        updateTabButtons()
    }

    private fun updateTabButtons() {
        if (!::tableTabButton.isInitialized) return
        styleTab(tableTabButton, activeTab == Tab.TABLE)
        styleTab(statTabButton, activeTab == Tab.STAT)
        styleTab(filesTabButton, activeTab == Tab.FILES)
    }

    private fun styleTab(button: Button, active: Boolean) {
        val accent = if (active) PRIMARY else SECONDARY
        button.setTextColor(if (active) PRIMARY else MUTED)
        button.background = if (active) activeButtonBackground(accent) else noBorderBackground()
        AppFonts.apply(button, bold = active)
    }

    private fun setSelectedPath(path: String?) {
        selectedPath = path
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            if (path == null) remove(PREF_SELECTED_PATH) else putString(PREF_SELECTED_PATH, path)
        }.apply()
        if (::selectedFileText.isInitialized) selectedFileText.text = path?.substringAfterLast('/') ?: "No file selected"
    }

    private fun requireToken(): String? {
        if (!settings.isConfigured()) {
            statusText.text = "Configure GitHub owner/repository in Settings."
            drawerRoot.openDrawer()
            return null
        }
        val token = tokenStore.load()
        if (token == null) {
            statusText.text = "Add a GitHub PAT in Settings."
            drawerRoot.openDrawer()
        }
        return token
    }

    private fun setBusy(isBusy: Boolean, message: String? = null) {
        busy = isBusy
        if (::amendButton.isInitialized) amendButton.isEnabled = !isBusy && selectedPath != null
        if (::createFileButton.isInitialized) createFileButton.isEnabled = !isBusy
        if (::removeFileButton.isInitialized) removeFileButton.isEnabled = !isBusy && selectedPath != null
        formInputs.values.forEach { it.isEnabled = !isBusy }
        if (message != null) statusText.text = message
    }

    private fun handleError(error: Exception, prefix: String) {
        setBusy(false)
        if (error is GitHubHttpException && error.statusCode == 401) {
            tokenStore.clear()
            statusText.text = "$prefix: authentication failed. Update the PAT in Settings."
            drawerRoot.openDrawer()
            return
        }
        val extra = when (error) {
            is GitHubHttpException -> when (error.statusCode) {
                409 -> " The branch/file changed; reload and try again."
                403 -> " Check PAT permissions or repository rules."
                404 -> " The configured repository/folder/file was not found."
                else -> ""
            }
            else -> ""
        }
        statusText.text = "$prefix: ${error.message ?: error.javaClass.simpleName}.$extra"
    }

    private fun showDialog(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.decorView?.let { AppFonts.applyToTree(it) }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(PRIMARY)
            it.background = inactiveActionBackground(PRIMARY)
            it.minHeight = dp(36)
            AppFonts.apply(it, bold = true)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let {
            it.setTextColor(MUTED)
            it.background = inactiveActionBackground(SECONDARY)
            it.minHeight = dp(36)
            AppFonts.apply(it)
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let {
            it.setTextColor(MUTED)
            it.background = inactiveActionBackground(SECONDARY)
            it.minHeight = dp(36)
            AppFonts.apply(it)
        }
    }

    private fun currentDateTime(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yy @ HH:mm"))

    private fun styledInput(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
        setTextColor(WHITE)
        setHintTextColor(MUTED)
        backgroundTintList = inputTint()
        setPadding(dp(8), dp(5), dp(8), dp(5))
        minHeight = dp(46)
        AppFonts.apply(this)
    }

    private fun inputTint(): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(),
        ),
        intArrayOf(PRIMARY, SECONDARY),
    )

    private fun styledButton(label: String, accent: Int = PRIMARY): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(WHITE)
        background = inactiveActionBackground(accent)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        minHeight = dp(38)
        minimumHeight = dp(38)
        AppFonts.apply(this, bold = true)
    }

    /** No border at rest; focus/press/selected is the active visual state. */
    private fun inactiveActionBackground(accent: Int): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), activeButtonBackground(accent))
        addState(intArrayOf(android.R.attr.state_focused), activeButtonBackground(accent))
        addState(intArrayOf(android.R.attr.state_selected), activeButtonBackground(accent))
        addState(intArrayOf(), noBorderBackground())
    }

    private fun noBorderBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(BLACK)
        cornerRadius = dp(3).toFloat()
    }

    private fun activeButtonBackground(strokeColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(BLACK)
        setStroke(dp(1).coerceAtLeast(1), strokeColor)
        cornerRadius = dp(3).toFloat()
    }

    private fun outlinedBackground(selected: Boolean, strokeColor: Int = if (selected) PRIMARY else SECONDARY): GradientDrawable =
        if (selected) activeButtonBackground(strokeColor) else noBorderBackground()

    private fun isLightPalette(value: ThemePalette): Boolean {
        val color = value.tertiaryColor()
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.6
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun spacedMatchWidth(bottomDp: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottomDp) }
    private fun frameMatch() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

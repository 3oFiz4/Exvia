package com.example.exp_tracker

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val PREFS = "exvia_ui"
        private const val LEGACY_PREFS = "exp_tracker_ui"
        private const val PREF_SELECTED_PATH = "selected_path"
        private val GREEN = Color.rgb(52, 199, 89)
    }

    private enum class Tab { TABLE, STAT, FILES }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var tokenStore: TokenStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var settings: RepoSettings
    private lateinit var tooltipController: TooltipController
    private lateinit var plotRuntime: PlotWebRuntime
    private lateinit var customMetricEngine: CustomMetricEngine
    private lateinit var fileCache: ExviaFileCache
    private val uiHandler = Handler(Looper.getMainLooper())

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
    private lateinit var resyncButton: TextView
    private lateinit var titleText: TextView
    private lateinit var filteringMethodButton: TextView

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
    private lateinit var plotColumnsSetting: EditText
    private lateinit var financeColumnsSetting: EditText
    private lateinit var plotBackgroundSetting: EditText
    private lateinit var plotSurfaceSetting: EditText
    private lateinit var plotTextSetting: EditText
    private lateinit var plotMutedSetting: EditText
    private lateinit var plotGridSetting: EditText
    private lateinit var plotAxisSetting: EditText
    private lateinit var plotPositiveSetting: EditText
    private lateinit var plotNegativeSetting: EditText
    private lateinit var plotObservationSetting: EditText
    private lateinit var plotOutlierSetting: EditText
    private lateinit var plotCenterSetting: EditText
    private lateinit var plotAccentSetting: EditText
    private lateinit var reportRepoSetting: EditText
    private lateinit var uiScaleSetting: EditText
    private lateinit var textScaleSetting: EditText
    private lateinit var customMetricList: LinearLayout
    private lateinit var customPlotList: LinearLayout
    private var customMetricsDraft = mutableListOf<CustomMetricDefinition>()
    private var customPlotsDraft = mutableListOf<CustomPlotDefinition>()
    private var filterSnippets = mutableListOf<FilterSnippet>()

    private val formInputs = linkedMapOf<String, EditText>()
    private var selectedPath: String? = null
    private var files: List<RepoFile> = emptyList()
    private var currentData = TableData(emptyList(), emptyList(), null, null, null, null)
    private var activeTab = Tab.TABLE
    private var busy = false
    private var filterEnabled = false
    private var filterQuery = ""
    private var developerMode = true
    private var titleTapCount = 0
    private var lastTitleTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = TokenStore(this)
        settingsStore = SettingsStore(this)
        settings = settingsStore.load()
        developerMode = settingsStore.developerModeEnabled()
        customMetricsDraft = settings.customMetrics.toMutableList()
        customPlotsDraft = settings.customPlots.toMutableList()
        filterSnippets = settingsStore.loadFilterSnippets().toMutableList()
        tooltipController = TooltipController(this, { PRIMARY }, { BLACK }, { WHITE })
        fileCache = ExviaFileCache(this)
        migrateUiPreferences()
        val lightPalette = isLightPalette(settings.palette)
        setTheme(if (lightPalette) R.style.AppTheme_Light else R.style.AppTheme_Dark)
        window.statusBarColor = BLACK
        window.navigationBarColor = BLACK
        window.decorView.systemUiVisibility = if (lightPalette) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else 0
        plotRuntime = PlotWebRuntime(this)
        customMetricEngine = CustomMetricEngine(plotRuntime) { settings.plotTheme }
        setContentView(buildUi())
        // Let the first Android frame render, then warm the shared JavaScript runtime.
        window.decorView.post { plotRuntime.prewarm() }

        val token = tokenStore.load()
        when {
            token == null -> {
                statusText.text = "GitHub PAT is required. Add it in Settings."
                drawerRoot.openDrawer()
            }
            !settingsStore.repoInitializationAsked() -> promptRepositoryInitialization()
            !settings.isConfigured() -> {
                statusText.text = "Configure GitHub in Settings. Swipe right from the left edge."
                drawerRoot.openDrawer()
            }
            else -> loadCachedOrRefresh()
        }
    }

    override fun onDestroy() {
        plotRuntime.destroy()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun migrateUiPreferences() {
        val current = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!current.contains(PREF_SELECTED_PATH)) {
            val legacy = getSharedPreferences(LEGACY_PREFS, MODE_PRIVATE)
            legacy.getString(PREF_SELECTED_PATH, null)?.let {
                current.edit().putString(PREF_SELECTED_PATH, it).apply()
            }
        }
    }

    private fun handleTitleTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTitleTapAt > 1_100L) titleTapCount = 0
        lastTitleTapAt = now
        titleTapCount += 1
        if (titleTapCount < 3) return
        titleTapCount = 0
        developerMode = !developerMode
        settingsStore.setDeveloperModeEnabled(developerMode)
        Toast.makeText(
            this,
            if (developerMode) "Developer Options enabled" else "Developer Options hidden",
            Toast.LENGTH_SHORT,
        ).show()
        recreate()
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
        val logoBitmap = runCatching {
            assets.open("logo.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (logoBitmap != null) {
            titleRow.addView(ImageView(this).apply {
                setImageBitmap(logoBitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "Exvia logo"
            }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(9) })
        }
        titleText = TextView(this).apply {
            text = "Exvia"
            textSize = 22f
            setTextColor(WHITE)
            setPadding(0, dp(2), 0, dp(4))
            AppFonts.apply(this, bold = true, textScale = settings.textScale)
            setOnClickListener { handleTitleTap() }
        }
        titleRow.addView(titleText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resyncButton = TextView(this).apply {
            text = "Re-sync"
            setTextColor(PRIMARY)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = noBorderBackground()
            AppFonts.apply(this, bold = true)
            setOnClickListener { if (!busy) refreshFilesAndTable() }
        }
        titleRow.addView(resyncButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(7) })
        titleRow.addView(TextView(this).apply {
            text = "Exvia Settings ›"
            setTextColor(PRIMARY)
            setPadding(dp(8), dp(8), 0, dp(8))
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
        AppFonts.applyToTree(drawerRoot, settings.textScale)
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
            isLongClickable = false
            minHeight = dp(30)
            maxHeight = dp(30)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            textSize = 12f
            setOnLongClickListener { true }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterQuery = s?.toString().orEmpty()
                    if (filterEnabled) applyFilterAndRender(showStatus = false)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            attachTimedHold(this, 1_000L) { showFilterSnippetManager() }
        }
        filteringMethodButton = TextView(this@MainActivity).apply {
            text = "Filtering method"
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(PRIMARY)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            minHeight = dp(30)
            background = inactiveActionBackground(PRIMARY)
            AppFonts.apply(this, bold = true)
            setOnClickListener { showFilteringMethodManager() }
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
        val filterControl: View = if (developerMode) filterInput else filteringMethodButton
        filterRow.addView(filterControl, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(5) })
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

    private data class ConfigField(val wrapper: LinearLayout, val input: EditText)

    private fun buildSettingsDrawer(): ScrollView {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
            setBackgroundColor(BLACK)
        }
        body.addView(TextView(this).apply {
            text = "Exvia Settings"
            textSize = 24f
            setTextColor(WHITE)
            setPadding(0, 0, 0, dp(6))
            AppFonts.apply(this, bold = true, textScale = settings.textScale)
        }, matchWidth())
        body.addView(TextView(this).apply {
            text = if (developerMode) {
                "Developer Options are ON. Triple-tap the Exvia title to hide advanced controls. Swipe left to close."
            } else {
                "Regular mode. Triple-tap the Exvia title to show Developer Options. Swipe left to close."
            }
            setTextColor(MUTED)
            setPadding(0, 0, 0, dp(14))
            AppFonts.apply(this, textScale = settings.textScale)
        }, matchWidth())

        // Initialize every field even when its advanced section is hidden, so Save remains deterministic.
        val owner = configField("GitHub owner / username", "github.owner", settings.owner, "GitHub account that owns the expense repository and the report repository.")
        ownerSetting = owner.input
        val repo = configField("Repository", "github.repo", settings.repo, "Repository name containing the JSON expense folder.")
        repoSetting = repo.input
        val branch = configField("Branch", "github.branch", settings.branch, "Branch used for every read and write. Usually main.")
        branchSetting = branch.input
        val folder = configField("JSON folder", "github.folder", settings.folder, "Folder directly containing selectable JSON files, for example Financial.")
        folderSetting = folder.input
        val defaultFile = configField("Default JSON file", "github.default_file", settings.defaultJson, "Preferred JSON file selected when Exvia loads.")
        defaultFileSetting = defaultFile.input
        val reportRepo = configField("Report issue repository", "github.report_repo", settings.reportRepo, "Repository that receives bug, enhancement, and feature reports. The owner is github.owner.")
        reportRepoSetting = reportRepo.input
        val token = configField("GitHub PAT", "github.pat", "", "Personal access token used for repository files and issue creation. It is encrypted locally and never uploaded in the synchronized config file.", password = true)
        tokenSetting = token.input.apply {
            hint = if (tokenStore.load() == null) "github.pat" else "github.pat  ${"*".repeat(12)}"
        }

        val array = configField("Object array key (fallback)", "schema.array_key", settings.arrayKey, "If the JSON root is an object rather than an array, this key selects the row array.")
        arrayKeySetting = array.input
        val date = configField("Date key override", "schema.date_key", settings.dateKeyOverride, "Optional explicit date/datetime column. Common date-like keys are inferred when blank.")
        dateKeySetting = date.input
        val money = configField("Money key override", "schema.money_key", settings.moneyKeyOverride, "Optional explicit money column. Common amount-like keys are inferred when blank.")
        moneyKeySetting = money.input
        val ticker = configField("Ticker/category key override", "schema.ticker_key", settings.tickerKeyOverride, "Optional category/ticker column used for coloring and finance grouping.")
        tickerKeySetting = ticker.input
        val tags = configField("Tags key override", "schema.tags_key", settings.tagsKeyOverride, "Optional tags column. Tags are edited as comma-separated values.")
        tagsKeySetting = tags.input
        val tickerColors = configField("Ticker color mapping", "display.ticker_colors", SettingsStore.colorsToText(settings.tickerColors), "One mapping per line, for example FD=#FFB300.", multiline = true)
        tickerColorsSetting = tickerColors.input
        val plotColumns = configField("Columns with plotting enabled", "stats.plot_columns", settings.plotColumns.joinToString(", "), "Comma-separated numeric JSON keys that receive built-in plots. Default: price.")
        plotColumnsSetting = plotColumns.input
        val financeColumns = configField("Columns reported as personal finance", "finance.columns", settings.financeColumns.joinToString(", "), "Comma-separated numeric JSON keys used for personal-finance reports. Default: price.")
        financeColumnsSetting = financeColumns.input

        val uiScale = configField("UI size multiplier", "display.ui_scale", settings.uiScale.toString(), "Scales margins, padding, control height, and other density-based UI dimensions. Recommended range: 0.70–1.60.")
        uiScaleSetting = uiScale.input.apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val textScale = configField("Text size multiplier", "display.text_scale", settings.textScale.toString(), "Scales text independently from the rest of the UI. Recommended range: 0.70–1.80.")
        textScaleSetting = textScale.input.apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }

        val primary = colorConfigField("Primary", "theme.primary", settings.palette.primary, "Active/focused state and highest-priority outline.")
        primarySetting = primary.input
        val secondary = colorConfigField("Secondary", "theme.secondary", settings.palette.secondary, "Inactive or lower-priority accent.")
        secondarySetting = secondary.input
        val tertiary = colorConfigField("Tertiary", "theme.tertiary", settings.palette.tertiary, "Main application background.")
        tertiarySetting = tertiary.input
        val quaternary = colorConfigField("Quaternary", "theme.quaternary", settings.palette.quaternary, "Secondary surfaces and plot grids.")
        quaternarySetting = quaternary.input
        val quinary = colorConfigField("Quinary", "theme.quinary", settings.palette.quinary, "Muted labels and placeholders.")
        quinarySetting = quinary.input
        val senary = colorConfigField("Senary", "theme.senary", settings.palette.senary, "Primary readable text.")
        senarySetting = senary.input

        val plotBackground = colorConfigField("Background", "plot.background", settings.plotTheme.background, "Plot canvas background. Defaults to AMOLED black.")
        plotBackgroundSetting = plotBackground.input
        val plotSurface = colorConfigField("Surface", "plot.surface", settings.plotTheme.surface, "Tooltip and secondary plot surface color.")
        plotSurfaceSetting = plotSurface.input
        val plotText = colorConfigField("Text", "plot.text", settings.plotTheme.text, "Main chart label and title color.")
        plotTextSetting = plotText.input
        val plotMuted = colorConfigField("Muted", "plot.muted", settings.plotTheme.muted, "Secondary labels and helper text.")
        plotMutedSetting = plotMuted.input
        val plotGrid = colorConfigField("Grid", "plot.grid", settings.plotTheme.grid, "Plot grid line color.")
        plotGridSetting = plotGrid.input
        val plotAxis = colorConfigField("Axis", "plot.axis", settings.plotTheme.axis, "Axes and tick color.")
        plotAxisSetting = plotAxis.input
        val plotPositive = colorConfigField("Positive", "plot.positive", settings.plotTheme.positive, "Rising box and positive-series color.")
        plotPositiveSetting = plotPositive.input
        val plotNegative = colorConfigField("Negative", "plot.negative", settings.plotTheme.negative, "Falling box and negative-series color.")
        plotNegativeSetting = plotNegative.input
        val plotObservation = colorConfigField("Observation", "plot.observation", settings.plotTheme.observation, "Actual observation rhombus and history path.")
        plotObservationSetting = plotObservation.input
        val plotOutlier = colorConfigField("Outlier", "plot.outlier", settings.plotTheme.outlier, "Tiny hollow outlier circle color.")
        plotOutlierSetting = plotOutlier.input
        val plotCenter = colorConfigField("Center lines", "plot.center", settings.plotTheme.center, "Median and dotted mean line color.")
        plotCenterSetting = plotCenter.input
        val plotAccent = colorConfigField("Accent", "plot.accent", settings.plotTheme.accent, "Accumulation, distribution, and custom-plot accent.")
        plotAccentSetting = plotAccent.input

        themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, ThemePreset.entries.map { it.displayName })
            backgroundTintList = inputTint()
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

        if (developerMode) {
            body.addView(accordion("GitHub", initiallyOpen = true, tooltip = "Repository connection, report target, data folder, default file, and encrypted GitHub PAT.") { container ->
                listOf(owner.wrapper, repo.wrapper, branch.wrapper, folder.wrapper, defaultFile.wrapper, reportRepo.wrapper, token.wrapper)
                    .forEach { container.addView(it, spacedMatchWidth(5)) }
                container.addView(styledButton("Clear stored PAT", accent = SECONDARY).apply {
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Clear GitHub PAT?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear") { _, _ ->
                                tokenStore.clear(); tokenSetting.text.clear(); tokenSetting.hint = "github.pat"; statusText.text = "Stored PAT cleared."
                            }.create().also { showDialog(it) }
                    }
                }, spacedMatchWidth(6))
            }, spacedMatchWidth(10))
        }

        body.addView(accordion("Color", tooltip = "Theme preset and six configurable semantic palette colors.") { container ->
            container.addView(infoText("Theme").apply {
                AppFonts.apply(this, bold = true, textScale = settings.textScale)
                tooltipController.attachHold(this, { "Switch among Default, Ayu, Ayu-Light, and Default light. Individual colors remain editable." })
            }, spacedMatchWidth(3))
            container.addView(themeSpinner, spacedMatchWidth(8))
            listOf(primary.wrapper, secondary.wrapper, tertiary.wrapper, quaternary.wrapper, quinary.wrapper, senary.wrapper)
                .forEach { container.addView(it, spacedMatchWidth(5)) }
        }, spacedMatchWidth(10))

        body.addView(accordion("Interface", tooltip = "Resize the full UI and text independently. Changes take effect after Save settings and reload.") { container ->
            listOf(uiScale.wrapper, textScale.wrapper).forEach { container.addView(it, spacedMatchWidth(5)) }
        }, spacedMatchWidth(10))

        body.addView(accordion("Plotting", tooltip = "Colors used by the D3.js and Observable Plot runtime. Plot background defaults to black and is independent from the application palette.") { container ->
            container.addView(infoText("Engine: pre-warmed WebView · D3.js for statistical boxes · Observable Plot for accumulation/distribution · Arquero for table operations.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
            val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            presets.addView(styledButton("Black default").apply {
                setOnClickListener { applyPlotThemeFields(PlotTheme.default()) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(3) })
            presets.addView(styledButton("Ayu plot").apply {
                setOnClickListener { applyPlotThemeFields(PlotTheme.ayu()) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3) })
            container.addView(presets, spacedMatchWidth(6))
            listOf(
                plotBackground.wrapper, plotSurface.wrapper, plotText.wrapper, plotMuted.wrapper,
                plotGrid.wrapper, plotAxis.wrapper, plotPositive.wrapper, plotNegative.wrapper,
                plotObservation.wrapper, plotOutlier.wrapper, plotCenter.wrapper, plotAccent.wrapper,
            ).forEach { container.addView(it, spacedMatchWidth(5)) }
        }, spacedMatchWidth(10))

        if (developerMode) {
            body.addView(accordion("Schema & Display", tooltip = "Schema overrides, category colors, plot-enabled columns, and finance-report columns.") { container ->
                listOf(array.wrapper, date.wrapper, money.wrapper, ticker.wrapper, tags.wrapper, tickerColors.wrapper, plotColumns.wrapper, financeColumns.wrapper)
                    .forEach { container.addView(it, spacedMatchWidth(5)) }
            }, spacedMatchWidth(10))

            customMetricList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BLACK) }
            renderCustomMetricSettings()
            body.addView(accordion("Custom metric", tooltip = "Create JavaScript metrics evaluated in the shared pre-warmed D3/Observable Plot/Arquero runtime.") { container ->
                container.addView(infoText("Available modules: d3, Plot, aq, context, theme, helpers, and jsonFile. The editor uses an Ayu syntax theme.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
                container.addView(customMetricList, matchWidth())
                container.addView(styledButton("+ Add custom metric").apply { setOnClickListener { editCustomMetric(null) } }, spacedMatchWidth(4))
            }, spacedMatchWidth(12))

            customPlotList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BLACK) }
            renderCustomPlotSettings()
            body.addView(accordion("Custom plot", tooltip = "Write a D3.js or Observable Plot script with Arquero available for dataframe handling.") { container ->
                container.addView(infoText("The script receives d3, Plot, aq, jsonFile, context, theme, and helpers. Return a DOM/SVG node or append to context.container.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
                container.addView(customPlotList, matchWidth())
                container.addView(styledButton("+ Add custom plot").apply { setOnClickListener { editCustomPlot(null) } }, spacedMatchWidth(4))
            }, spacedMatchWidth(12))
        } else {
            // Keep late-init properties valid while advanced sections are hidden.
            customMetricList = LinearLayout(this)
            customPlotList = LinearLayout(this)
        }

        body.addView(styledButton("Report").apply { setOnClickListener { showReportDialog() } }, spacedMatchWidth(6))
        body.addView(styledButton("Save settings and reload").apply { setOnClickListener { saveSettings() } }, spacedMatchWidth(6))
        body.addView(styledButton("Close Settings", accent = SECONDARY).apply { setOnClickListener { drawerRoot.closeDrawer() } }, spacedMatchWidth(6))

        return ScrollView(this).apply { setBackgroundColor(BLACK); addView(body, matchWidth()) }
    }

    private fun configField(
        title: String,
        key: String,
        value: String,
        description: String,
        password: Boolean = false,
        multiline: Boolean = false,
    ): ConfigField {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BLACK) }
        val label = TextView(this).apply {
            text = title; setTextColor(WHITE); textSize = 12.5f; setPadding(dp(4), dp(2), dp(4), dp(2)); AppFonts.apply(this, bold = true)
        }
        tooltipController.attachHold(label, { description })
        wrapper.addView(label, matchWidth())
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = styledInput(key).apply {
            setText(value)
            setOnLongClickListener { true }
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            if (multiline) { isSingleLine = false; minLines = 3; gravity = Gravity.TOP }
        }
        tooltipController.attachHold(input, { description })
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "ⓘ"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(PRIMARY); setPadding(dp(5), 0, dp(2), 0); AppFonts.apply(this, bold = true)
            setOnClickListener { tooltipController.show(this, description) }
        }, LinearLayout.LayoutParams(dp(34), dp(38)))
        wrapper.addView(row, matchWidth())
        return ConfigField(wrapper, input)
    }

    private fun colorConfigField(title: String, key: String, value: String, description: String): ConfigField {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BLACK) }
        val label = TextView(this).apply { text = title; setTextColor(WHITE); textSize = 12.5f; setPadding(dp(4), dp(2), dp(4), dp(2)); AppFonts.apply(this, bold = true) }
        tooltipController.attachHold(label, { description })
        wrapper.addView(label, matchWidth())
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = styledInput(key).apply { setText(value); setOnLongClickListener { true } }
        val swatch = TextView(this).apply { gravity = Gravity.CENTER; text = ""; setPadding(0, 0, 0, 0) }
        fun updateSwatch() {
            val color = try { Color.parseColor(input.text.toString().trim()) } catch (_: Exception) { SURFACE }
            swatch.background = GradientDrawable().apply { setColor(color); cornerRadius = dp(3).toFloat(); setStroke(1, MUTED) }
        }
        swatch.setOnClickListener { showColorPicker(input, swatch) }
        row.addView(swatch, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(7) })
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "ⓘ"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this, bold = true)
            setOnClickListener { tooltipController.show(this, description) }
        }, LinearLayout.LayoutParams(dp(34), dp(38)))
        tooltipController.attachHold(input, { description })
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSwatch()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateSwatch()
        wrapper.addView(row, matchWidth())
        return ConfigField(wrapper, input)
    }

    private fun showColorPicker(input: EditText, swatch: TextView) {
        val initial = try { Color.parseColor(input.text.toString().trim()) } catch (_: Exception) { PRIMARY }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(4)); setBackgroundColor(BLACK) }
        val preview = TextView(this).apply { minHeight = dp(32) }
        val channels = intArrayOf(Color.red(initial), Color.green(initial), Color.blue(initial))
        fun sync() {
            val c = Color.rgb(channels[0], channels[1], channels[2])
            preview.background = GradientDrawable().apply { setColor(c); cornerRadius = dp(3).toFloat() }
        }
        box.addView(preview, spacedMatchWidth(8))
        listOf("R", "G", "B").forEachIndexed { index, name ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(infoText(name).apply { setTextColor(WHITE) }, LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT))
            val seek = SeekBar(this).apply { max = 255; progress = channels[index] }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { channels[index] = progress; sync() }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            row.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(row, matchWidth())
        }
        sync()
        val dialog = AlertDialog.Builder(this).setTitle("Pick color").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Use") { _, _ ->
            val c = Color.rgb(channels[0], channels[1], channels[2])
            input.setText(String.format(Locale.US, "#%02X%02X%02X", channels[0], channels[1], channels[2]))
            swatch.background = GradientDrawable().apply { setColor(c); cornerRadius = dp(3).toFloat(); setStroke(1, MUTED) }
        }.create()
        showDialog(dialog)
    }

    private fun applyThemeFields(preset: ThemePreset) {
        val p = ThemePalette.preset(preset)
        primarySetting.setText(p.primary); secondarySetting.setText(p.secondary); tertiarySetting.setText(p.tertiary)
        quaternarySetting.setText(p.quaternary); quinarySetting.setText(p.quinary); senarySetting.setText(p.senary)
    }

    private fun applyPlotThemeFields(theme: PlotTheme) {
        plotBackgroundSetting.setText(theme.background)
        plotSurfaceSetting.setText(theme.surface)
        plotTextSetting.setText(theme.text)
        plotMutedSetting.setText(theme.muted)
        plotGridSetting.setText(theme.grid)
        plotAxisSetting.setText(theme.axis)
        plotPositiveSetting.setText(theme.positive)
        plotNegativeSetting.setText(theme.negative)
        plotObservationSetting.setText(theme.observation)
        plotOutlierSetting.setText(theme.outlier)
        plotCenterSetting.setText(theme.center)
        plotAccentSetting.setText(theme.accent)
    }

    private fun renderCustomMetricSettings() {
        if (!::customMetricList.isInitialized) return
        customMetricList.removeAllViews()

        customMetricList.addView(accordion("Built-in metric examples", initiallyOpen = false) { examples ->
            examples.addView(infoText("Templates are disabled until copied. Each receives only jsonFile { name, content }.").apply {
                setTextColor(MUTED)
            }, spacedMatchWidth(5))
            BuiltinExamples.customMetrics.forEach { example ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(1), 0, dp(1))
                }
                row.addView(infoText(example.name).apply { setTextColor(WHITE) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = "Use"
                    gravity = Gravity.CENTER
                    setTextColor(PRIMARY)
                    AppFonts.apply(this, textScale = settings.textScale)
                    setOnClickListener { editCustomMetric(null, example) }
                }, LinearLayout.LayoutParams(dp(52), dp(30)))
                examples.addView(row, matchWidth())
            }
        }, spacedMatchWidth(5))

        customMetricList.addView(infoText("Your custom metrics").apply {
            setTextColor(PRIMARY)
            AppFonts.apply(this, bold = true)
        }, spacedMatchWidth(3))
        if (customMetricsDraft.isEmpty()) customMetricList.addView(infoText("No custom metrics configured.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
        customMetricsDraft.forEach { metric ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            row.addView(infoText(metric.name).apply { setTextColor(if (metric.enabled) WHITE else MUTED) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply { text = "Edit"; setTextColor(PRIMARY); gravity = Gravity.CENTER; AppFonts.apply(this); setOnClickListener { editCustomMetric(metric) } }, LinearLayout.LayoutParams(dp(50), dp(32)))
            row.addView(TextView(this).apply { text = "×"; setTextColor(RED); gravity = Gravity.CENTER; AppFonts.apply(this, bold = true); setOnClickListener { customMetricsDraft.removeAll { it.id == metric.id }; renderCustomMetricSettings() } }, LinearLayout.LayoutParams(dp(36), dp(32)))
            customMetricList.addView(row, matchWidth())
        }
    }

    private fun editCustomMetric(existing: CustomMetricDefinition?, template: CustomMetricDefinition? = null) {
        val source = existing ?: template
        val name = styledInput("custom_metric.name").apply {
            setText(source?.name?.removePrefix("Example · ").orEmpty())
        }
        val script = JavaScriptCodeEditor(this).apply {
            hint = "custom_metric.javascript"
            setText(source?.script ?: "const rows = JSON.parse(jsonFile.content);\nreturn rows.length;")
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("Available modules: d3, Plot, aq, theme, helpers, context, and jsonFile { name, content }. Parse the effective JSON with JSON.parse(jsonFile.content). Scripts return synchronously.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
            addView(name, spacedMatchWidth(6))
            addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)))
        }
        val title = when {
            existing != null -> "Edit custom metric"
            template != null -> "Use example metric"
            else -> "New custom metric"
        }
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setView(body).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val code = script.text.toString().trim()
                if (n.isBlank()) { name.error = "Name is required"; return@setOnClickListener }
                if (code.isBlank()) { script.error = "JavaScript is required"; return@setOnClickListener }
                val next = CustomMetricDefinition(existing?.id ?: UUID.randomUUID().toString(), n, code, existing?.enabled ?: true)
                customMetricsDraft = if (existing == null) (customMetricsDraft + next).toMutableList()
                    else customMetricsDraft.map { if (it.id == existing.id) next else it }.toMutableList()
                renderCustomMetricSettings(); dialog.dismiss()
            }
        }
        showDialog(dialog)
    }

    private fun renderCustomPlotSettings() {
        if (!::customPlotList.isInitialized) return
        customPlotList.removeAllViews()
        customPlotList.addView(accordion("Built-in plot examples", initiallyOpen = false) { examples ->
            examples.addView(infoText("The first three templates use Observable Plot; the final two use D3.js directly. Arquero is available in every script.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
            BuiltinExamples.customPlots.forEach { example ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(1), 0, dp(1)) }
                row.addView(infoText("${example.name}\n${example.engine}").apply { setTextColor(WHITE); textSize = 12f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = "Use"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this)
                    setOnClickListener { editCustomPlot(null, example) }
                }, LinearLayout.LayoutParams(dp(52), dp(30)))
                examples.addView(row, matchWidth())
            }
        }, spacedMatchWidth(5))

        customPlotList.addView(infoText("Your custom plots").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(3))
        if (customPlotsDraft.isEmpty()) {
            customPlotList.addView(infoText("No custom plots configured.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
            return
        }
        customPlotsDraft.forEach { plot ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            row.addView(infoText("${plot.name}\n${plot.engine}").apply {
                setTextColor(if (plot.enabled) WHITE else MUTED); textSize = 12f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (plot.enabled) "ON" else "OFF"; setTextColor(if (plot.enabled) GREEN else MUTED); gravity = Gravity.CENTER; AppFonts.apply(this, bold = true)
                setOnClickListener {
                    customPlotsDraft = customPlotsDraft.map { if (it.id == plot.id) it.copy(enabled = !it.enabled) else it }.toMutableList()
                    renderCustomPlotSettings()
                }
            }, LinearLayout.LayoutParams(dp(44), dp(30)))
            row.addView(TextView(this).apply { text = "Edit"; setTextColor(PRIMARY); gravity = Gravity.CENTER; AppFonts.apply(this); setOnClickListener { editCustomPlot(plot) } }, LinearLayout.LayoutParams(dp(50), dp(30)))
            row.addView(TextView(this).apply {
                text = "×"; setTextColor(RED); gravity = Gravity.CENTER; AppFonts.apply(this, bold = true)
                setOnClickListener { customPlotsDraft.removeAll { it.id == plot.id }; renderCustomPlotSettings() }
            }, LinearLayout.LayoutParams(dp(34), dp(30)))
            customPlotList.addView(row, matchWidth())
        }
    }

    private fun editCustomPlot(existing: CustomPlotDefinition?, template: CustomPlotDefinition? = null) {
        val source = existing ?: template
        val name = styledInput("custom_plot.name").apply { setText(source?.name?.removePrefix("Example · ").orEmpty()) }
        val engineNames = listOf("Auto", "Observable Plot", "D3.js")
        val engineValues = listOf("auto", "observable", "d3")
        val engine = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, engineNames)
            backgroundTintList = inputTint()
            setSelection(engineValues.indexOf(source?.engine ?: "auto").coerceAtLeast(0))
        }
        val script = JavaScriptCodeEditor(this).apply {
            hint = "custom_plot.javascript"
            setText(source?.script ?: """const rows = helpers.rows(jsonFile);
const moneyKey = helpers.inferKey(rows, ['price', 'amount', 'cost', 'expense', 'value', 'total', 'money']);
const dateKey = helpers.inferKey(rows, ['date', 'datetime', 'timestamp', 'time', 'created_at']);
if (!moneyKey) throw new Error('No numeric/money column detected');
const points = rows.map((row, index) => ({
  x: dateKey ? helpers.parseDate(row[dateKey]) : index,
  y: helpers.number(row[moneyKey])
})).filter(point => (point.x instanceof Date ? !Number.isNaN(+point.x) : Number.isFinite(point.x)) && Number.isFinite(point.y));
return Plot.plot({
  width: context.width,
  height: context.height,
  style: helpers.plotStyle(theme),
  x: {grid: true},
  y: {grid: true},
  marks: [
    Plot.lineY(points, {x: 'x', y: 'y', stroke: theme.accent}),
    Plot.dot(points, {x: 'x', y: 'y', fill: theme.observation, tip: true})
  ]
});""")
        }
        var enabled = source?.enabled ?: true
        val enabledButton = styledButton(if (enabled) "Enabled" else "Disabled").apply {
            setOnClickListener { enabled = !enabled; text = if (enabled) "Enabled" else "Disabled"; setTextColor(if (enabled) GREEN else MUTED) }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("Available: d3, Plot, aq, jsonFile, context.container, context.width/height, theme, and helpers. Return an SVG/HTMLElement or append directly to context.container. Scripts return synchronously.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(name, spacedMatchWidth(5))
            addView(engine, spacedMatchWidth(5))
            addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(390)))
            addView(enabledButton, spacedMatchWidth(5))
        }
        val title = when {
            existing != null -> "Edit custom plot"
            template != null -> "Use example plot"
            else -> "New custom plot"
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(body).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val code = script.text.toString().trim()
                if (n.isBlank()) { name.error = "Name is required"; return@setOnClickListener }
                if (code.isBlank()) { script.error = "JavaScript is required"; return@setOnClickListener }
                val next = CustomPlotDefinition(existing?.id ?: UUID.randomUUID().toString(), n, code, engineValues[engine.selectedItemPosition], enabled)
                customPlotsDraft = if (existing == null) (customPlotsDraft + next).toMutableList()
                    else customPlotsDraft.map { if (it.id == existing.id) next else it }.toMutableList()
                renderCustomPlotSettings(); dialog.dismiss()
            }
        }
        showDialog(dialog)
    }

    private fun saveSettings() {
        val paletteInputs = listOf(
            primarySetting to "Primary", secondarySetting to "Secondary", tertiarySetting to "Tertiary",
            quaternarySetting to "Quaternary", quinarySetting to "Quinary", senarySetting to "Senary",
        )
        var invalid = false
        paletteInputs.forEach { (input, label) ->
            val value = input.text.toString().trim()
            if (!ThemePalette.isValidHex(value)) {
                input.error = "$label must be #RRGGBB or #AARRGGBB"
                invalid = true
            }
        }

        val plotThemeInputs = listOf(
            plotBackgroundSetting to "Plot background", plotSurfaceSetting to "Plot surface",
            plotTextSetting to "Plot text", plotMutedSetting to "Plot muted", plotGridSetting to "Plot grid",
            plotAxisSetting to "Plot axis", plotPositiveSetting to "Plot positive", plotNegativeSetting to "Plot negative",
            plotObservationSetting to "Plot observation", plotOutlierSetting to "Plot outlier",
            plotCenterSetting to "Plot center", plotAccentSetting to "Plot accent",
        )
        plotThemeInputs.forEach { (input, label) ->
            if (!PlotTheme.isValid(input.text.toString().trim())) {
                input.error = "$label must be #RRGGBB or #AARRGGBB"
                invalid = true
            }
        }
        val uiScale = uiScaleSetting.text.toString().trim().toDoubleOrNull()
        val textScale = textScaleSetting.text.toString().trim().toDoubleOrNull()
        if (uiScale == null || uiScale !in 0.70..1.60) {
            uiScaleSetting.error = "Use a value from 0.70 to 1.60"
            invalid = true
        }
        if (textScale == null || textScale !in 0.70..1.80) {
            textScaleSetting.error = "Use a value from 0.70 to 1.80"
            invalid = true
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
            plotColumns = SettingsStore.parseColumnList(plotColumnsSetting.text.toString()),
            financeColumns = SettingsStore.parseColumnList(financeColumnsSetting.text.toString()),
            customMetrics = customMetricsDraft.toList(),
            customPlots = customPlotsDraft.toList(),
            reportRepo = reportRepoSetting.text.toString().trim().ifBlank { "Exvia" },
            uiScale = uiScale!!,
            textScale = textScale!!,
            themePreset = selectedTheme,
            palette = ThemePalette(
                primarySetting.text.toString().trim(), secondarySetting.text.toString().trim(), tertiarySetting.text.toString().trim(),
                quaternarySetting.text.toString().trim(), quinarySetting.text.toString().trim(), senarySetting.text.toString().trim(),
            ),
            plotTheme = PlotTheme(
                background = plotBackgroundSetting.text.toString().trim(),
                surface = plotSurfaceSetting.text.toString().trim(),
                text = plotTextSetting.text.toString().trim(),
                muted = plotMutedSetting.text.toString().trim(),
                grid = plotGridSetting.text.toString().trim(),
                axis = plotAxisSetting.text.toString().trim(),
                positive = plotPositiveSetting.text.toString().trim(),
                negative = plotNegativeSetting.text.toString().trim(),
                observation = plotObservationSetting.text.toString().trim(),
                outlier = plotOutlierSetting.text.toString().trim(),
                center = plotCenterSetting.text.toString().trim(),
                accent = plotAccentSetting.text.toString().trim(),
            ),
        )
        settingsStore.save(next)
        val enteredToken = tokenSetting.text.toString().trim()
        if (enteredToken.isNotBlank()) tokenStore.save(enteredToken)
        val token = tokenStore.load()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_SELECTED_PATH).apply()

        if (token == null || !next.isConfigured()) {
            Toast.makeText(this, "Settings saved locally. GitHub config sync needs a PAT and configured repository.", Toast.LENGTH_LONG).show()
            recreate()
            return
        }

        setBusy(true, "Saving settings and syncing ${GitHubApi.CONFIG_PATH}…")
        val configJson = SettingsStore.settingsToConfigJson(next, filterSnippets, developerMode)
        executor.execute {
            try {
                GitHubApi(token, next).upsertTextFile(
                    GitHubApi.CONFIG_PATH,
                    configJson,
                    "Update Exvia configuration",
                )
                runOnUiThread {
                    statusText.text = "Settings saved and synchronized. Reloading…"
                    recreate()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    handleError(e, "Settings saved locally, but config sync failed")
                    AlertDialog.Builder(this)
                        .setTitle("Configuration sync failed")
                        .setMessage("Your settings were saved on this device, but ${GitHubApi.CONFIG_PATH} could not be pushed. Check the PAT and repository permissions, then press Save settings and reload again.")
                        .setNegativeButton("Close", null)
                        .create().also { showDialog(it) }
                }
            }
        }
    }

    private fun showReportDialog() {
        val token = tokenStore.load()
        if (token == null) {
            AlertDialog.Builder(this)
                .setTitle("GitHub PAT required")
                .setMessage("A GitHub PAT with Issues: write permission is required to submit a report.")
                .setNegativeButton("Close", null)
                .create().also { showDialog(it) }
            return
        }
        val categories = listOf("Bug", "Enhancement", "Feature")
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            backgroundTintList = inputTint()
        }
        val reportInput = styledInput("report.description").apply {
            isSingleLine = false
            minLines = 6
            gravity = Gravity.TOP
            hint = "Describe the problem or requested change"
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(BLACK)
            addView(infoText("Reports are created as GitHub Issues in ${settings.owner}/${settings.reportRepo}.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(typeSpinner, spacedMatchWidth(6))
            addView(reportInput, matchWidth())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Report")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val description = reportInput.text.toString().trim()
                if (description.isBlank()) {
                    reportInput.error = "Describe the report"
                    return@setOnClickListener
                }
                val prefix = when (typeSpinner.selectedItemPosition) {
                    0 -> "bug:"
                    1 -> "ench:"
                    else -> "feat:"
                }
                val summary = description.lineSequence().firstOrNull { it.isNotBlank() }
                    ?.trim()?.replace(Regex("\\s+"), " ")?.take(90)
                    .orEmpty().ifBlank { categories[typeSpinner.selectedItemPosition] }
                val issueTitle = "$prefix $summary"
                val issueBody = buildString {
                    append(description)
                    append("\n\n---\n")
                    append("Submitted from Exvia 1.11\n")
                    append("Classification: ${categories[typeSpinner.selectedItemPosition]}\n")
                    append("Data repository: ${settings.owner}/${settings.repo}\n")
                    append("Branch: ${settings.branch}\n")
                    append("Developer Options: ${if (developerMode) "enabled" else "hidden"}\n")
                }
                dialog.dismiss()
                setBusy(true, "Creating issue in ${settings.owner}/${settings.reportRepo}…")
                executor.execute {
                    try {
                        val url = GitHubApi(token, settings).createIssue(
                            settings.owner,
                            settings.reportRepo,
                            issueTitle,
                            issueBody,
                        )
                        runOnUiThread {
                            setBusy(false)
                            statusText.text = if (url.isBlank()) "Report submitted." else "Report submitted: $url"
                            Toast.makeText(this, "GitHub Issue created", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Report submission failed") }
                    }
                }
            }
        }
        showDialog(dialog)
    }

    private fun promptRepositoryInitialization() {
        val token = tokenStore.load() ?: return
        val dialog = AlertDialog.Builder(this)
            .setTitle("GitHub repository")
            .setMessage("Is your expense GitHub repository already initialized?")
            .setPositiveButton("Yes") { _, _ ->
                settingsStore.setRepoInitializationAsked(true)
                if (settings.isConfigured()) refreshFilesAndTable() else {
                    statusText.text = "Set the existing repository details in GitHub Settings."
                    drawerRoot.openDrawer()
                }
            }
            .setNegativeButton("No") { _, _ -> showRepositoryCreationDialog(token) }
            .create()
        showDialog(dialog)
    }

    private fun showRepositoryCreationDialog(token: String) {
        val username = styledInput("github.username").apply { setText(settings.owner.takeUnless { it.startsWith("YOUR_") }.orEmpty()) }
        val repo = styledInput("github.repo").apply { setText(settings.repo.takeUnless { it.startsWith("YOUR_") }.orEmpty()) }
        val branch = styledInput("github.branch").apply { setText(settings.branch.ifBlank { "main" }) }
        val folder = styledInput("github.folder").apply { setText(settings.folder.ifBlank { "Financial" }) }
        val defaultFile = styledInput("github.default_file").apply { setText(settings.defaultJson.ifBlank { YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".json" }) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(BLACK)
            addView(infoText("A private repository will be created for the account represented by this PAT, then the branch and empty JSON file will be initialized.").apply { setTextColor(MUTED) }, spacedMatchWidth(8))
            listOf(username, repo, branch, folder, defaultFile).forEach { addView(it, spacedMatchWidth(5)) }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Initialize repository")
            .setView(body)
            .setNegativeButton("Cancel") { _, _ -> drawerRoot.openDrawer() }
            .setPositiveButton("Create", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = username.text.toString().trim()
                val r = repo.text.toString().trim()
                val b = branch.text.toString().trim().ifBlank { "main" }
                val f = folder.text.toString().trim().trim('/').ifBlank { "Financial" }
                var d = defaultFile.text.toString().trim().ifBlank { "expenses.json" }
                if (!d.endsWith(".json", true)) d += ".json"
                if (u.isBlank()) { username.error = "Username is required"; return@setOnClickListener }
                if (r.isBlank()) { repo.error = "Repository is required"; return@setOnClickListener }
                dialog.dismiss()
                setBusy(true, "Creating private GitHub repository…")
                executor.execute {
                    try {
                        GitHubApi(token, settings).createAndInitializeRepository(u, r, b, f, d)
                        val next = settings.copy(owner = u, repo = r, branch = b, folder = f, defaultJson = d)
                        settingsStore.save(next)
                        settingsStore.setRepoInitializationAsked(true)
                        runOnUiThread {
                            statusText.text = "Repository initialized. Reloading…"
                            recreate()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            setBusy(false)
                            handleError(e, "Repository creation failed")
                            showRepositoryCreationDialog(token)
                        }
                    }
                }
            }
        }
        showDialog(dialog)
    }

    private fun loadCachedOrRefresh() {
        val token = requireToken() ?: return
        val cachedFiles = fileCache.loadFiles(settings)
        if (cachedFiles == null) {
            refreshFilesAndTable()
            return
        }
        val savedPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SELECTED_PATH, null)
        val defaultPath = settings.pathFor(settings.defaultJson)
        val selected = cachedFiles.firstOrNull { it.path == savedPath }
            ?: cachedFiles.firstOrNull { it.path == defaultPath }
            ?: cachedFiles.firstOrNull()
        val cachedFile = selected?.let { fileCache.loadFile(settings, it.path) }
        val cacheMatchesIndex = selected == null || cachedFile == null || selected.sha.isBlank() || cachedFile.sha == selected.sha
        if (selected != null && (cachedFile == null || !cacheMatchesIndex)) {
            files = cachedFiles
            setSelectedPath(selected.path)
            renderFiles()
            refreshSelected(forceNetwork = true)
            return
        }
        val data = cachedFile?.let { GitHubApi(token, settings).parseCachedTable(it.text) }
            ?: TableData(emptyList(), emptyList(), null, null, null, null)
        files = cachedFiles
        setSelectedPath(selected?.path)
        renderFiles()
        applyTableData(data)
        statusText.text = when {
            selected == null -> "Loaded cached file list. No JSON files are available."
            else -> "Loaded ${data.rows.size} row(s) from local cache: ${selected.name}."
        }
        setBusy(false)
    }

    private fun refreshFilesAndTable() {
        val token = requireToken() ?: return
        setBusy(true, "Re-syncing ${settings.folder.ifBlank { "/" }}/ with GitHub…")
        executor.execute {
            try {
                val api = GitHubApi(token, settings)
                val loadedFiles = api.listExpenseFiles()
                val savedPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SELECTED_PATH, null)
                val defaultPath = settings.pathFor(settings.defaultJson)
                val selected = loadedFiles.firstOrNull { it.path == savedPath }
                    ?: loadedFiles.firstOrNull { it.path == defaultPath }
                    ?: loadedFiles.firstOrNull()
                val fetched = selected?.let { api.fetchTableFile(it.path) }
                fileCache.saveFiles(settings, loadedFiles)
                fetched?.first?.let { fileCache.saveFile(settings, it) }
                val data = fetched?.second ?: TableData(emptyList(), emptyList(), null, null, null, null)
                runOnUiThread {
                    files = loadedFiles
                    setSelectedPath(selected?.path)
                    renderFiles()
                    applyTableData(data)
                    statusText.text = if (selected == null) {
                        "Re-sync complete. No .json files found in ${settings.folder}/."
                    } else {
                        "Re-synced ${data.rows.size} row(s) from ${selected.name}."
                    }
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val cachedFiles = fileCache.loadFiles(settings)
                    if (cachedFiles != null) {
                        files = cachedFiles
                        renderFiles()
                    }
                    handleError(e, "Automatic GitHub sync failed")
                }
            }
        }
    }

    private fun refreshSelected(successMessage: String? = null, forceNetwork: Boolean = false) {
        val path = selectedPath ?: return
        val token = requireToken() ?: return
        if (!forceNetwork) {
            fileCache.loadFile(settings, path)?.takeIf { cached ->
                val indexedSha = files.firstOrNull { it.path == path }?.sha.orEmpty()
                indexedSha.isBlank() || indexedSha == cached.sha
            }?.let { cached ->
                try {
                    val data = GitHubApi(token, settings).parseCachedTable(cached.text)
                    applyTableData(data)
                    statusText.text = successMessage ?: "Loaded ${data.rows.size} row(s) from local cache: ${path.substringAfterLast('/')}"
                    setBusy(false)
                    return
                } catch (_: Exception) {
                    fileCache.removeFile(settings, path)
                }
            }
        }
        setBusy(true, "Fetching ${path.substringAfterLast('/')} from GitHub…")
        executor.execute {
            try {
                val fetched = GitHubApi(token, settings).fetchTableFile(path)
                fileCache.saveFile(settings, fetched.first)
                files = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
                fileCache.saveFiles(settings, files)
                runOnUiThread {
                    applyTableData(fetched.second)
                    statusText.text = successMessage ?: "Fetched and cached ${fetched.second.rows.size} row(s) from ${path.substringAfterLast('/')}."
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
        if (::drawerRoot.isInitialized) AppFonts.applyToTree(drawerRoot, settings.textScale)
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

    private fun showFilterSnippetManager() {
        var managerDialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(BLACK)
        }
        list.addView(accordion("Built-in examples", initiallyOpen = false) { examples ->
            BuiltinExamples.filterSnippets.forEach { snippet ->
                examples.addView(TextView(this).apply {
                    text = "${snippet.name}\n${snippet.query}"
                    maxLines = 3
                    textSize = 11.5f
                    setTextColor(WHITE)
                    setPadding(dp(5), dp(4), dp(5), dp(4))
                    AppFonts.apply(this, textScale = settings.textScale)
                    setOnClickListener {
                        selectFilterSnippet(snippet)
                        managerDialog?.dismiss()
                    }
                }, spacedMatchWidth(3))
            }
        }, spacedMatchWidth(5))

        list.addView(infoText("Saved snippets").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true, textScale = settings.textScale) }, spacedMatchWidth(3))
        if (filterSnippets.isEmpty()) list.addView(infoText("No saved filtering snippets.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
        filterSnippets.forEach { snippet ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(2), 0, dp(2)) }
            val choose = TextView(this).apply {
                text = "${snippet.name}\n${snippet.query}"
                maxLines = 2
                textSize = 11.5f
                setTextColor(WHITE)
                setPadding(dp(5), dp(3), dp(5), dp(3))
                AppFonts.apply(this, textScale = settings.textScale)
                setOnClickListener { selectFilterSnippet(snippet); managerDialog?.dismiss() }
            }
            row.addView(choose, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this, textScale = settings.textScale)
                setOnClickListener { managerDialog?.dismiss(); editFilterSnippet(snippet) }
            }, LinearLayout.LayoutParams(dp(48), dp(34)))
            row.addView(TextView(this).apply {
                text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true, textScale = settings.textScale)
                setOnClickListener {
                    managerDialog?.dismiss()
                    filterSnippets.removeAll { it.id == snippet.id }
                    settingsStore.saveFilterSnippets(filterSnippets)
                    showFilterSnippetManager()
                }
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            list.addView(row, matchWidth())
        }
        list.addView(styledButton("+ New snippet").apply { setOnClickListener { managerDialog?.dismiss(); editFilterSnippet(null) } }, spacedMatchWidth(4))
        val scroll = ScrollView(this).apply { addView(list, matchWidth()) }
        managerDialog = AlertDialog.Builder(this).setTitle("Filtering snippets").setView(scroll).setNegativeButton("Close", null).create()
        showDialog(managerDialog!!)
    }

    /** Regular-mode selector: methods are named, but their SQL-like implementation remains hidden. */
    private fun showFilteringMethodManager() {
        var dialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(BLACK)
        }
        list.addView(infoText("Choose a filtering method. Developer Options expose and edit the underlying syntax.").apply {
            setTextColor(MUTED)
        }, spacedMatchWidth(6))

        list.addView(accordion("Built-in methods", initiallyOpen = false) { builtins ->
            BuiltinExamples.filterSnippets.forEach { snippet ->
                builtins.addView(TextView(this).apply {
                    text = snippet.name
                    setTextColor(WHITE)
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    AppFonts.apply(this, textScale = settings.textScale)
                    setOnClickListener { selectFilterSnippet(snippet); dialog?.dismiss() }
                }, matchWidth())
            }
        }, spacedMatchWidth(5))

        list.addView(accordion("Saved methods", initiallyOpen = true) { saved ->
            if (filterSnippets.isEmpty()) {
                saved.addView(infoText("No saved methods. Enable Developer Options to create one.").apply { setTextColor(MUTED) }, matchWidth())
            } else {
                filterSnippets.forEach { snippet ->
                    saved.addView(TextView(this).apply {
                        text = snippet.name
                        setTextColor(WHITE)
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        AppFonts.apply(this, textScale = settings.textScale)
                        setOnClickListener { selectFilterSnippet(snippet); dialog?.dismiss() }
                    }, matchWidth())
                }
            }
        }, spacedMatchWidth(5))

        val scroll = ScrollView(this).apply { addView(list, matchWidth()) }
        dialog = AlertDialog.Builder(this)
            .setTitle("Filtering method")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .create()
        showDialog(dialog!!)
    }

    private fun selectFilterSnippet(snippet: FilterSnippet) {
        filterQuery = snippet.query
        filterInput.setText(snippet.query)
        filteringMethodButton.text = snippet.name
        if (filterEnabled) applyFilterAndRender(showStatus = true)
    }

    private fun editFilterSnippet(existing: FilterSnippet?) {
        val name = styledInput("snippet.name").apply { setText(existing?.name.orEmpty()) }
        val query = styledInput("snippet.query").apply { isSingleLine = false; minLines = 4; gravity = Gravity.TOP; setText(existing?.query ?: "SELECT * WHERE ") }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0); addView(name, spacedMatchWidth(5)); addView(query, matchWidth()) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "New filtering snippet" else "Edit filtering snippet")
            .setView(body).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val q = query.text.toString().trim()
                if (n.isBlank()) { name.error = "Name is required"; return@setOnClickListener }
                if (q.isBlank()) { query.error = "Query is required"; return@setOnClickListener }
                val next = FilterSnippet(existing?.id ?: UUID.randomUUID().toString(), n, q)
                if (existing == null) filterSnippets += next else filterSnippets = filterSnippets.map { if (it.id == existing.id) next else it }.toMutableList()
                settingsStore.saveFilterSnippets(filterSnippets)
                dialog.dismiss(); showFilterSnippetManager()
            }
        }
        showDialog(dialog)
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
                        val fetched = api.fetchTableFile(path)
                        fileCache.saveFile(settings, fetched.first)
                        files = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
                        fileCache.saveFiles(settings, files)
                        runOnUiThread {
                            applyTableData(fetched.second)
                            statusText.text = "Committed and cached at $date."
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
                        val fetched = api.fetchTableFile(path)
                        fileCache.saveFile(settings, fetched.first)
                        files = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
                        fileCache.saveFiles(settings, files)
                        runOnUiThread { applyTableData(fetched.second); statusText.text = "Row updated and cache refreshed."; setBusy(false) }
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
                        val fetched = api.fetchTableFile(path)
                        fileCache.saveFile(settings, fetched.first)
                        files = files.map { if (it.path == path) it.copy(sha = fetched.first.sha) else it }
                        fileCache.saveFiles(settings, files)
                        runOnUiThread { applyTableData(fetched.second); statusText.text = "Row deleted and cache refreshed."; setBusy(false) }
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

        // One serialization is shared by every custom plot and custom metric in this render pass.
        val effectiveJson = effectiveJsonFile(data)

        val financeKeys = settings.resolvedFinanceColumns(data.keys)
        if (financeKeys.isNotEmpty()) {
            statContent.addView(accordion("Personal finance", tooltip = "Derived personal-finance metrics calculated from the currently visible dataset. Filtering therefore changes every value here.") { financeRoot ->
                financeKeys.forEach { moneyKey ->
                    val reportData = data.copy(moneyKey = moneyKey)
                    val finance = Statistics.financeStats(reportData) ?: return@forEach
                    if (financeKeys.size > 1) {
                        financeRoot.addView(accordion(moneyKey, tooltip = "Personal-finance report using '$moneyKey' as the money column.") { nestedContent ->
                            renderFinanceGroups(nestedContent, finance, reportData)
                        }, matchWidth())
                    } else {
                        renderFinanceGroups(financeRoot, finance, reportData)
                    }
                }
            }, matchWidth())
        }

        if (data.keys.isEmpty()) {
            statContent.addView(infoText("No inferred keys in this file."), matchWidth())
            return
        }
        val plottedKeys = settings.resolvedPlotColumns(data.keys).map { it.lowercase() }.toSet()
        data.keys.forEach { key ->
            val values = data.rows.map { it.values[key].orEmpty() }
            val stats = Statistics.keyStats(values)
            statContent.addView(accordion(key, tooltip = "Statistical summary for the '$key' column using the currently visible rows.") { container ->
                if (key.lowercase() in plottedKeys) {
                    container.addView(accordion("History", initiallyOpen = true, tooltip = "Cumulative timestamp-level boxplot for $key. Rows at identical datetimes are summed before each statistical snapshot.") { graphBox ->
                        val graph = buildGraph(data, key)
                        graph.view?.let { graphBox.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340))) }
                        graph.legend?.let { graphBox.addView(infoText(it).apply { setTextColor(MUTED) }, matchWidth()) }
                    }, matchWidth())
                    container.addView(accordion("Accumulation", tooltip = "Running sum of timestamp-level $key totals over time.") { plotBox ->
                        val accumulation = buildAccumulationPlot(data, key)
                        accumulation.view?.let { plotBox.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230))) }
                        accumulation.legend?.let { plotBox.addView(infoText(it).apply { setTextColor(MUTED) }, matchWidth()) }
                    }, matchWidth())
                    container.addView(accordion("Normal distribution", tooltip = "Normal probability-density curve fitted to the visible numeric $key values.") { plotBox ->
                        val normal = buildNormalPlot(data, key)
                        normal.view?.let { plotBox.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230))) }
                        normal.legend?.let { plotBox.addView(infoText(it).apply { setTextColor(MUTED) }, matchWidth()) }
                    }, matchWidth())
                } else {
                    container.addView(infoText("Plots disabled for this key. Configure stats.plot_columns in Settings to enable them.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
                }

                addStatisticMetric(container, "Mean", stats.mean, STAT_MEAN)
                addStatisticMetric(container, "Median", stats.median, STAT_MEDIAN)
                addStatisticMetric(container, "Mean − Median gap", stats.meanMedianGap, if ((stats.meanMedianGap ?: 0.0) < 0.0) RED else GREEN)
                val modeNumber = stats.mode?.let { Statistics.parseNumber(it) }
                addMetric(container, "Mode", stats.mode ?: "N/A", nameColor = STAT_MEDIAN, valueColor = when {
                    stats.mode == null -> MUTED
                    modeNumber != null && modeNumber < 0.0 -> RED
                    else -> STAT_MEDIAN
                }, tooltip = statTooltip("Mode"))
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
                addMetric(container, "n", stats.n.toString(), nameColor = MUTED, valueColor = WHITE, tooltip = statTooltip("n"))
                addMetric(container, "n unique", stats.nUnique.toString(), nameColor = MUTED, valueColor = WHITE, tooltip = statTooltip("n unique"))
                addStatisticMetric(container, "Variance", stats.variance, STAT_SPREAD)
                if (stats.numericN != stats.n) addMetric(container, "Numeric n", stats.numericN.toString(), nameColor = MUTED, valueColor = WHITE, tooltip = "Number of non-empty values that could be parsed as numbers.")
            }, matchWidth())
        }

        val enabledCustomPlots = customPlotsDraft.filter { it.enabled }
        if (enabledCustomPlots.isNotEmpty()) {
            statContent.addView(accordion("Custom plots", tooltip = "JavaScript plots evaluated locally with D3.js, Observable Plot, and Arquero already initialized.") { customRoot ->
                enabledCustomPlots.forEach { definition ->
                    customRoot.addView(accordion(definition.name, tooltip = "${definition.engine} script using the current visible/filtered JSON file.") { plotRoot ->
                        val payload = JSONObject().apply {
                            put("kind", "custom")
                            put("engine", definition.engine)
                            put("script", definition.script)
                            put("height", 340)
                            put("theme", settings.plotTheme.toJson())
                            put("jsonFile", effectiveJson)
                        }
                        val plot = WebPlotView(this@MainActivity).apply { bind(plotRuntime, payload) }
                        plotRoot.addView(plot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)))
                        plotRoot.addView(infoText("Runtime: ${definition.engine} · d3, Plot, aq, jsonFile, context, theme, and helpers are available. Filtering changes jsonFile.content.").apply { setTextColor(MUTED) }, matchWidth())
                    }, matchWidth())
                }
            }, matchWidth())
        }

        val enabledCustom = customMetricsDraft.filter { it.enabled }
        if (enabledCustom.isNotEmpty()) {
            statContent.addView(accordion("Custom", tooltip = "User-defined JavaScript metrics evaluated locally against the currently visible/filtered JSON rows through jsonFile.content.") { container ->
                enabledCustom.forEach { metric ->
                    val result = TextView(this).apply { text = "Evaluating…"; setTextColor(MUTED); gravity = Gravity.END; AppFonts.apply(this) }
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3)) }
                    val name = infoText(metric.name).apply { setTextColor(STAT_SHAPE); tooltipController.attachHold(this, { "Custom JavaScript metric evaluated from jsonFile.content. Edit it under Settings → Custom metric to inspect the script." }) }
                    row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(result, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    container.addView(row, matchWidth())
                    customMetricEngine.evaluate(metric, effectiveJson) { evaluated ->
                        runOnUiThread {
                            evaluated.fold(
                                onSuccess = { result.text = it; result.setTextColor(WHITE) },
                                onFailure = { result.text = "Error: ${it.message ?: "evaluation failed"}"; result.setTextColor(RED) },
                            )
                        }
                    }
                }
            }, matchWidth())
        }
    }

    private fun renderFinanceGroups(container: LinearLayout, finance: Statistics.FinanceStats, data: TableData) {
        fun amount(value: Double?) = value?.let(::fmt) ?: "N/A"
        fun pct(value: Double?) = value?.let { "${fmt(it)}%" } ?: "N/A"
        fun days(value: Double?) = value?.let { "${fmt(it)} days" } ?: "N/A"
        fun months(value: Double?) = value?.let { "${fmt(it)} months" } ?: "N/A"
        fun meanTemplate(mean: Double?, stdv: Double?, median: Double?): String = if (mean != null && stdv != null && median != null) "${fmt(mean)} ± ${fmt(stdv)} (${fmt(median)})" else "N/A"

        container.addView(accordion("Core", initiallyOpen = true, tooltip = "Highest-priority cash-flow and financial-health ratios.") { c ->
            addFinancialMetric(c, "Net Cash Flow", amount(finance.netCashFlow), "Income minus expenses. Positive means the visible period generated surplus cash.", signed = finance.netCashFlow)
            addFinancialMetric(c, "Savings Rate", pct(finance.savingsRate), "Net cash flow as a percentage of income. Higher positive values indicate more income retained.", signed = finance.savingsRate)
            addFinancialMetric(c, "Expense Ratio", pct(finance.expenseRatio), "Expenses divided by income. Values above 100% mean spending exceeded income.", signed = finance.expenseRatio?.let { 100.0 - it })
            addFinancialMetric(c, "Emergency Fund", months(finance.emergencyFundMonths), "Estimated months of average spending covered by the observed positive surplus. This is not an account balance because Exvia does not know assets outside the selected rows.")
            addFinancialMetric(c, "Debt-To-Income ratio", pct(finance.debtToIncomeRatio), "Heuristic debt burden: expenses whose row text mentions debt, loan, credit, or mortgage divided by income.")
            addFinancialMetric(c, "Total Income", amount(finance.totalIncome), "Total value of rows whose money value begins with + in the visible dataset.", signed = finance.totalIncome)
            addFinancialMetric(c, "Total Expenses", amount(finance.totalExpenses), "Total absolute value of non-+ money rows in the visible dataset.")
        }, matchWidth())

        container.addView(accordion("Expense", tooltip = "Spending size, frequency, growth, volatility, recurrence, and subscription load.") { c ->
            addFinancialMetric(c, "Average and Median Expense", meanTemplate(finance.averageExpense, finance.expenseStdv, finance.medianExpense), "Mean ± population standard deviation of expenses, with median in parentheses.")
            addFinancialMetric(c, "Largest Expense", amount(finance.largestExpense), "Largest single expense amount in the visible dataset.")
            addFinancialMetric(c, "Expense Frequency per day", finance.expenseFrequencyPerDay?.let(::fmt) ?: "N/A", "Expense transaction count divided by the number of calendar days covered by the visible dated data.")
            addFinancialMetric(c, "Expense Growth Rate", pct(finance.expenseGrowthRate), "Percentage change from the first dated expense amount to the last dated expense amount.", signed = finance.expenseGrowthRate?.let { -it })
            addFinancialMetric(c, "Expense Volatility", amount(finance.expenseVolatility), "Population standard deviation of individual expense amounts.")
            addFinancialMetric(c, "Recurring Expense Ratio", pct(finance.recurringExpenseRatio), "Share of expense value whose row text looks recurring, such as recurring, subscription, rent, mortgage, utility, or monthly.")
            addFinancialMetric(c, "Subscription Burden", pct(finance.subscriptionBurden), "Subscription-like spending divided by total income.")
        }, matchWidth())

        container.addView(accordion("Income", tooltip = "Income level, stability, growth, diversity, recurrence, and timing.") { c ->
            addFinancialMetric(c, "Average and Median Income", meanTemplate(finance.averageIncome, finance.incomeStdv, finance.medianIncome), "Mean ± population standard deviation of income, with median in parentheses.")
            addFinancialMetric(c, "Income stability score", finance.incomeStabilityScore?.let { "${fmt(it)}/100" } ?: "N/A", "100 / (1 + coefficient of variation). More consistent income approaches 100.")
            addFinancialMetric(c, "Income growth rate", pct(finance.incomeGrowthRate), "Percentage change from the first dated income amount to the last dated income amount.", signed = finance.incomeGrowthRate)
            addFinancialMetric(c, "Income diversity", "${finance.incomeDiversity} source(s)", "Number of distinct ticker/category sources that contributed income.")
            addFinancialMetric(c, "Largest Income Source", finance.largestIncomeSource?.let { "${it.first}: ${fmt(it.second)}" } ?: "N/A", "Ticker/category source contributing the largest total income.")
            addFinancialMetric(c, "Recurring Income Ratio", pct(finance.recurringIncomeRatio), "Share of income whose row text appears recurring, including salary/payroll and recurring markers.")
            addFinancialMetric(c, "Bonus Income Ratio", pct(finance.bonusIncomeRatio), "Share of income whose row text contains bonus, windfall, gift, or reward markers.")
            addFinancialMetric(c, "Average Time Between Income", days(finance.averageTimeBetweenIncomeDays), "Mean number of days between consecutive dated income transactions.")
            addFinancialMetric(c, "Largest Income", amount(finance.largestIncome), "Largest single income amount in the visible dataset.")
        }, matchWidth())

        container.addView(accordion("Behavior", tooltip = "Behavioral patterns and transaction cadence from the visible date range.") { c ->
            addFinancialMetric(c, "No-Spend Day Ratio", pct(finance.noSpendDayRatio), "Percentage of calendar days in the visible period with no expense transaction.")
            addFinancialMetric(c, "Transaction count", finance.transactionCount.toString(), "Number of numeric money rows in the current report.")
            addFinancialMetric(c, "Period", listOfNotNull(finance.firstDate, finance.lastDate).joinToString(" → ").ifBlank { "N/A" }, "First to last parseable datetime in the visible data.")
            if (finance.categorySpending.isNotEmpty()) {
                c.addView(infoText("Spending by ${data.tickerKey ?: "category"}").apply { setTextColor(MUTED); AppFonts.apply(this, bold = true); tooltipController.attachHold(this, { "Largest expense categories/tickers by total amount." }) }, spacedMatchWidth(3))
                finance.categorySpending.forEach { (name, value) -> addFinancialMetric(c, name, fmt(value), "Total visible expense assigned to this category/ticker.") }
            }
        }, matchWidth())

        container.addView(accordion("Liquidity", tooltip = "Observed cash burn, reserve coverage, reconstructed balance, and simple runway estimate.") { c ->
            addFinancialMetric(c, "Cash Burn Rate", finance.cashBurnRate?.let { "${fmt(it)}/day" } ?: "N/A", "Average daily expenses minus average daily income over the visible period. Positive means cash is being consumed.", signed = finance.cashBurnRate?.let { -it })
            addFinancialMetric(c, "Cash Reserve Days", days(finance.cashReserveDays), "Observed positive net cash flow divided by average daily expenses. It is a surplus-coverage estimate, not your actual bank balance.")
            addFinancialMetric(c, "Average Daily Balance", amount(finance.averageDailyBalance), "Average reconstructed daily closing balance when the selected period starts at zero and applies visible income/expenses chronologically.", signed = finance.averageDailyBalance)
            addFinancialMetric(c, "Days Until Cash Runs Out", days(finance.daysUntilCashRunsOut), "Simple forecast: reconstructed ending balance divided by positive daily cash burn. N/A when there is no positive balance or no current burn.")
        }, matchWidth())
    }

    private fun addFinancialMetric(container: LinearLayout, name: String, value: String, tooltip: String, signed: Double? = null) {
        val color = when {
            signed == null -> WHITE
            signed < 0.0 -> RED
            signed > 0.0 -> GREEN
            else -> MUTED
        }
        addMetric(container, name, value, nameColor = MUTED, valueColor = color, tooltip = tooltip)
    }

    private fun statTooltip(name: String): String = when (name) {
        "Mean" -> "Arithmetic average of numeric values."
        "Median", "Q2" -> "50th percentile: half the numeric observations are below and half above."
        "Mean − Median gap" -> "Signed difference between mean and median; useful as a compact indication of asymmetry."
        "Mode" -> "Most frequently occurring non-empty value."
        "Sum" -> "Total of all numeric values."
        "STDV" -> "Population standard deviation: typical spread around the mean."
        "Minimum" -> "Smallest numeric observation."
        "Maximum" -> "Largest numeric observation."
        "Range" -> "Maximum minus minimum."
        "Q1" -> "25th percentile of numeric values."
        "Q3" -> "75th percentile of numeric values."
        "IQR" -> "Interquartile range: Q3 minus Q1, describing the middle 50% spread."
        "Skew" -> "Standardized third moment; sign indicates the direction of distribution asymmetry."
        "Kurtosis" -> "Excess kurtosis from the standardized fourth moment; describes tail/peak heaviness relative to a normal distribution."
        "n" -> "Count of non-empty values."
        "n unique" -> "Count of distinct non-empty values."
        "Variance" -> "Population variance: mean squared deviation from the mean."
        else -> "Statistic calculated from the currently visible rows."
    }

    private data class WebPlotResult(val view: WebPlotView?, val legend: String?)

    private fun effectiveJsonFile(data: TableData): JSONObject {
        val rows = JSONArray().apply {
            data.rows.forEach { row ->
                put(runCatching { JSONObject(row.originalJson) }.getOrElse {
                    JSONObject().apply { row.values.forEach { (key, value) -> put(key, value) } }
                })
            }
        }
        return JSONObject().apply {
            put("name", selectedPath?.substringAfterLast('/') ?: "current.json")
            put("content", rows.toString())
        }
    }

    private fun datedNumericPayload(data: TableData, key: String): Triple<JSONArray, Int, Int> {
        val dateKey = data.dateKey ?: return Triple(JSONArray(), data.rows.size, 0)
        var missingDates = 0
        var nonNumeric = 0
        val values = data.rows.mapNotNull { row ->
            val rawDate = row.values[dateKey].orEmpty()
            val x = Statistics.parseDate(rawDate)
            if (x == null) { missingDates += 1; return@mapNotNull null }
            val y = Statistics.parseNumber(row.values[key].orEmpty())
            if (y == null) { nonNumeric += 1; return@mapNotNull null }
            Triple(x, y, rawDate)
        }.sortedBy { it.first }
        return Triple(JSONArray().apply {
            values.forEach { (x, y, label) -> put(JSONObject().apply { put("x", x); put("y", y); put("label", label) }) }
        }, missingDates, nonNumeric)
    }

    private fun webPlot(payload: JSONObject): WebPlotView = WebPlotView(this).apply { bind(plotRuntime, payload) }

    private fun buildGraph(data: TableData, key: String): WebPlotResult {
        val dateKey = data.dateKey ?: return WebPlotResult(null, "No date key detected; set a Date key override in Settings if needed.")
        if (key == dateKey) return WebPlotResult(null, "The date key is the time axis; a cumulative distribution plot is not meaningful for it.")
        val (plotData, missingDates, nonNumeric) = datedNumericPayload(data, key)
        if (plotData.length() == 0) return WebPlotResult(null, "Cumulative box timeline requires dated numeric values for this key.")
        val payload = JSONObject().apply {
            put("kind", "history")
            put("data", plotData)
            put("timeAxis", true)
            put("height", 340)
            put("theme", settings.plotTheme.toJson())
        }
        val omitted = buildList {
            if (missingDates > 0) add("$missingDates row(s) have missing/unparseable dates")
            if (nonNumeric > 0) add("$nonNumeric dated row(s) are non-numeric")
        }.joinToString("; ")
        return WebPlotResult(
            webPlot(payload),
            "D3.js cumulative timestamp box plot. Identical datetimes are summed first; sparse/missing dates remain real gaps. Q1–Q3 is the solid box, median is solid, mean is dotted, whiskers are mean ± 1σ, tiny hollow red circles are Tukey outliers, and the blue rhombus/path is the timestamp total.${if (omitted.isBlank()) "" else " Omitted: $omitted."} Pinch/drag to inspect and double-tap to reset.",
        )
    }

    private fun buildAccumulationPlot(data: TableData, key: String): WebPlotResult {
        if (data.dateKey == null) return WebPlotResult(null, "No date key detected for cumulative timeline.")
        val (plotData, _, _) = datedNumericPayload(data, key)
        if (plotData.length() == 0) return WebPlotResult(null, "No dated numeric $key values in the current dataset.")
        val payload = JSONObject().apply {
            put("kind", "accumulation")
            put("data", plotData)
            put("timeAxis", true)
            put("height", 230)
            put("theme", settings.plotTheme.toJson())
        }
        return WebPlotResult(webPlot(payload), "Observable Plot running sum of timestamp-level $key totals. Filtering changes this plot. Pinch/drag to inspect and double-tap to reset.")
    }

    private fun buildNormalPlot(data: TableData, key: String): WebPlotResult {
        val values = data.rows.mapNotNull { Statistics.parseNumber(it.values[key].orEmpty()) }
        if (values.isEmpty()) return WebPlotResult(null, "No numeric $key values in the current dataset.")
        val payload = JSONObject().apply {
            put("kind", "normal")
            put("values", JSONArray(values))
            put("height", 230)
            put("theme", settings.plotTheme.toJson())
        }
        val note = if (values.distinct().size <= 1) " All values are identical, so the fitted distribution collapses around one point." else ""
        return WebPlotResult(webPlot(payload), "Observable Plot fitted normal PDF; bottom ticks show the visible observations.$note Pinch/drag to inspect and double-tap to reset.")
    }

    private fun setPlotViewsActive(view: View, active: Boolean, ancestorVisible: Boolean = true) {
        val visible = ancestorVisible && view.visibility == View.VISIBLE
        if (view is WebPlotView) view.setPlotActive(active && visible)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) setPlotViewsActive(view.getChildAt(index), active, visible)
        }
    }

    private fun accordion(
        title: String,
        initiallyOpen: Boolean = false,
        tooltip: String? = null,
        build: (LinearLayout) -> Unit,
    ): LinearLayout {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BLACK) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(7), dp(10), dp(10))
            setBackgroundColor(BLACK)
        }
        var built = false
        fun ensureBuilt() {
            if (built) return
            built = true
            build(content)
        }
        val header = TextView(this).apply {
            text = if (initiallyOpen) "− $title" else "+ $title"
            textSize = 16f
            setTextColor(if (initiallyOpen) PRIMARY else WHITE)
            setPadding(dp(9), dp(6), dp(9), dp(6))
            minHeight = dp(34)
            background = if (initiallyOpen) activeButtonBackground(PRIMARY) else noBorderBackground()
            AppFonts.apply(this, bold = true)
            setOnClickListener {
                val opening = content.visibility != View.VISIBLE
                if (opening) {
                    ensureBuilt()
                    content.visibility = View.VISIBLE
                    setPlotViewsActive(content, true)
                } else {
                    setPlotViewsActive(content, false)
                    content.visibility = View.GONE
                }
                text = if (opening) "− $title" else "+ $title"
                setTextColor(if (opening) PRIMARY else WHITE)
                background = if (opening) activeButtonBackground(PRIMARY) else noBorderBackground()
            }
        }
        tooltip?.let { description -> tooltipController.attachHold(header, { description }) }
        wrapper.addView(header, matchWidth())
        wrapper.addView(content, matchWidth())
        if (initiallyOpen) {
            ensureBuilt()
            content.visibility = View.VISIBLE
        }
        return wrapper
    }

    private fun addMetric(
        container: LinearLayout,
        name: String,
        value: String,
        nameColor: Int = MUTED,
        valueColor: Int = WHITE,
        tooltip: String? = null,
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2), 0, dp(2)) }
        val nameView = infoText(name).apply { setTextColor(nameColor) }
        tooltip?.let { description -> tooltipController.attachHold(nameView, { description }) }
        row.addView(nameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = infoText(value).apply { gravity = Gravity.END; setTextColor(valueColor) }
        tooltip?.let { description -> tooltipController.attachHold(valueView, { description }) }
        row.addView(valueView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(row, matchWidth())
    }

    private fun addStatisticMetric(container: LinearLayout, name: String, value: Double?, metricColor: Int) {
        val color = when {
            value == null || value.isNaN() || value.isInfinite() -> MUTED
            value < 0.0 -> RED
            else -> metricColor
        }
        addMetric(container, name, value?.let(::fmt) ?: "N/A", nameColor = metricColor, valueColor = color, tooltip = statTooltip(name))
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
                        val fetched = api.fetchTableFile(created.path)
                        fileCache.saveFiles(settings, loadedFiles)
                        fileCache.saveFile(settings, fetched.first)
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(created.path)
                            renderFiles()
                            applyTableData(fetched.second)
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
                        fileCache.removeFile(settings, file.path)
                        fileCache.saveFiles(settings, loadedFiles)
                        val next = loadedFiles.firstOrNull()
                        val data = if (next == null) {
                            TableData(emptyList(), emptyList(), null, null, null, null)
                        } else {
                            val cached = fileCache.loadFile(settings, next.path)
                            if (cached != null) {
                                api.parseCachedTable(cached.text)
                            } else {
                                val fetched = api.fetchTableFile(next.path)
                                fileCache.saveFile(settings, fetched.first)
                                fetched.second
                            }
                        }
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
        val visibleFiles = files.filterNot { it.name.startsWith(".") || it.name.equals(GitHubApi.CONFIG_FILE_NAME, true) }
        if (visibleFiles.isEmpty()) {
            filesList.addView(infoText("No .json files"), matchWidth())
            removeFileButton.isEnabled = false
            return
        }
        removeFileButton.isEnabled = !busy && selectedPath != null && visibleFiles.any { it.path == selectedPath }
        visibleFiles.forEach { file ->
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
        if (::resyncButton.isInitialized) {
            resyncButton.isEnabled = !isBusy
            resyncButton.alpha = if (isBusy) 0.45f else 1f
        }
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
        statusText.text = "$prefix: ${error.message ?: error.javaClass.simpleName}.$extra Tap Re-sync to retry."
        if (::resyncButton.isInitialized) {
            resyncButton.setTextColor(PRIMARY)
            resyncButton.background = activeButtonBackground(PRIMARY)
        }
    }

    private fun showDialog(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(BLACK)
            setStroke(dp(1).coerceAtLeast(1), PRIMARY)
            cornerRadius = dp(5).toFloat()
        })
        dialog.window?.decorView?.let { AppFonts.applyToTree(it, settings.textScale) }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(PRIMARY)
            it.background = inactiveActionBackground(PRIMARY)
            it.minHeight = dp(34)
            AppFonts.apply(it, bold = true)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let {
            it.setTextColor(MUTED)
            it.background = inactiveActionBackground(SECONDARY)
            it.minHeight = dp(34)
            AppFonts.apply(it)
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let {
            it.setTextColor(MUTED)
            it.background = inactiveActionBackground(SECONDARY)
            it.minHeight = dp(34)
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
        minHeight = dp(34)
        minimumHeight = dp(34)
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

    private fun attachTimedHold(view: View, delayMs: Long, action: () -> Unit) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var active = false
        var fired = false
        var downX = 0f
        var downY = 0f
        val runnable = Runnable {
            if (!active) return@Runnable
            fired = true
            action()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    uiHandler.removeCallbacks(runnable)
                    active = true
                    fired = false
                    downX = event.x
                    downY = event.y
                    uiHandler.postDelayed(runnable, delayMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (active && (kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop)) {
                        active = false
                        uiHandler.removeCallbacks(runnable)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    active = false
                    uiHandler.removeCallbacks(runnable)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    active = false
                    uiHandler.removeCallbacks(runnable)
                    if (fired) return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun spacedMatchWidth(bottomDp: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottomDp) }
    private fun frameMatch() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density * settings.uiScale).toInt()
}

package xyz.x3ofiz4.exvia.presentation.main
import xyz.x3ofiz4.exvia.core.json.toJson
import xyz.x3ofiz4.exvia.core.config.RepoConfig
import xyz.x3ofiz4.exvia.domain.model.custom.*
import xyz.x3ofiz4.exvia.domain.model.repository.*
import xyz.x3ofiz4.exvia.domain.model.settings.*
import xyz.x3ofiz4.exvia.domain.model.table.*
import xyz.x3ofiz4.exvia.domain.model.theme.*
import xyz.x3ofiz4.exvia.domain.service.BuiltinExamples
import xyz.x3ofiz4.exvia.domain.service.Statistics
import xyz.x3ofiz4.exvia.domain.service.TableStyleResult
import xyz.x3ofiz4.exvia.presentation.common.*
import xyz.x3ofiz4.exvia.presentation.plot.*
import xyz.x3ofiz4.exvia.presentation.settings.*

import xyz.x3ofiz4.exvia.R
import xyz.x3ofiz4.exvia.app.ExviaApplication
import xyz.x3ofiz4.exvia.app.ExviaContainer
import xyz.x3ofiz4.exvia.presentation.statistics.StatisticsViewModel
import xyz.x3ofiz4.exvia.presentation.notification.NotificationDispatcher

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
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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

class MainActivity : Activity() {
    companion object {
        private val GREEN = Color.rgb(52, 199, 89)
    }

    private enum class Tab { TABLE, STAT, FILES }
    private data class ThemeChoice(val id: String, val label: String)
    private data class CustomStatNode(
        val name: String,
        val fullPath: String,
        val groupIds: MutableList<String> = mutableListOf(),
        val children: LinkedHashMap<String, CustomStatNode> = linkedMapOf(),
    )

    private lateinit var container: ExviaContainer
    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private val statisticsViewModel = StatisticsViewModel()
    private lateinit var settings: RepoSettings
    private val subscriptions = mutableListOf<AutoCloseable>()
    private var renderedRevision = -1L
    private lateinit var tooltipController: TooltipController
    private lateinit var plotRuntime: PlotWebRuntime
    private lateinit var customMetricEngine: CustomMetricEngine
    private lateinit var fieldFormulaEngine: FieldFormulaEngine
    private lateinit var fieldSchemaEngine: FieldSchemaEngine
    private lateinit var metricColorEngine: MetricColorEngine
    private lateinit var notificationScriptEngine: NotificationScriptEngine
    private lateinit var notificationDispatcher: NotificationDispatcher
    private val uiHandler = Handler(Looper.getMainLooper())

    private val palette get() = settings.palette
    private val PRIMARY get() = palette.primaryColor()
    private val SECONDARY get() = palette.secondaryColor()
    private val BLACK get() = palette.tertiaryColor()
    private val SURFACE get() = palette.quaternaryColor()
    private val MUTED get() = palette.quinaryColor()
    private val WHITE get() = palette.senaryColor()
    private val RED = Color.rgb(247, 35, 35)
    private val LOG_GREEN = Color.rgb(52, 199, 89)
    private val LOG_YELLOW = Color.rgb(245, 197, 66)
    private val LOG_BLUE = Color.rgb(41, 182, 246)
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
    private lateinit var tableEntryControls: LinearLayout
    private lateinit var tableControlsToggle: Button
    private var tableControlsVisible = true
    private lateinit var table: TableLayout
    private lateinit var filesList: LinearLayout
    private lateinit var filterInput: EditText
    private lateinit var filterToggle: TextView
    private lateinit var amendButton: Button
    private lateinit var undoButton: Button
    private lateinit var redoButton: Button
    private lateinit var createFileButton: Button
    private lateinit var removeFileButton: Button
    private lateinit var fileScriptButton: Button
    private lateinit var tableTabButton: Button
    private lateinit var statTabButton: Button
    private lateinit var filesTabButton: Button
    private lateinit var resyncButton: TextView
    private lateinit var gitButton: TextView
    private lateinit var titleText: TextView
    private lateinit var filteringMethodButton: TextView
    private lateinit var previousPageButton: Button
    private lateinit var nextPageButton: Button
    private lateinit var pageIndicatorText: TextView
    private lateinit var pageOverlayText: TextView
    private var pageJumpInput: EditText? = null

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
    private lateinit var themeSpinner: Spinner
    private lateinit var plotThemeSpinner: Spinner
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
    private lateinit var plotSelectionSetting: EditText
    private lateinit var plotTooltipBackgroundSetting: EditText
    private lateinit var plotTooltipTextSetting: EditText
    private lateinit var plotTooltipBorderSetting: EditText
    private lateinit var uiScaleSetting: EditText
    private lateinit var textScaleSetting: EditText
    private lateinit var rowsPerPageSetting: EditText
    private lateinit var undoHistoryLimitSetting: EditText
    private lateinit var automaticAmendSpinner: Spinner
    private lateinit var customStatList: LinearLayout
    private var customMetricsDraft = mutableListOf<CustomMetricDefinition>()
    private var customPlotsDraft = mutableListOf<CustomPlotDefinition>()
    private var scriptGroupsDraft = mutableListOf<ScriptGroupDefinition>()
    private var environmentVariablesDraft = mutableListOf<EnvironmentVariableDefinition>()
    private var notificationRulesDraft = mutableListOf<NotificationRule>()
    private var schemaRulesDraft = mutableListOf<SchemaRuleDefinition>()
    private var metricColorMappingsDraft = mutableListOf<MetricColorRule>()
    private var customMetricInputsDraft = mutableMapOf<String, String>()
    private var fileScriptsDraft = mutableListOf<FileScriptDefinition>()
    private var customUiThemesDraft = mutableListOf<NamedUiTheme>()
    private var customPlotThemesDraft = mutableListOf<NamedPlotTheme>()
    private var uiThemeChoices: List<ThemeChoice> = emptyList()
    private var plotThemeChoices: List<ThemeChoice> = emptyList()
    private var suppressUiThemeSelection = false
    private var suppressPlotThemeSelection = false
    private var filterSnippets = mutableListOf<FilterSnippet>()
    private var flaggingRulesDraft = mutableListOf<TableStyleRule>()
    private var colorMappingsDraft = mutableListOf<TableStyleRule>()
    private var imaginaryFieldsDraft = mutableListOf<ImaginaryFieldDefinition>()

    private val formInputs = linkedMapOf<String, EditText>()
    private var selectedPath: String? = null
    private var files: List<RepoFile> = emptyList()
    private var currentData = TableData(emptyList(), emptyList(), null, null, null, null)
    private var activeTab = Tab.TABLE
    private var busy = false
    private var queryMode = TableQueryMode.FILTERING
    private var filterEnabled = false
    private var filterQuery = ""
    private var flagEnabled = false
    private var flagQuery = ""
    private var selectedFlagRule: TableStyleRule? = null
    private var tableStyles = TableStyleResult()
    private var imaginaryKeys: Set<String> = emptySet()
    private var lastImaginaryEvaluationSignature: String? = null
    private var suppressQueryInputChange = false
    private var developerMode = true
    private var titleTapCount = 0
    private var lastTitleTapAt = 0L
    private var paginatedTableData = TableData(emptyList(), emptyList(), null, null, null, null)
    private var currentPageIndex = 0
    private var pageOverlayHideRunnable: Runnable? = null
    private var hasStagedChanges = false
    private var hasAnyStagedChanges = false
    private var gitDialog: AlertDialog? = null
    private var gitCommitList: LinearLayout? = null
    private var gitPageText: TextView? = null
    private var gitPreviousButton: Button? = null
    private var gitNextButton: Button? = null
    private var gitHistoryPage = 1
    private var schemaConfigCache = mapOf<String, FieldSchemaEngine.FieldConfig>()
    private var schemaConfigSignature = ""
    private var metricColorCache = mapOf<String, MetricColorEngine.Colors>()
    private var metricColorSignature = ""
    private var lastEnvironmentSnapshot = ""
    private var environmentSaveRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as ExviaApplication).container
        settingsViewModel = SettingsViewModel(container.configurationRepository)
        mainViewModel = MainViewModel(container.expenseRepository)

        val settingsState = settingsViewModel.state.value
        settings = settingsState.settings
        AppFonts.defaultTextScale = settings.textScale
        developerMode = settingsState.developerMode
        customMetricsDraft = settings.customMetrics.toMutableList()
        customPlotsDraft = settings.customPlots.toMutableList()
        scriptGroupsDraft = settings.scriptGroups.toMutableList().ifEmpty { mutableListOf(ScriptGroupDefinition(DEFAULT_SCRIPT_GROUP_ID, "Default")) }
        environmentVariablesDraft = settings.environmentVariables.toMutableList()
        notificationRulesDraft = settings.notificationRules.toMutableList()
        schemaRulesDraft = settings.schemaRules.toMutableList()
        metricColorMappingsDraft = settings.metricColorMappings.toMutableList()
        customMetricInputsDraft = settings.customMetricInputs.toMutableMap()
        fileScriptsDraft = settings.fileScripts.toMutableList()
        customUiThemesDraft = settings.customUiThemes.toMutableList()
        customPlotThemesDraft = settings.customPlotThemes.toMutableList()
        filterSnippets = settingsState.snippets.toMutableList()
        flaggingRulesDraft = settings.flaggingRules.toMutableList()
        colorMappingsDraft = settings.colorMappings.toMutableList()
        imaginaryFieldsDraft = settings.imaginaryFields.toMutableList()

        tooltipController = TooltipController(this, { PRIMARY }, { BLACK }, { WHITE })
        val lightPalette = isLightPalette(settings.palette)
        setTheme(if (lightPalette) R.style.AppTheme_Light else R.style.AppTheme_Dark)
        window.statusBarColor = BLACK
        window.navigationBarColor = BLACK
        window.decorView.systemUiVisibility = if (lightPalette) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else 0

        plotRuntime = PlotWebRuntime(
            this,
            environmentProvider = { xyz.x3ofiz4.exvia.data.local.SettingsStore.environmentPayload(environmentVariablesDraft) },
            onEnvironmentChanged = { snapshot -> runOnUiThread { persistEnvironmentSnapshot(snapshot) } },
        )
        customMetricEngine = CustomMetricEngine(plotRuntime) { settings.plotTheme }
        fieldFormulaEngine = FieldFormulaEngine(plotRuntime) { settings.plotTheme }
        fieldSchemaEngine = FieldSchemaEngine(plotRuntime)
        metricColorEngine = MetricColorEngine(plotRuntime)
        notificationScriptEngine = NotificationScriptEngine(plotRuntime)
        notificationDispatcher = NotificationDispatcher(this)
        setContentView(buildUi())
        bindViewModels()
        window.decorView.post { plotRuntime.prewarm(); uiHandler.postDelayed({ initializeEnvironmentRuntime() }, 180L) }

        when {
            !settingsState.hasToken -> {
                statusText.text = "GitHub PAT is required. Add it in Settings."
                drawerRoot.openDrawer()
            }
            !settingsState.repoInitializationAsked -> promptRepositoryInitialization()
            !settings.isConfigured() -> {
                statusText.text = "Configure GitHub in Settings. Swipe right from the left edge."
                drawerRoot.openDrawer()
            }
            else -> mainViewModel.loadInitial(settings)
        }
    }

    override fun onDestroy() {
        subscriptions.forEach { runCatching { it.close() } }
        mainViewModel.close()
        settingsViewModel.close()
        plotRuntime.destroy()
        super.onDestroy()
    }

    private fun bindViewModels() {
        subscriptions += mainViewModel.state.observe { state ->
            runOnUiThread { renderMainState(state) }
        }
        subscriptions += mainViewModel.effects.observe { effect ->
            runOnUiThread {
                when (effect) {
                    is MainEffect.Error -> handleError(effect.throwable as? Exception ?: Exception(effect.throwable), effect.prefix)
                    is MainEffect.ToastMessage -> Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
                    is MainEffect.GitHistoryLoaded -> renderGitHistory(effect.page)
                    is MainEffect.AutomationEvent -> triggerAutomationEvent(effect.name, effect.payload)
                }
            }
        }
        subscriptions += settingsViewModel.effects.observe { effect ->
            runOnUiThread {
                when (effect) {
                    is SettingsEffect.Error -> handleError(effect.throwable as? Exception ?: Exception(effect.throwable), effect.prefix)
                    is SettingsEffect.Reload -> {
                        Toast.makeText(this, effect.message, Toast.LENGTH_LONG).show()
                        triggerAutomationEvent("event.save", mapOf("message" to effect.message))
                        uiHandler.postDelayed({ recreate() }, 700L)
                    }
                    is SettingsEffect.ReportCreated -> {
                        setBusy(false)
                        statusText.text = "Report created: ${effect.url}"
                    }
                    is SettingsEffect.RepositoryCreated -> {
                        settings = effect.settings
                        statusText.text = "Repository initialized. Reloading…"
                        recreate()
                    }
                    is SettingsEffect.TableRulesSaved -> {
                        setBusy(false)
                        statusText.text = effect.message
                    }
                }
            }
        }
    }

    private fun renderMainState(state: MainUiState) {
        val dataChanged = state.revision != renderedRevision
        files = state.files
        selectedPath = state.selectedPath
        currentData = state.sourceData
        queryMode = state.queryMode
        filterEnabled = state.filterEnabled
        filterQuery = state.filterQuery
        flagEnabled = state.flagEnabled
        flagQuery = state.flagQuery
        selectedFlagRule = state.activeFlagRule
        tableStyles = state.tableStyles
        imaginaryKeys = state.imaginaryKeys
        hasStagedChanges = state.hasStagedChanges
        hasAnyStagedChanges = state.hasAnyStagedChanges
        busy = state.busy
        if (::undoButton.isInitialized) { undoButton.isEnabled = !state.busy && state.canUndo; undoButton.alpha = if (undoButton.isEnabled) 1f else 0.38f }
        if (::redoButton.isInitialized) { redoButton.isEnabled = !state.busy && state.canRedo; redoButton.alpha = if (redoButton.isEnabled) 1f else 0.38f }
        if (::statusText.isInitialized && state.status.isNotBlank()) statusText.text = state.status
        if (::selectedFileText.isInitialized) {
            selectedFileText.text = state.selectedPath?.substringAfterLast('/') ?: "No file selected"
        }
        if (::filterInput.isInitialized) {
            val expected = if (queryMode == TableQueryMode.FILTERING) state.filterQuery else state.flagQuery
            if (filterInput.text.toString() != expected) {
                suppressQueryInputChange = true
                filterInput.setText(expected)
                suppressQueryInputChange = false
            }
            filterInput.setTextColor(if (queryMode == TableQueryMode.FILTERING && state.filterError != null) RED else WHITE)
            updateQueryControls()
        }
        if (dataChanged && ::dynamicForm.isInitialized) {
            renderedRevision = state.revision
            renderDynamicForm(state.sourceData)
            renderTable(state.visibleData)
            renderStats(state.visibleData)
            renderFiles()
            updateFilterToggle()
            AppFonts.applyToTree(drawerRoot, settings.textScale)
            scheduleSchemaEvaluation(state.sourceData)
            scheduleImaginaryFieldEvaluation(state)
        }
        setBusy(state.busy)
    }

    private fun scheduleImaginaryFieldEvaluation(state: MainUiState) {
        val path = state.selectedPath ?: return
        val definitions = settings.imaginaryFields.filter { it.enabled }
        val baseKeys = state.sourceData.keys.filterNot { key -> state.imaginaryKeys.any { it.equals(key, true) } }
        val baseRows = state.sourceData.rows.map { row ->
            row.copy(values = LinkedHashMap(row.values.filterKeys { key -> baseKeys.any { it.equals(key, true) } }))
        }
        val base = state.sourceData.copy(
            keys = baseKeys,
            rows = baseRows,
            dateKey = settings.detectDateKey(baseKeys),
            moneyKey = settings.detectMoneyKey(baseKeys),
            tickerKey = settings.detectTickerKey(baseKeys),
            tagsKey = settings.detectTagsKey(baseKeys),
        )
        val signature = buildString {
            append(path).append('|')
            definitions.forEach { append(it.id).append(':').append(it.name).append(':').append(it.expression).append(':').append(it.manualValues.hashCode()).append(':').append(it.enabled).append('|') }
            base.rows.forEach { append(it.originalIndex).append(':').append(it.originalJson.hashCode()).append(';') }
        }
        if (signature == lastImaginaryEvaluationSignature) return
        lastImaginaryEvaluationSignature = signature
        if (definitions.isEmpty()) {
            if (state.imaginaryKeys.isNotEmpty()) mainViewModel.applyImaginaryValues(settings, emptyMap())
            return
        }
        fieldFormulaEngine.evaluateImaginaryFields(
            definitions = definitions,
            table = base,
            fileName = path.substringAfterLast('/'),
        ) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { mainViewModel.applyImaginaryValues(settings, it) },
                    onFailure = {
                        // Do not cache a failed evaluation. A temporary WebView/runtime
                        // failure must be allowed to retry on the next render/state update.
                        lastImaginaryEvaluationSignature = null
                        statusText.text = "Imaginary field formula error: ${it.message ?: it.javaClass.simpleName}"
                    },
                )
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
        settingsViewModel.setDeveloperMode(developerMode)
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
        gitButton = TextView(this).apply {
            text = "Git"
            setTextColor(PRIMARY)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = noBorderBackground()
            AppFonts.apply(this, bold = true)
            setOnClickListener { if (!busy) showGitPanel() }
        }
        titleRow.addView(gitButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(3) })
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
            setTextColor(LOG_BLUE)
            setPadding(0, dp(4), 0, dp(10))
            AppFonts.apply(this)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { setTextColor(statusColor(s?.toString().orEmpty())) }
            })
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

        tableControlsToggle = styledButton("Hide input controls").apply {
            setOnClickListener {
                tableControlsVisible = !tableControlsVisible
                tableEntryControls.visibility = if (tableControlsVisible) View.VISIBLE else View.GONE
                text = if (tableControlsVisible) "Hide input controls" else "Show input controls"
            }
        }
        addView(tableControlsToggle, spacedMatchWidth(3))

        tableEntryControls = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        addView(tableEntryControls, matchWidth())

        dynamicForm = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        tableEntryControls.addView(dynamicForm, matchWidth())

        val fieldOperations = styledButton("Fields").apply { setOnClickListener { showFieldOperationsManager() } }
        val imaginaryOperations = styledButton("Imaginary fields").apply { setOnClickListener { showImaginaryFieldOperationsManager() } }
        val operationRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        operationRow.addView(fieldOperations, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(3) })
        operationRow.addView(imaginaryOperations, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(3) })
        tableEntryControls.addView(operationRow, spacedMatchWidth(4))

        amendButton = styledButton("Amend").apply { setOnClickListener { amend() } }
        tableEntryControls.addView(amendButton, spacedMatchWidth(4))

        val historyActions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        undoButton = styledButton("Undo").apply { isEnabled = false; alpha = 0.38f; setOnClickListener { if (!busy) mainViewModel.undo(settings) } }
        redoButton = styledButton("Redo").apply { isEnabled = false; alpha = 0.38f; setOnClickListener { if (!busy) mainViewModel.redo(settings) } }
        historyActions.addView(undoButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(3) })
        historyActions.addView(redoButton, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(3) })
        tableEntryControls.addView(historyActions, spacedMatchWidth(4))

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
                    if (suppressQueryInputChange) return
                    val value = s?.toString().orEmpty()
                    if (queryMode == TableQueryMode.FILTERING) {
                        filterQuery = value
                        if (filterEnabled) applyFilterAndRender(showStatus = false)
                    } else {
                        flagQuery = value
                        if (flagEnabled) applyFlagAndRender(showStatus = false)
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            attachTimedHold(this, 2_000L) {
                if (queryMode == TableQueryMode.FILTERING) showFilterSnippetManager()
                else showFlaggingRuleManager()
            }
        }
        filteringMethodButton = TextView(this@MainActivity).apply {
            text = "Filtering method"
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(PRIMARY)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            minHeight = dp(30)
            background = inactiveActionBackground(PRIMARY)
            AppFonts.apply(this, bold = true)
            setOnClickListener {
                if (queryMode == TableQueryMode.FILTERING) showFilteringMethodManager()
                else showFlaggingMethodManager()
            }
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
                if (queryMode == TableQueryMode.FILTERING) {
                    filterEnabled = !filterEnabled
                    applyFilterAndRender(showStatus = true)
                } else {
                    flagEnabled = !flagEnabled
                    applyFlagAndRender(showStatus = true)
                }
                updateQueryControls()
            }
            attachTimedHold(this, 1_000L) { toggleQueryMode() }
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
        val tableFrame = FrameLayout(this@MainActivity).apply {
            setBackgroundColor(BLACK)
            addView(vertical, frameMatch())
        }
        pageOverlayText = TextView(this@MainActivity).apply {
            visibility = View.GONE
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = activeButtonBackground(PRIMARY)
            elevation = dp(6).toFloat()
            AppFonts.apply(this, bold = true)
        }
        tableFrame.addView(pageOverlayText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))
        addView(tableFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val pagination = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        previousPageButton = styledButton("Previous").apply {
            setOnClickListener { changeTablePage(-1) }
        }
        pageIndicatorText = TextView(this@MainActivity).apply {
            gravity = Gravity.CENTER
            setTextColor(MUTED)
            setPadding(dp(4), 0, dp(4), 0)
            isClickable = true
            isFocusable = true
            background = inactiveActionBackground(PRIMARY)
            setOnClickListener { beginPageJump() }
            AppFonts.apply(this, bold = true)
        }
        nextPageButton = styledButton("Next").apply {
            setOnClickListener { changeTablePage(1) }
        }
        pagination.addView(previousPageButton, LinearLayout.LayoutParams(0, dp(34), 1f))
        pagination.addView(pageIndicatorText, LinearLayout.LayoutParams(0, dp(34), 1f))
        pagination.addView(nextPageButton, LinearLayout.LayoutParams(0, dp(34), 1f))
        addView(pagination, matchWidth())
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
        fileScriptButton = styledButton("SQLite file scripts").apply { setOnClickListener { showFileScriptManager() } }
        addView(fileScriptButton, spacedMatchWidth(8))

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
        val token = configField("GitHub PAT", "github.pat", "", "Personal access token used for repository files and issue creation. It is encrypted locally and never uploaded in the synchronized config file.", password = true)
        tokenSetting = token.input.apply {
            hint = if (settingsViewModel.token() == null) "github.pat" else "github.pat  ${"*".repeat(12)}"
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
        val colorMappingButton = styledButton("Color Mapping").apply {
            setOnClickListener { showColorMappingManager() }
        }
        val imaginaryFieldsButton = styledButton("Imaginary fields").apply {
            setOnClickListener { showImaginaryFieldManager() }
        }
        val environmentButton = styledButton("Variables / ENV").apply { setOnClickListener { showEnvironmentManager() } }
        val notificationsButton = styledButton("Notifications").apply { setOnClickListener { showNotificationManager() } }
        val schemaRulesButton = styledButton("Key schema scripts").apply { setOnClickListener { showSchemaRuleManager() } }
        val metricColorButton = styledButton("Metric Color Mapping").apply { setOnClickListener { showMetricColorManager() } }
        val plotColumns = configField("Columns with plotting enabled", "stats.plot_columns", settings.plotColumns.joinToString(", "), "Comma-separated numeric JSON keys that receive built-in plots. Default: price.")
        plotColumnsSetting = plotColumns.input
        val financeColumns = configField("Columns reported as personal finance", "finance.columns", settings.financeColumns.joinToString(", "), "Comma-separated numeric JSON keys used for personal-finance reports. Default: price.")
        financeColumnsSetting = financeColumns.input

        val uiScale = configField("UI size multiplier", "display.ui_scale", settings.uiScale.toString(), "Scales margins, padding, control height, and other density-based UI dimensions. Recommended range: 0.70–1.60.")
        uiScaleSetting = uiScale.input.apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val textScale = configField("Text size multiplier", "display.text_scale", settings.textScale.toString(), "Scales text independently from the rest of the UI. Recommended range: 0.70–1.80.")
        textScaleSetting = textScale.input.apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val rowsPerPage = configField("Rows per table page", "display.rows_per_page", settings.rowsPerPage.toString(), "Number of rows shown on one Table page. Allowed range: 1–500.")
        rowsPerPageSetting = rowsPerPage.input.apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val undoHistory = configField("Undo/Redo history", "display.undo_history_limit", settings.undoHistoryLimit.toString(), "Maximum Table CRUD undo history retained in memory. Only changed-row deltas are stored. Allowed range: 1–50.")
        undoHistoryLimitSetting = undoHistory.input.apply { inputType = InputType.TYPE_CLASS_NUMBER }
        automaticAmendSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Automatic · Commit immediately", "Manual · Stage locally"),
            )
            setSelection(if (settings.automaticAmend) 0 else 1)
            backgroundTintList = inputTint()
        }
        val automaticAmendWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            addView(TextView(this@MainActivity).apply {
                text = "Git amend mode"
                setTextColor(WHITE)
                textSize = 12.5f
                setPadding(dp(4), dp(2), dp(4), dp(2))
                AppFonts.apply(this, bold = true)
                tooltipController.attachHold(this, { "Automatic commits each Table CRUD operation immediately. Manual stores Table changes in a persistent local stage until Git → Amend is pressed." })
            }, matchWidth())
            addView(automaticAmendSpinner, matchWidth())
        }

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
        val plotSelection = colorConfigField("Selected node", "plot.selection", settings.plotTheme.selection, "Ring/highlight color used for a tapped and pinned graph node.")
        plotSelectionSetting = plotSelection.input
        val plotTooltipBackground = colorConfigField("Tooltip background", "plot.tooltip_background", settings.plotTheme.tooltipBackground, "Pinned graph-tooltip background color.")
        plotTooltipBackgroundSetting = plotTooltipBackground.input
        val plotTooltipText = colorConfigField("Tooltip text", "plot.tooltip_text", settings.plotTheme.tooltipText, "Pinned graph-tooltip text color.")
        plotTooltipTextSetting = plotTooltipText.input
        val plotTooltipBorder = colorConfigField("Tooltip border", "plot.tooltip_border", settings.plotTheme.tooltipBorder, "Pinned graph-tooltip border color.")
        plotTooltipBorderSetting = plotTooltipBorder.input

        themeSpinner = Spinner(this).apply { backgroundTintList = inputTint() }
        themeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressUiThemeSelection) {
                    uiThemeChoices.getOrNull(position)?.let { applyUiThemeChoice(it.id) }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        rebuildUiThemeSpinner(settings.activeUiThemeId)

        plotThemeSpinner = Spinner(this).apply { backgroundTintList = inputTint() }
        plotThemeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!suppressPlotThemeSelection) {
                    plotThemeChoices.getOrNull(position)?.let { applyPlotThemeChoice(it.id) }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        rebuildPlotThemeSpinner(settings.activePlotThemeId)

        if (developerMode) {
            body.addView(accordion("GitHub", initiallyOpen = true, tooltip = "Repository connection, report target, data folder, default file, and encrypted GitHub PAT.") { container ->
                listOf(owner.wrapper, repo.wrapper, branch.wrapper, folder.wrapper, defaultFile.wrapper, token.wrapper)
                    .forEach { container.addView(it, spacedMatchWidth(5)) }
                container.addView(styledButton("Clear stored PAT", accent = SECONDARY).apply {
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Clear GitHub PAT?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear") { _, _ ->
                                settingsViewModel.clearToken(); tokenSetting.text.clear(); tokenSetting.hint = "github.pat"; statusText.text = "Stored PAT cleared."
                            }.create().also { showDialog(it) }
                    }
                }, spacedMatchWidth(6))
            }, spacedMatchWidth(10))
        }

        body.addView(accordion("Color", tooltip = "Built-in and named custom UI themes, plus six configurable semantic palette colors.") { container ->
            container.addView(infoText("UI theme").apply {
                AppFonts.apply(this, bold = true, textScale = settings.textScale)
                tooltipController.attachHold(this, { "Select a built-in theme or one of your named custom themes. Selecting a theme copies its colors into the editable fields below." })
            }, spacedMatchWidth(3))
            container.addView(themeSpinner, spacedMatchWidth(6))
            val themeActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            themeActions.addView(styledButton("Save current as theme").apply {
                setOnClickListener { promptCreateUiTheme() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(3) })
            themeActions.addView(styledButton("Delete selected", accent = SECONDARY).apply {
                setOnClickListener { deleteSelectedUiTheme() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3) })
            container.addView(themeActions, spacedMatchWidth(8))
            listOf(primary.wrapper, secondary.wrapper, tertiary.wrapper, quaternary.wrapper, quinary.wrapper, senary.wrapper)
                .forEach { container.addView(it, spacedMatchWidth(5)) }
        }, spacedMatchWidth(10))

        body.addView(accordion("Interface", tooltip = "Resize the full UI and text independently. Changes take effect after Save settings and reload.") { container ->
            listOf(uiScale.wrapper, textScale.wrapper, rowsPerPage.wrapper, undoHistory.wrapper).forEach { container.addView(it, spacedMatchWidth(5)) }
            container.addView(automaticAmendWrapper, spacedMatchWidth(5))
        }, spacedMatchWidth(10))

        body.addView(accordion("Plotting", tooltip = "Built-in and named custom plot themes used by the D3.js and Observable Plot runtime.") { container ->
            container.addView(infoText("Engine: pre-warmed WebView · D3.js for statistical boxes · Observable Plot for accumulation/distribution · Arquero for table operations.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
            container.addView(infoText("Plot theme").apply {
                AppFonts.apply(this, bold = true, textScale = settings.textScale)
                tooltipController.attachHold(this, { "Select Black, Ayu, or a named plot theme. Theme colors are copied into the editable plotting fields below." })
            }, spacedMatchWidth(3))
            container.addView(plotThemeSpinner, spacedMatchWidth(6))
            val plotThemeActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            plotThemeActions.addView(styledButton("Save current as theme").apply {
                setOnClickListener { promptCreatePlotTheme() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(3) })
            plotThemeActions.addView(styledButton("Delete selected", accent = SECONDARY).apply {
                setOnClickListener { deleteSelectedPlotTheme() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3) })
            container.addView(plotThemeActions, spacedMatchWidth(8))
            listOf(
                plotBackground.wrapper, plotSurface.wrapper, plotText.wrapper, plotMuted.wrapper,
                plotGrid.wrapper, plotAxis.wrapper, plotPositive.wrapper, plotNegative.wrapper,
                plotObservation.wrapper, plotOutlier.wrapper, plotCenter.wrapper, plotAccent.wrapper,
                plotSelection.wrapper, plotTooltipBackground.wrapper, plotTooltipText.wrapper, plotTooltipBorder.wrapper,
            ).forEach { container.addView(it, spacedMatchWidth(5)) }
        }, spacedMatchWidth(10))

        if (developerMode) {
            body.addView(accordion("Schema & Display", tooltip = "Schema overrides, category colors, plot-enabled columns, and finance-report columns.") { container ->
                listOf(array.wrapper, date.wrapper, money.wrapper, ticker.wrapper, tags.wrapper, plotColumns.wrapper, financeColumns.wrapper)
                    .forEach { container.addView(it, spacedMatchWidth(5)) }
                container.addView(infoText("Automatic cell/row styling rules. They use the same SQLite-like matcher as Filtering and are applied whenever the table renders.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
                container.addView(colorMappingButton, spacedMatchWidth(5))
                container.addView(metricColorButton, spacedMatchWidth(5))
                container.addView(imaginaryFieldsButton, spacedMatchWidth(5))
                container.addView(schemaRulesButton, spacedMatchWidth(5))
            }, spacedMatchWidth(10))

            body.addView(accordion("Automation & ENV", tooltip = "Persistent JavaScript ENV variables and Android notification rules driven by Exvia events.") { container ->
                container.addView(infoText("ENV is an Exvia-managed JavaScript environment, not an OS .env file. Use ENV.name.get()/post()/put()/delete() from formulas, plots, metrics, schema rules, and notifications.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
                container.addView(environmentButton, spacedMatchWidth(5))
                container.addView(notificationsButton, spacedMatchWidth(5))
            }, spacedMatchWidth(10))

            customStatList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BLACK) }
            renderCustomStatSettings()
            body.addView(accordion("Custom Stat", tooltip = "Manage custom plots and custom metrics together. Shared Custom Accordions control where both appear in Stat.") { container ->
                container.addView(infoText("Available modules: d3, Plot, aq, context, theme, helpers, jsonFile, and ENV. Plotting modules are listed before Metrics inside each shared accordion.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
                container.addView(customStatList, matchWidth())
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(styledButton("+ Plot").apply { setOnClickListener { editCustomPlot(null) } }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) })
                actions.addView(styledButton("+ Metric").apply { setOnClickListener { editCustomMetric(null) } }, LinearLayout.LayoutParams(0, dp(34), 1f))
                container.addView(actions, spacedMatchWidth(4))
            }, spacedMatchWidth(12))
        } else {
            // Keep late-init property valid while advanced sections are hidden.
            customStatList = LinearLayout(this)
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
            if (!password) setOnLongClickListener { true }
            if (password) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                isLongClickable = true
                setTextIsSelectable(true)
            }
            if (multiline) { isSingleLine = false; minLines = 3; gravity = Gravity.TOP }
        }
        if (!password) tooltipController.attachHold(input, { description })
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
        plotSelectionSetting.setText(theme.selection)
        plotTooltipBackgroundSetting.setText(theme.tooltipBackground)
        plotTooltipTextSetting.setText(theme.tooltipText)
        plotTooltipBorderSetting.setText(theme.tooltipBorder)
    }


    private fun applyThemeFields(palette: ThemePalette) {
        primarySetting.setText(palette.primary)
        secondarySetting.setText(palette.secondary)
        tertiarySetting.setText(palette.tertiary)
        quaternarySetting.setText(palette.quaternary)
        quinarySetting.setText(palette.quinary)
        senarySetting.setText(palette.senary)
    }

    private fun currentUiPalette(): ThemePalette = ThemePalette(
        primary = primarySetting.text.toString().trim(),
        secondary = secondarySetting.text.toString().trim(),
        tertiary = tertiarySetting.text.toString().trim(),
        quaternary = quaternarySetting.text.toString().trim(),
        quinary = quinarySetting.text.toString().trim(),
        senary = senarySetting.text.toString().trim(),
    )

    private fun currentPlotTheme(): PlotTheme = PlotTheme(
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
        selection = plotSelectionSetting.text.toString().trim(),
        tooltipBackground = plotTooltipBackgroundSetting.text.toString().trim(),
        tooltipText = plotTooltipTextSetting.text.toString().trim(),
        tooltipBorder = plotTooltipBorderSetting.text.toString().trim(),
    )

    private fun rebuildUiThemeSpinner(selectedId: String) {
        uiThemeChoices = ThemePreset.entries.map { ThemeChoice("builtin:${it.id}", it.displayName) } +
            customUiThemesDraft.map { ThemeChoice(it.id, "Custom · ${it.name}") }
        suppressUiThemeSelection = true
        themeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            uiThemeChoices.map { it.label },
        )
        val index = uiThemeChoices.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
        themeSpinner.setSelection(index, false)
        themeSpinner.post { suppressUiThemeSelection = false }
    }

    private fun rebuildPlotThemeSpinner(selectedId: String) {
        plotThemeChoices = listOf(
            ThemeChoice("builtin:black", "Black default"),
            ThemeChoice("builtin:ayu", "Ayu plot"),
        ) + customPlotThemesDraft.map { ThemeChoice(it.id, "Custom · ${it.name}") }
        suppressPlotThemeSelection = true
        plotThemeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            plotThemeChoices.map { it.label },
        )
        val index = plotThemeChoices.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
        plotThemeSpinner.setSelection(index, false)
        plotThemeSpinner.post { suppressPlotThemeSelection = false }
    }

    private fun applyUiThemeChoice(id: String) {
        if (id.startsWith("builtin:")) {
            applyThemeFields(ThemePreset.fromId(id.removePrefix("builtin:")))
            return
        }
        customUiThemesDraft.firstOrNull { it.id == id }?.let { applyThemeFields(it.palette) }
    }

    private fun applyPlotThemeChoice(id: String) {
        when (id) {
            "builtin:black" -> applyPlotThemeFields(PlotTheme.default())
            "builtin:ayu" -> applyPlotThemeFields(PlotTheme.ayu())
            else -> customPlotThemesDraft.firstOrNull { it.id == id }?.let { applyPlotThemeFields(it.theme) }
        }
    }

    private fun promptCreateUiTheme() {
        val name = styledInput("Theme name")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Save UI theme")
            .setView(name)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val clean = name.text.toString().trim()
                if (clean.isBlank()) {
                    Toast.makeText(this, "Theme name is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (customUiThemesDraft.any { it.name.equals(clean, ignoreCase = true) }) {
                    Toast.makeText(this, "A UI theme with that name already exists.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val item = NamedUiTheme(UUID.randomUUID().toString(), clean, currentUiPalette())
                customUiThemesDraft += item
                rebuildUiThemeSpinner(item.id)
                Toast.makeText(this, "UI theme '$clean' added. Save settings to persist it.", Toast.LENGTH_SHORT).show()
            }
            .create()
        showDialog(dialog)
    }

    private fun promptCreatePlotTheme() {
        val name = styledInput("Plot theme name")
        val dialog = AlertDialog.Builder(this)
            .setTitle("Save plot theme")
            .setView(name)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val clean = name.text.toString().trim()
                if (clean.isBlank()) {
                    Toast.makeText(this, "Theme name is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (customPlotThemesDraft.any { it.name.equals(clean, ignoreCase = true) }) {
                    Toast.makeText(this, "A plot theme with that name already exists.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val item = NamedPlotTheme(UUID.randomUUID().toString(), clean, currentPlotTheme())
                customPlotThemesDraft += item
                rebuildPlotThemeSpinner(item.id)
                Toast.makeText(this, "Plot theme '$clean' added. Save settings to persist it.", Toast.LENGTH_SHORT).show()
            }
            .create()
        showDialog(dialog)
    }

    private fun deleteSelectedUiTheme() {
        val selected = uiThemeChoices.getOrNull(themeSpinner.selectedItemPosition) ?: return
        if (selected.id.startsWith("builtin:")) {
            Toast.makeText(this, "Built-in themes cannot be removed.", Toast.LENGTH_SHORT).show()
            return
        }
        val item = customUiThemesDraft.firstOrNull { it.id == selected.id } ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete UI theme?")
            .setMessage(item.name)
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                customUiThemesDraft.removeAll { it.id == item.id }
                rebuildUiThemeSpinner("builtin:${ThemePreset.DEFAULT.id}")
                applyThemeFields(ThemePalette.preset(ThemePreset.DEFAULT))
            }
            .create().also { showDialog(it) }
    }

    private fun deleteSelectedPlotTheme() {
        val selected = plotThemeChoices.getOrNull(plotThemeSpinner.selectedItemPosition) ?: return
        if (selected.id.startsWith("builtin:")) {
            Toast.makeText(this, "Built-in plot themes cannot be removed.", Toast.LENGTH_SHORT).show()
            return
        }
        val item = customPlotThemesDraft.firstOrNull { it.id == selected.id } ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete plot theme?")
            .setMessage(item.name)
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                customPlotThemesDraft.removeAll { it.id == item.id }
                rebuildPlotThemeSpinner("builtin:black")
                applyPlotThemeFields(PlotTheme.default())
            }
            .create().also { showDialog(it) }
    }

    private fun normalizedScriptGroups(): List<ScriptGroupDefinition> {
        val default = ScriptGroupDefinition(DEFAULT_SCRIPT_GROUP_ID, "Default")
        return (listOf(default) + scriptGroupsDraft.filterNot { it.id == DEFAULT_SCRIPT_GROUP_ID }).distinctBy { it.id }
    }

    private fun renderCustomStatSettings() {
        if (!::customStatList.isInitialized) return
        customStatList.removeAllViews()

        customStatList.addView(accordion("Built-in metric examples", initiallyOpen = false) { examples ->
            val allExamples = BuiltinExamples.customMetrics + BuiltinExamples.customMetricInputExamples
            examples.addView(infoText("Ordinary and input-returning metric templates. Using one creates an editable copy in the selected Custom Accordion.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
            allExamples.forEach { example ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(infoText(example.name).apply { setTextColor(WHITE); textSize = 12f; AppFonts.apply(this) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply { text = "Use"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { editCustomMetric(null, example) } }, LinearLayout.LayoutParams(dp(52), dp(30)))
                examples.addView(row, matchWidth())
            }
        }, spacedMatchWidth(4))

        customStatList.addView(accordion("Built-in plot examples", initiallyOpen = false) { examples ->
            BuiltinExamples.customPlots.forEach { example ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(infoText("${example.name}\n${example.engine}").apply { setTextColor(WHITE); textSize = 12f; AppFonts.apply(this) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply { text = "Use"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { editCustomPlot(null, example) } }, LinearLayout.LayoutParams(dp(52), dp(30)))
                examples.addView(row, matchWidth())
            }
        }, spacedMatchWidth(5))

        // One shared manager for Plot + Metric group hierarchy. Keeping the
        // button here (after examples, before group rows) avoids the duplicate
        // Custom/Manage Custom Accordions controls from earlier builds.
        customStatList.addView(styledButton("Custom Accordions").apply {
            setOnClickListener { showScriptGroupManager() }
        }, spacedMatchWidth(6))

        normalizedScriptGroups().forEach { group ->
            val plots = customPlotsDraft.filter { it.groupId == group.id }
            val metrics = customMetricsDraft.filter { it.groupId == group.id }
            customStatList.addView(accordion(group.name, initiallyOpen = group.id == DEFAULT_SCRIPT_GROUP_ID) { box ->
                box.addView(infoText("# Plotting").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(3))
                if (plots.isEmpty()) box.addView(infoText("No plots in this accordion.").apply { setTextColor(MUTED) }, spacedMatchWidth(3))
                plots.forEach { plot ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(infoText(plot.name).apply { setTextColor(if (plot.enabled) WHITE else MUTED) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(TextView(this).apply { text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { editCustomPlot(plot) } }, LinearLayout.LayoutParams(dp(48), dp(30)))
                    row.addView(TextView(this).apply { text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true); setOnClickListener { customPlotsDraft.removeAll { it.id == plot.id }; persistCustomPlots("Custom plot removed."); renderCustomStatSettings() } }, LinearLayout.LayoutParams(dp(34), dp(30)))
                    box.addView(row, matchWidth())
                }
                box.addView(infoText("# Metrics").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(5))
                if (metrics.isEmpty()) box.addView(infoText("No metrics in this accordion.").apply { setTextColor(MUTED) }, spacedMatchWidth(3))
                metrics.forEach { metric ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(infoText(metric.name).apply { setTextColor(if (metric.enabled) WHITE else MUTED) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(TextView(this).apply { text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { editCustomMetric(metric) } }, LinearLayout.LayoutParams(dp(48), dp(30)))
                    row.addView(TextView(this).apply { text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true); setOnClickListener { customMetricsDraft.removeAll { it.id == metric.id }; persistCustomMetrics("Custom metric removed."); renderCustomStatSettings() } }, LinearLayout.LayoutParams(dp(34), dp(30)))
                    box.addView(row, matchWidth())
                }
            }, matchWidth())
        }
    }

    private fun groupSpinner(selectedId: String): Spinner {
        val groups = normalizedScriptGroups()
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, groups.map { it.name })
            backgroundTintList = inputTint()
            setSelection(groups.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
            tag = groups
        }
    }

    private fun editCustomMetric(existing: CustomMetricDefinition?, template: CustomMetricDefinition? = null) {
        val source=existing?:template
        val name=styledInput("custom_metric.name").apply{setText(source?.name?.removePrefix("Example · ").orEmpty())}
        val group=groupSpinner(source?.groupId?:DEFAULT_SCRIPT_GROUP_ID)
        val script=JavaScriptCodeEditor(this).apply{
            hint="custom_metric.javascript"
            setText(source?.script?:"const rows = JSON.parse(jsonFile.content);\nreturn rows.length;")
        }
        var enabled=source?.enabled?:true
        val enabledButton=styledButton(if(enabled)"Enabled" else "Disabled").apply{setOnClickListener{enabled=!enabled;text=if(enabled)"Enabled" else "Disabled"}}
        val body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,dp(14),0)
            addView(infoText("Available: jsonFile, d3, Plot, aq, theme, helpers, context.inputs, and ENV. Return a scalar/object, or {label,value,inputs:[{name,label,placeholder,default,env:'ENV.x.path'}]} to render persistent inputs.").apply{setTextColor(MUTED)},spacedMatchWidth(5))
            addView(name,spacedMatchWidth(5));addView(group,spacedMatchWidth(5));addView(script,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(350)));addView(enabledButton,spacedMatchWidth(5))
        }
        val dialog=AlertDialog.Builder(this).setTitle(if(existing!=null)"Edit custom metric" else "New custom metric").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val n=name.text.toString().trim();val code=script.text.toString().trim();if(n.isBlank()){name.error="Name is required";return@setOnClickListener};if(code.isBlank()){script.error="JavaScript is required";return@setOnClickListener}
            @Suppress("UNCHECKED_CAST") val groups=group.tag as List<ScriptGroupDefinition>; val gid=groups.getOrNull(group.selectedItemPosition)?.id?:DEFAULT_SCRIPT_GROUP_ID
            val next=CustomMetricDefinition(existing?.id?:UUID.randomUUID().toString(),n,code,enabled,gid)
            customMetricsDraft=if(existing==null)(customMetricsDraft+next).toMutableList() else customMetricsDraft.map{if(it.id==existing.id)next else it}.toMutableList()
            persistCustomMetrics("Custom metric auto-saved.");renderCustomStatSettings();dialog.dismiss()
        }}
        showDialog(dialog)
    }

    private fun editCustomPlot(existing: CustomPlotDefinition?, template: CustomPlotDefinition? = null) {
        val source=existing?:template
        val name=styledInput("custom_plot.name").apply{setText(source?.name?.removePrefix("Example · ").orEmpty())}
        val group=groupSpinner(source?.groupId?:DEFAULT_SCRIPT_GROUP_ID)
        val engineNames=listOf("Auto","Observable Plot","D3.js");val engineValues=listOf("auto","observable","d3")
        val engine=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,engineNames);backgroundTintList=inputTint();setSelection(engineValues.indexOf(source?.engine?:"auto").coerceAtLeast(0))}
        val script=JavaScriptCodeEditor(this).apply{hint="custom_plot.javascript";setText(source?.script?:"const rows=helpers.rows(jsonFile); return Plot.plot({width:context.width,height:context.height,marks:[Plot.dot(rows,{x:(d,i)=>i,y:d=>helpers.number(d.PRICE),tip:true})]});")}
        var enabled=source?.enabled?:true
        val enabledButton=styledButton(if(enabled)"Enabled" else "Disabled").apply{setOnClickListener{enabled=!enabled;text=if(enabled)"Enabled" else "Disabled"}}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,dp(14),0);addView(infoText("Available: d3, Plot, aq, jsonFile, context, theme, helpers, and ENV. Custom plots receive Exvia semantic zoom automatically when possible.").apply{setTextColor(MUTED)},spacedMatchWidth(5));addView(name,spacedMatchWidth(5));addView(group,spacedMatchWidth(5));addView(engine,spacedMatchWidth(5));addView(script,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(390)));addView(enabledButton,spacedMatchWidth(5))}
        val dialog=AlertDialog.Builder(this).setTitle(if(existing!=null)"Edit custom plot" else "New custom plot").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val n=name.text.toString().trim();val code=script.text.toString().trim();if(n.isBlank()){name.error="Name is required";return@setOnClickListener};if(code.isBlank()){script.error="JavaScript is required";return@setOnClickListener}
            @Suppress("UNCHECKED_CAST") val groups=group.tag as List<ScriptGroupDefinition>;val gid=groups.getOrNull(group.selectedItemPosition)?.id?:DEFAULT_SCRIPT_GROUP_ID
            val next=CustomPlotDefinition(existing?.id?:UUID.randomUUID().toString(),n,code,engineValues[engine.selectedItemPosition],enabled,gid)
            customPlotsDraft=if(existing==null)(customPlotsDraft+next).toMutableList() else customPlotsDraft.map{if(it.id==existing.id)next else it}.toMutableList()
            persistCustomPlots("Custom plot auto-saved.");renderCustomStatSettings();dialog.dismiss()
        }}
        showDialog(dialog)
    }

    private fun showScriptGroupManager() {
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),0)}
        lateinit var dialog:AlertDialog
        fun rebuild(){
            list.removeAllViews(); list.addView(styledButton("+ New accordion").apply{setOnClickListener{promptEditScriptGroup(null){dialog.dismiss();showScriptGroupManager()}}},spacedMatchWidth(5))
            normalizedScriptGroups().forEach{group->
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
                row.addView(infoText(group.name).apply{setTextColor(if(group.id==DEFAULT_SCRIPT_GROUP_ID)MUTED else WHITE);setOnClickListener{if(group.id!=DEFAULT_SCRIPT_GROUP_ID)promptEditScriptGroup(group){dialog.dismiss();showScriptGroupManager()}}},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                if(group.id!=DEFAULT_SCRIPT_GROUP_ID) row.addView(TextView(this).apply{text="×";gravity=Gravity.CENTER;setTextColor(RED);AppFonts.apply(this,bold=true);setOnClickListener{
                    customMetricsDraft=customMetricsDraft.map{if(it.groupId==group.id)it.copy(groupId=DEFAULT_SCRIPT_GROUP_ID)else it}.toMutableList();customPlotsDraft=customPlotsDraft.map{if(it.groupId==group.id)it.copy(groupId=DEFAULT_SCRIPT_GROUP_ID)else it}.toMutableList();scriptGroupsDraft.removeAll{it.id==group.id};persistScriptGroups("Accordion removed; scripts moved to Default.");persistCustomMetrics("Metrics regrouped.");persistCustomPlots("Plots regrouped.");dialog.dismiss();showScriptGroupManager()
                }},LinearLayout.LayoutParams(dp(40),dp(32)))
                list.addView(row,matchWidth())
            }
        }
        dialog=AlertDialog.Builder(this).setTitle("Custom Accordions").setView(ScrollView(this).apply{addView(list,matchWidth())}).setNegativeButton("Close",null).create();rebuild();showDialog(dialog)
    }

    private fun promptEditScriptGroup(existing: ScriptGroupDefinition?, done:()->Unit) {
        val input=styledInput("accordion.name · use :: for nesting").apply{setText(existing?.name.orEmpty())}
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,dp(14),0);addView(infoText("Use :: to create nested Stat accordions, for example Stat+::Finance. Without :: the accordion is a top-level Stat sibling.").apply{setTextColor(MUTED)},spacedMatchWidth(5));addView(input,matchWidth())}
        val dialog=AlertDialog.Builder(this).setTitle(if(existing==null)"New Custom Accordion" else "Rename Custom Accordion").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val raw=input.text.toString().trim(); val parts=raw.split("::").map{it.trim()}
            if(parts.isEmpty()||parts.any{it.isBlank()}){input.error="Each :: path segment needs a name";return@setOnClickListener}
            val name=parts.joinToString("::")
            if(normalizedScriptGroups().any{it.id!=existing?.id&&it.name.equals(name,true)}){input.error="Name already exists";return@setOnClickListener}
            val next=ScriptGroupDefinition(existing?.id?:UUID.randomUUID().toString(),name)
            scriptGroupsDraft=if(existing==null)(scriptGroupsDraft+next).toMutableList() else scriptGroupsDraft.map{if(it.id==existing.id)next else it}.toMutableList()
            persistScriptGroups("Custom Accordion saved.");dialog.dismiss();done()
        }}
        showDialog(dialog)
    }


    private fun persistEnvironmentSnapshot(snapshot: JSONObject) {
        val encoded = snapshot.toString()
        if (encoded == lastEnvironmentSnapshot) return
        lastEnvironmentSnapshot = encoded
        val nextEnvironment = environmentVariablesDraft.map { definition ->
            if (!snapshot.has(definition.name)) definition else definition.copy(valueJson = jsonLiteral(snapshot.opt(definition.name)))
        }.toMutableList()
        val snapshotKeys = snapshot.keys()
        while (snapshotKeys.hasNext()) {
            val name = snapshotKeys.next()
            if (nextEnvironment.none { it.name.equals(name, true) }) {
                nextEnvironment += EnvironmentVariableDefinition(
                    id = UUID.randomUUID().toString(), name = name,
                    initializerScript = "return null;", valueJson = jsonLiteral(snapshot.opt(name)), enabled = true,
                )
            }
        }
        environmentVariablesDraft = nextEnvironment
        settings = settings.copy(environmentVariables = environmentVariablesDraft.toList())
        mainViewModel.updateStyleSettings(settings.copy(colorMappings = colorMappingsDraft.map(::resolveRuleEnv)))
        if (filterEnabled) applyFilterAndRender(false)
        if (flagEnabled) applyFlagAndRender(false)
        environmentSaveRunnable?.let(uiHandler::removeCallbacks)
        val save = Runnable { settingsViewModel.saveEnvironmentVariables(settings) }
        environmentSaveRunnable = save
        uiHandler.postDelayed(save, 700L)
    }

    private fun jsonLiteral(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject, is JSONArray -> value.toString()
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun persistEnvironmentVariables(message: String) {
        settings = settings.copy(environmentVariables = environmentVariablesDraft.toList())
        settingsViewModel.saveEnvironmentVariables(settings)
        statusText.text = message
    }

    private fun showEnvironmentManager() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        lateinit var dialog: AlertDialog
        fun rebuild() {
            root.removeAllViews()
            root.addView(infoText("ENV is Exvia's persistent JavaScript variable store, not Android/OS environment variables. Scripts can call ENV.name.get(), post(), put(), and delete().").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            root.addView(accordion("Built-in ENV examples", initiallyOpen = false) { box ->
                BuiltinExamples.environmentVariables.forEach { example ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(infoText(example.name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(TextView(this).apply { text = "Use"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { editEnvironmentVariable(null, example) { dialog.dismiss(); showEnvironmentManager() } } }, LinearLayout.LayoutParams(dp(52), dp(30)))
                    box.addView(row, matchWidth())
                }
            }, matchWidth())
            root.addView(styledButton("+ Add ENV variable").apply { setOnClickListener { editEnvironmentVariable(null, null) { dialog.dismiss(); showEnvironmentManager() } } }, spacedMatchWidth(5))
            environmentVariablesDraft.forEach { item ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(infoText("ENV.${item.name}").apply { setOnClickListener { editEnvironmentVariable(item, null) { dialog.dismiss(); showEnvironmentManager() } } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply { text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true); setOnClickListener { environmentVariablesDraft.removeAll { it.id == item.id }; persistEnvironmentVariables("ENV variable removed."); rebuild() } }, LinearLayout.LayoutParams(dp(40), dp(32)))
                root.addView(row, matchWidth())
            }
        }
        dialog = AlertDialog.Builder(this).setTitle("Variables / ENV").setView(ScrollView(this).apply { addView(root, matchWidth()) }).setNegativeButton("Close", null).create()
        rebuild(); showDialog(dialog)
    }

    private fun editEnvironmentVariable(existing: EnvironmentVariableDefinition?, template: EnvironmentVariableDefinition?, done: () -> Unit) {
        val source = existing ?: template
        val name = styledInput("env.name").apply { setText(source?.name.orEmpty()) }
        val script = JavaScriptCodeEditor(this).apply { hint = "env.initializer.js"; setText(source?.initializerScript ?: "return null;") }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("Initializer is JavaScript. Example usage elsewhere: ENV.budget.get('daily'), ENV.budget.put('daily', 60), ENV.budget.post({monthly:1800}), ENV.budget.delete('daily'). Runtime changes persist automatically.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(name, spacedMatchWidth(5)); addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "New ENV variable" else "Edit ENV variable").setView(body).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim(); if (n.isBlank() || !n.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { name.error = "Use a JavaScript identifier"; return@setOnClickListener }
            if (environmentVariablesDraft.any { it.id != existing?.id && it.name.equals(n, true) }) { name.error = "ENV name already exists"; return@setOnClickListener }
            val next = EnvironmentVariableDefinition(existing?.id ?: UUID.randomUUID().toString(), n, script.text.toString().trim().ifBlank { "return null;" }, existing?.valueJson ?: template?.valueJson ?: "null", true)
            environmentVariablesDraft = if (existing == null) (environmentVariablesDraft + next).toMutableList() else environmentVariablesDraft.map { if (it.id == existing.id) next else it }.toMutableList()
            persistEnvironmentVariables("ENV variable auto-saved."); dialog.dismiss(); done()
        } }
        showDialog(dialog)
    }

    private fun persistNotificationRules(message: String) {
        settings = settings.copy(notificationRules = notificationRulesDraft.toList())
        settingsViewModel.saveNotificationRules(settings); statusText.text = message
    }

    private fun showNotificationManager() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        lateinit var dialog: AlertDialog
        fun rebuild() {
            root.removeAllViews()
            root.addView(accordion("Built-in notification examples", initiallyOpen = false) { box ->
                BuiltinExamples.notificationRules.forEach { example ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    row.addView(infoText(example.name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(TextView(this).apply { text = "Use"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { editNotificationRule(null, example) { dialog.dismiss(); showNotificationManager() } } }, LinearLayout.LayoutParams(dp(52), dp(30)))
                    box.addView(row, matchWidth())
                }
            }, matchWidth())
            root.addView(styledButton("+ Add notification rule").apply { setOnClickListener { editNotificationRule(null, null) { dialog.dismiss(); showNotificationManager() } } }, spacedMatchWidth(5))
            notificationRulesDraft.forEach { rule ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(infoText("${rule.name} · ${rule.eventName}").apply { setTextColor(if (rule.enabled) WHITE else MUTED); setOnClickListener { editNotificationRule(rule, null) { dialog.dismiss(); showNotificationManager() } } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply { text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true); setOnClickListener { notificationRulesDraft.removeAll { it.id == rule.id }; persistNotificationRules("Notification rule removed."); rebuild() } }, LinearLayout.LayoutParams(dp(40), dp(32)))
                root.addView(row, matchWidth())
            }
        }
        dialog = AlertDialog.Builder(this).setTitle("Notifications").setView(ScrollView(this).apply { addView(root, matchWidth()) }).setNegativeButton("Close", null).create(); rebuild(); showDialog(dialog)
    }

    private fun editNotificationRule(existing: NotificationRule?, template: NotificationRule?, done: () -> Unit) {
        val source = existing ?: template
        val events = listOf("event.amend", "event.resync", "event.save")
        val name = styledInput("notification.name").apply { setText(source?.name?.removePrefix("Example · ").orEmpty()) }
        val event = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, events); backgroundTintList = inputTint(); setSelection(events.indexOf(source?.eventName ?: "event.amend").coerceAtLeast(0)) }
        val script = JavaScriptCodeEditor(this).apply { hint = "notification.javascript"; setText(source?.script ?: "return {notify:true,title:'Exvia',body:String(event.name)};") }
        var enabled = source?.enabled ?: true
        val enabledButton = styledButton(if (enabled) "Enabled" else "Disabled").apply { setOnClickListener { enabled = !enabled; text = if (enabled) "Enabled" else "Disabled" } }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0); addView(infoText("Available: event, metric(name), jsonFile, ENV, d3, Plot, aq, theme and helpers. Return {notify,title,body,severity:'red|normal',IS_TOAST:true|false}. IS_TOAST shows an in-app Android Toast; notify controls the system notification.").apply { setTextColor(MUTED) }, spacedMatchWidth(5)); addView(name, spacedMatchWidth(5)); addView(event, spacedMatchWidth(5)); addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300))); addView(enabledButton, spacedMatchWidth(5)) }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "New notification rule" else "Edit notification rule").setView(body).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val n = name.text.toString().trim(); if (n.isBlank()) { name.error = "Name required"; return@setOnClickListener }; val next = NotificationRule(existing?.id ?: UUID.randomUUID().toString(), n, events[event.selectedItemPosition], script.text.toString(), enabled); notificationRulesDraft = if (existing == null) (notificationRulesDraft + next).toMutableList() else notificationRulesDraft.map { if (it.id == existing.id) next else it }.toMutableList(); persistNotificationRules("Notification rule auto-saved."); dialog.dismiss(); done() } }
        showDialog(dialog)
    }

    private fun persistSchemaRules(message: String) { settings = settings.copy(schemaRules = schemaRulesDraft.toList()); settingsViewModel.saveSchemaRules(settings); schemaConfigSignature = ""; statusText.text = message; scheduleSchemaEvaluation(currentData) }
    private fun showSchemaRuleManager() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,dp(12),0) }; lateinit var dialog: AlertDialog
        fun rebuild(){ root.removeAllViews(); root.addView(accordion("Built-in schema examples", initiallyOpen=false){ box -> BuiltinExamples.schemaRules.forEach { e -> val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(infoText(e.name),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));row.addView(TextView(this).apply{text="Use";gravity=Gravity.CENTER;setTextColor(PRIMARY);AppFonts.apply(this);setOnClickListener{editSchemaRule(null,e){dialog.dismiss();showSchemaRuleManager()}}},LinearLayout.LayoutParams(dp(52),dp(30)));box.addView(row,matchWidth()) }},matchWidth());root.addView(styledButton("+ Add schema rule").apply{setOnClickListener{editSchemaRule(null,null){dialog.dismiss();showSchemaRuleManager()}}},spacedMatchWidth(5));schemaRulesDraft.forEach{r->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(infoText(r.name).apply{setTextColor(if(r.enabled)WHITE else MUTED);setOnClickListener{editSchemaRule(r,null){dialog.dismiss();showSchemaRuleManager()}}},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));row.addView(TextView(this).apply{text="×";gravity=Gravity.CENTER;setTextColor(RED);AppFonts.apply(this,bold=true);setOnClickListener{schemaRulesDraft.removeAll{it.id==r.id};persistSchemaRules("Schema rule removed.");rebuild()}},LinearLayout.LayoutParams(dp(40),dp(32)));root.addView(row,matchWidth())}}
        dialog=AlertDialog.Builder(this).setTitle("Key schema scripts").setView(ScrollView(this).apply{addView(root,matchWidth())}).setNegativeButton("Close",null).create();rebuild();showDialog(dialog)
    }
    private fun editSchemaRule(existing:SchemaRuleDefinition?,template:SchemaRuleDefinition?,done:()->Unit){val source=existing?:template;val name=styledInput("schema_rule.name").apply{setText(source?.name.orEmpty())};val script=JavaScriptCodeEditor(this).apply{setText(source?.script?:"return {};")};var enabled=source?.enabled?:true;val toggle=styledButton(if(enabled)"Enabled" else "Disabled").apply{setOnClickListener{enabled=!enabled;text=if(enabled)"Enabled" else "Disabled"}};val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,dp(14),0);addView(infoText("Per key, return any of DEFAULT_VALUE, HIDDEN, NUMBER_ONLY_KEYPAD, PLACEHOLDER, AUTO_COMPLETION, AUTO_COMPLETION_PARSING, BOOLEAN_01. Available: key, rows, ENV, context.").apply{setTextColor(MUTED)},spacedMatchWidth(5));addView(name,spacedMatchWidth(5));addView(script,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(300)));addView(toggle,spacedMatchWidth(5))};val dialog=AlertDialog.Builder(this).setTitle(if(existing==null)"New schema rule" else "Edit schema rule").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val n=name.text.toString().trim();if(n.isBlank()){name.error="Name required";return@setOnClickListener};val next=SchemaRuleDefinition(existing?.id?:UUID.randomUUID().toString(),n,script.text.toString(),enabled);schemaRulesDraft=if(existing==null)(schemaRulesDraft+next).toMutableList() else schemaRulesDraft.map{if(it.id==existing.id)next else it}.toMutableList();persistSchemaRules("Schema rule auto-saved.");dialog.dismiss();done()}};showDialog(dialog)}

    private fun persistMetricColorMappings(message:String){settings=settings.copy(metricColorMappings=metricColorMappingsDraft.toList());settingsViewModel.saveMetricColorMappings(settings);metricColorSignature="";statusText.text=message;renderStats(mainViewModel.state.value.visibleData)}
    private fun showMetricColorManager(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),0)};lateinit var dialog:AlertDialog;fun rebuild(){root.removeAllViews();root.addView(accordion("Built-in metric color examples",initiallyOpen=false){box->BuiltinExamples.metricColorRules.forEach{e->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(infoText(e.name),LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));row.addView(TextView(this).apply{text="Use";gravity=Gravity.CENTER;setTextColor(PRIMARY);AppFonts.apply(this);setOnClickListener{editMetricColorRule(null,e){dialog.dismiss();showMetricColorManager()}}},LinearLayout.LayoutParams(dp(52),dp(30)));box.addView(row,matchWidth())}},matchWidth());root.addView(styledButton("+ Add metric color rule").apply{setOnClickListener{editMetricColorRule(null,null){dialog.dismiss();showMetricColorManager()}}},spacedMatchWidth(5));metricColorMappingsDraft.forEach{r->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(infoText("${r.name} · ${r.metricName}").apply{setOnClickListener{editMetricColorRule(r,null){dialog.dismiss();showMetricColorManager()}}},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));row.addView(TextView(this).apply{text="×";gravity=Gravity.CENTER;setTextColor(RED);AppFonts.apply(this,bold=true);setOnClickListener{metricColorMappingsDraft.removeAll{it.id==r.id};persistMetricColorMappings("Metric color rule removed.");rebuild()}},LinearLayout.LayoutParams(dp(40),dp(32)));root.addView(row,matchWidth())}};dialog=AlertDialog.Builder(this).setTitle("Metric Color Mapping").setView(ScrollView(this).apply{addView(root,matchWidth())}).setNegativeButton("Close",null).create();rebuild();showDialog(dialog)}
    private fun editMetricColorRule(existing:MetricColorRule?,template:MetricColorRule?,done:()->Unit){val source=existing?:template;val name=styledInput("metric_color.name").apply{setText(source?.name.orEmpty())};val metric=styledInput("metric.name or *").apply{setText(source?.metricName?:"*")};val script=JavaScriptCodeEditor(this).apply{setText(source?.script?:"const n=Number(metric.value); return {key:n<0?theme.negative:theme.positive,value:n<0?theme.negative:theme.positive};")};var enabled=source?.enabled?:true;val toggle=styledButton(if(enabled)"Enabled" else "Disabled").apply{setOnClickListener{enabled=!enabled;text=if(enabled)"Enabled" else "Disabled"}};val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,dp(14),0);addView(infoText("Return {key:'#RRGGBB', value:'#RRGGBB'}. metric.name/value are exposed; metrics('Mean − Median gap') can read another rendered metric. ENV and theme are also available.").apply{setTextColor(MUTED)},spacedMatchWidth(5));addView(name,spacedMatchWidth(5));addView(metric,spacedMatchWidth(5));addView(script,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(260)));addView(toggle,spacedMatchWidth(5))};val dialog=AlertDialog.Builder(this).setTitle(if(existing==null)"New metric color rule" else "Edit metric color rule").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{val n=name.text.toString().trim();val m=metric.text.toString().trim();if(n.isBlank()||m.isBlank()){name.error="Name and metric are required";return@setOnClickListener};val next=MetricColorRule(existing?.id?:UUID.randomUUID().toString(),n,m,script.text.toString(),enabled);metricColorMappingsDraft=if(existing==null)(metricColorMappingsDraft+next).toMutableList() else metricColorMappingsDraft.map{if(it.id==existing.id)next else it}.toMutableList();persistMetricColorMappings("Metric color rule auto-saved.");dialog.dismiss();done()}};showDialog(dialog)}

    private fun persistCustomMetricInputs() { settings=settings.copy(customMetricInputs=customMetricInputsDraft.toMap()); settingsViewModel.saveCustomMetricInputs(settings) }

    private fun scheduleSchemaEvaluation(data:TableData){
        val active=schemaRulesDraft.filter{it.enabled}; if(active.isEmpty()||data.keys.isEmpty()){if(schemaConfigCache.isNotEmpty()){schemaConfigCache=emptyMap();renderDynamicForm(data)};return}
        val signature=data.keys.joinToString("|")+":"+active.joinToString("|"){it.id+it.script.hashCode()};if(signature==schemaConfigSignature)return;schemaConfigSignature=signature
        fieldSchemaEngine.evaluate(active,data){result->runOnUiThread{result.onSuccess{configs->val preserved=formInputs.mapValues{it.value.text.toString()};schemaConfigCache=configs;renderDynamicForm(data,preserved)}}}
    }

    private fun applySchemaOutputTransforms(values:Map<String,String>):LinkedHashMap<String,String> = linkedMapOf<String,String>().apply { values.forEach { (key,value) -> val config=schemaConfigCache.entries.firstOrNull{it.key.equals(key,true)}?.value; put(key, if(config?.boolean01==true) when(value.trim()){ "1"->"true";"0"->"false";else->value } else value) } }

    private fun triggerAutomationEvent(name:String,payload:Map<String,String>){
        val rules=notificationRulesDraft.filter{it.enabled&&it.eventName==name};if(rules.isEmpty())return
        val data=mainViewModel.state.value.visibleData;val json=effectiveJsonFile(data);val metrics=notificationMetricMap(data)
        val event=JSONObject().apply{put("name",name);payload.forEach{(k,v)->put(k,v)}}
        rules.forEach { rule ->
            notificationScriptEngine.evaluate(rule, event, metrics, json) { result ->
                runOnUiThread {
                    result.onSuccess { n ->
                        if (n.isToast) Toast.makeText(this, listOf(n.title, n.body).filter { it.isNotBlank() }.joinToString(": "), Toast.LENGTH_LONG).show()
                        if (n.notify) notificationDispatcher.post(n.title, n.body, n.severity)
                    }
                }
            }
        }
    }

    private fun notificationMetricMap(data:TableData):Map<String,Any?>{
        val out=linkedMapOf<String,Any?>(); val money=data.moneyKey?:settings.detectMoneyKey(data.keys); if(money!=null){val stats=statisticsViewModel.keyStats(data.rows.map{it.values[money].orEmpty()});out["Mean"]=stats.mean;out["Median"]=stats.median;out["STDV"]=stats.stdv;out["Sum"]=stats.sum;out["Minimum"]=stats.minimum;out["Maximum"]=stats.maximum;out["Q1"]=stats.q1;out["Q3"]=stats.q3;out["IQR"]=stats.iqr;out["Skew"]=stats.skew;out["Kurtosis"]=stats.kurtosis}
        statisticsViewModel.financeStats(data)?.let{f->out["Net Cash Flow"]=f.netCashFlow;out["Savings Rate"]=f.savingsRate;out["Expense Ratio"]=f.expenseRatio;out["Total Income"]=f.totalIncome;out["Total Expenses"]=f.totalExpenses}
        return out
    }

    private fun persistScriptGroups(message:String){settings=settings.copy(scriptGroups=normalizedScriptGroups());settingsViewModel.saveScriptGroups(settings);statusText.text=message;renderCustomStatSettings()}

    private fun persistCustomMetrics(message: String) {
        settings = settings.copy(customMetrics = customMetricsDraft.toList())
        settingsViewModel.saveCustomMetrics(settings)
        statusText.text = message
    }

    private fun persistCustomPlots(message: String) {
        settings = settings.copy(customPlots = customPlotsDraft.toList())
        settingsViewModel.saveCustomPlots(settings)
        statusText.text = message
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
            plotSelectionSetting to "Plot selection", plotTooltipBackgroundSetting to "Plot tooltip background",
            plotTooltipTextSetting to "Plot tooltip text", plotTooltipBorderSetting to "Plot tooltip border",
        )
        plotThemeInputs.forEach { (input, label) ->
            if (!PlotTheme.isValid(input.text.toString().trim())) {
                input.error = "$label must be #RRGGBB or #AARRGGBB"
                invalid = true
            }
        }
        val uiScale = uiScaleSetting.text.toString().trim().toDoubleOrNull()
        val textScale = textScaleSetting.text.toString().trim().toDoubleOrNull()
        val rowsPerPage = rowsPerPageSetting.text.toString().trim().toIntOrNull()
        val undoHistoryLimit = undoHistoryLimitSetting.text.toString().trim().toIntOrNull()
        if (uiScale == null || uiScale !in 0.70..1.60) {
            uiScaleSetting.error = "Use a value from 0.70 to 1.60"
            invalid = true
        }
        if (textScale == null || textScale !in 0.70..1.80) {
            textScaleSetting.error = "Use a value from 0.70 to 1.80"
            invalid = true
        }
        if (rowsPerPage == null || rowsPerPage !in 1..500) {
            rowsPerPageSetting.error = "Use a whole number from 1 to 500"
            invalid = true
        }
        if (undoHistoryLimit == null || undoHistoryLimit !in 1..50) {
            undoHistoryLimitSetting.error = "Use a whole number from 1 to 50"
            invalid = true
        }
        if (invalid) return

        val activeUiThemeId = uiThemeChoices.getOrNull(themeSpinner.selectedItemPosition)?.id
            ?: "builtin:${ThemePreset.DEFAULT.id}"
        val activePlotThemeId = plotThemeChoices.getOrNull(plotThemeSpinner.selectedItemPosition)?.id
            ?: "builtin:black"
        val selectedTheme = if (activeUiThemeId.startsWith("builtin:")) {
            ThemePreset.fromId(activeUiThemeId.removePrefix("builtin:"))
        } else {
            ThemePreset.DEFAULT
        }
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
            tickerColors = settings.tickerColors,
            flaggingRules = flaggingRulesDraft.toList(),
            colorMappings = colorMappingsDraft.toList(),
            plotColumns = settingsViewModel.parseColumnList(plotColumnsSetting.text.toString()),
            financeColumns = settingsViewModel.parseColumnList(financeColumnsSetting.text.toString()),
            customMetrics = customMetricsDraft.toList(),
            customPlots = customPlotsDraft.toList(),
            scriptGroups = scriptGroupsDraft.toList(),
            environmentVariables = environmentVariablesDraft.toList(),
            notificationRules = notificationRulesDraft.toList(),
            schemaRules = schemaRulesDraft.toList(),
            metricColorMappings = metricColorMappingsDraft.toList(),
            customMetricInputs = customMetricInputsDraft.toMap(),
            fileScripts = fileScriptsDraft.toList(),
            imaginaryFields = imaginaryFieldsDraft.toList(),
            uiScale = uiScale!!,
            textScale = textScale!!,
            rowsPerPage = rowsPerPage!!,
            undoHistoryLimit = undoHistoryLimit!!,
            automaticAmend = automaticAmendSpinner.selectedItemPosition == 0,
            themePreset = selectedTheme,
            palette = currentUiPalette(),
            plotTheme = currentPlotTheme(),
            customUiThemes = customUiThemesDraft.toList(),
            activeUiThemeId = activeUiThemeId,
            customPlotThemes = customPlotThemesDraft.toList(),
            activePlotThemeId = activePlotThemeId,
        )
        setBusy(true, "Saving settings and syncing ${RepoConfig.CONFIG_PATH}…")
        settingsViewModel.saveSettings(
            next = next,
            enteredToken = tokenSetting.text.toString().trim().ifBlank { null },
            snippets = filterSnippets.toList(),
            developerMode = developerMode,
        )
    }

    private fun showReportDialog() {
        if (settingsViewModel.token() == null) {
            AlertDialog.Builder(this)
                .setTitle("GitHub PAT required")
                .setMessage("A GitHub PAT with Issues: write permission is required to submit a report.")
                .setNegativeButton("Close", null)
                .create().also { showDialog(it) }
            return
        }
        val categories = listOf("Bug", "Enhancement", "Feature")
        val issueLabels = listOf("#bug", "#ench", "#feat")
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            backgroundTintList = inputTint()
        }
        val titleInput = styledInput("report.title").apply {
            isSingleLine = true
            hint = "Issue title"
        }
        val descriptionInput = styledInput("report.description").apply {
            isSingleLine = false
            minLines = 6
            gravity = Gravity.TOP
            hint = "Describe the problem or requested change"
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(BLACK)
            addView(infoText("Reports are created as GitHub Issues in ${settings.owner}/Exvia. The selected type is attached as an issue label.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(typeSpinner, spacedMatchWidth(6))
            addView(titleInput, spacedMatchWidth(6))
            addView(descriptionInput, matchWidth())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Report")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val issueTitle = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                if (issueTitle.isBlank()) { titleInput.error = "Enter an issue title"; return@setOnClickListener }
                if (description.isBlank()) { descriptionInput.error = "Describe the report"; return@setOnClickListener }
                val selectedIndex = typeSpinner.selectedItemPosition.coerceIn(categories.indices)
                dialog.dismiss()
                setBusy(true, "Creating issue in ${settings.owner}/Exvia…")
                settingsViewModel.submitReport(
                    settings = settings,
                    title = issueTitle,
                    description = description,
                    label = issueLabels[selectedIndex],
                    classification = categories[selectedIndex],
                    developerMode = developerMode,
                )
            }
        }
        showDialog(dialog)
    }

    private fun promptRepositoryInitialization() {
        if (settingsViewModel.token() == null) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("GitHub repository")
            .setMessage("Is your expense GitHub repository already initialized?")
            .setPositiveButton("Yes") { _, _ ->
                settingsViewModel.markRepositoryInitializationAsked()
                if (settings.isConfigured()) mainViewModel.synchronize(settings) else {
                    statusText.text = "Set the existing repository details in GitHub Settings."
                    drawerRoot.openDrawer()
                }
            }
            .setNegativeButton("No") { _, _ -> showRepositoryCreationDialog() }
            .create()
        showDialog(dialog)
    }

    private fun showRepositoryCreationDialog() {
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
                settingsViewModel.createRepository(settings, u, r, b, f, d)
            }
        }
        showDialog(dialog)
    }

    private fun showGitPanel() {
        if (requireToken() == null) return
        gitHistoryPage = 1
        val stageStatus = infoText(
            if (hasStagedChanges) "Working tree: STAGED · ${selectedPath?.substringAfterLast('/') ?: "selected file"}"
            else "Working tree: CLEAN · ${settings.branch}"
        ).apply {
            setTextColor(if (hasStagedChanges) PRIMARY else MUTED)
            AppFonts.apply(this, bold = hasStagedChanges, textScale = settings.textScale)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setBackgroundColor(BLACK)
            addView(stageStatus, spacedMatchWidth(7))
            addView(infoText("GitHub history uses the configured ${settings.branch} branch. Pull discards a local stage after confirmation; Amend commits and pushes the staged Table working copy.").apply {
                setTextColor(MUTED)
            }, spacedMatchWidth(8))
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(styledButton("Re-sync · Pull").apply {
            setOnClickListener {
                gitDialog?.dismiss()
                refreshFilesAndTable()
            }
        }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(3) })
        actions.addView(styledButton("Amend · Stage → Commit → Push").apply {
            isEnabled = hasStagedChanges
            alpha = if (hasStagedChanges) 1f else 0.38f
            setOnClickListener {
                if (hasStagedChanges) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Amend staged changes?")
                        .setMessage("Commit and push the selected file's local staged Table changes to ${settings.branch}?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Amend") { _, _ ->
                            gitDialog?.dismiss()
                            mainViewModel.amendStaged(settings)
                        }
                        .create().also { showDialog(it) }
                }
            }
        }, LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginStart = dp(3) })
        body.addView(actions, spacedMatchWidth(9))

        body.addView(infoText("Commits").apply {
            setTextColor(WHITE)
            AppFonts.apply(this, bold = true, textScale = settings.textScale)
        }, spacedMatchWidth(4))

        gitCommitList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        body.addView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BLACK)
            addView(gitCommitList, matchWidth())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))

        val pager = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        gitPreviousButton = styledButton("Previous").apply {
            setOnClickListener {
                if (gitHistoryPage > 1 && !busy) mainViewModel.loadGitHistory(settings, gitHistoryPage - 1)
            }
        }
        gitPageText = TextView(this).apply {
            text = "Page 1"
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            AppFonts.apply(this, bold = true, textScale = settings.textScale)
        }
        gitNextButton = styledButton("Next").apply {
            setOnClickListener { if (!busy) mainViewModel.loadGitHistory(settings, gitHistoryPage + 1) }
        }
        pager.addView(gitPreviousButton, LinearLayout.LayoutParams(0, dp(31), 1f).apply { marginEnd = dp(4) })
        pager.addView(gitPageText, LinearLayout.LayoutParams(0, dp(31), 0.8f))
        pager.addView(gitNextButton, LinearLayout.LayoutParams(0, dp(31), 1f).apply { marginStart = dp(4) })
        body.addView(pager, spacedMatchWidth(5))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Git · ${settings.owner}/${settings.repo}")
            .setView(body)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            if (gitDialog === dialog) {
                gitDialog = null
                gitCommitList = null
                gitPageText = null
                gitPreviousButton = null
                gitNextButton = null
            }
        }
        gitDialog = dialog
        showDialog(dialog)
        mainViewModel.loadGitHistory(settings, 1)
    }

    private fun renderGitHistory(page: CommitPage) {
        if (gitDialog?.isShowing != true) return
        gitHistoryPage = page.page
        gitPageText?.text = "Page ${page.page}"
        gitPreviousButton?.isEnabled = page.hasPrevious
        gitPreviousButton?.alpha = if (page.hasPrevious) 1f else 0.38f
        gitNextButton?.isEnabled = page.hasNext
        gitNextButton?.alpha = if (page.hasNext) 1f else 0.38f
        val list = gitCommitList ?: return
        list.removeAllViews()
        if (page.commits.isEmpty()) {
            list.addView(infoText("No commits found on this page.").apply { setTextColor(MUTED) }, spacedMatchWidth(8))
            return
        }
        page.commits.forEachIndexed { index, commit ->
            val isHead = page.page == 1 && index == 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(5), dp(6), dp(5), dp(7))
                setBackgroundColor(BLACK)
            }
            row.addView(TextView(this).apply {
                text = buildString {
                    if (isHead) append("HEAD · ")
                    append(commit.shortSha)
                    append(" · ")
                    append(commit.message.ifBlank { "(no commit message)" })
                }
                setTextColor(if (isHead) PRIMARY else WHITE)
                AppFonts.apply(this, bold = true, textScale = settings.textScale)
            }, matchWidth())
            row.addView(TextView(this).apply {
                text = "${commit.author} · ${commit.date.ifBlank { "unknown date" }}"
                setTextColor(MUTED)
                textSize = 11f
                AppFonts.apply(this, textScale = settings.textScale)
            }, matchWidth())
            if (!isHead) {
                row.addView(styledButton("Revert repository to ${commit.shortSha}", accent = SECONDARY).apply {
                    setOnClickListener {
                        val stagedWarning = if (hasAnyStagedChanges) "\n\nAll local staged Table changes in this repository will be discarded." else ""
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Revert to ${commit.shortSha}?")
                            .setMessage("This creates a new commit whose repository tree matches ${commit.shortSha}; existing history is preserved.$stagedWarning")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Revert") { _, _ ->
                                gitDialog?.dismiss()
                                mainViewModel.revertToCommit(settings, commit.sha)
                            }
                            .create().also { showDialog(it) }
                    }
                }, spacedMatchWidth(4))
            }
            list.addView(row, matchWidth())
            if (index != page.commits.lastIndex) list.addView(View(this).apply { setBackgroundColor(SURFACE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
    }

    private fun refreshFilesAndTable() {
        if (!hasAnyStagedChanges) {
            mainViewModel.synchronize(settings, discardStaged = true)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pull from ${settings.branch}?")
            .setMessage("There are local staged Table changes in this repository. Re-sync/Pull will discard all local stages and replace them with GitHub's ${settings.branch} branch.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Discard & Pull") { _, _ -> mainViewModel.synchronize(settings, discardStaged = true) }
            .create().also { showDialog(it) }
    }

    private fun refreshSelected(successMessage: String? = null, forceNetwork: Boolean = false) {
        val path = selectedPath ?: return
        mainViewModel.selectFile(settings, path, forceNetwork)
    }

    private fun applyFilterAndRender(showStatus: Boolean) {
        mainViewModel.setFilter(filterEnabled, resolveEnvInQuery(filterQuery), announce = showStatus)
    }

    private fun applyFlagAndRender(showStatus: Boolean) {
        mainViewModel.setFlag(flagEnabled, resolveEnvInQuery(flagQuery), selectedFlagRule?.let(::resolveRuleEnv), announce = showStatus)
    }

    private fun initializeEnvironmentRuntime() {
        val payload = JSONObject().apply { put("rows", JSONArray()); put("tasks", JSONArray()) }
        plotRuntime.evaluateFormulas(payload) { /* environment snapshot is consumed by PlotWebRuntime */ }
    }

    private fun resolveEnvInQuery(query: String): String {
        val regex = Regex("""ENV\.([A-Za-z_][A-Za-z0-9_]*)\.get\((?:['"]([^'"]*)['"])?\)""")
        return regex.replace(query) { match ->
            val name = match.groupValues[1]
            val path = match.groupValues.getOrNull(2).orEmpty()
            val definition = environmentVariablesDraft.firstOrNull { it.name.equals(name, true) } ?: return@replace "NULL"
            val root = runCatching { org.json.JSONTokener(definition.valueJson).nextValue() }.getOrNull()
            var value: Any? = root
            if (path.isNotBlank()) path.split('.').filter { it.isNotBlank() }.forEach { part ->
                value = when (val current = value) {
                    is JSONObject -> current.opt(part)
                    is JSONArray -> part.toIntOrNull()?.let { current.opt(it) }
                    else -> null
                }
            }
            when (value) {
                null, JSONObject.NULL -> "NULL"
                is Number, is Boolean -> value.toString()
                else -> "'${value.toString().replace("'", "''")}'"
            }
        }
    }

    private fun resolveEnvInScript(script: String): String {
        val regex = Regex("""ENV\.([A-Za-z_][A-Za-z0-9_]*)\.get\((?:['"]([^'"]*)['"])?\)""")
        return regex.replace(script) { match ->
            val name = match.groupValues[1]; val path = match.groupValues.getOrNull(2).orEmpty()
            val definition = environmentVariablesDraft.firstOrNull { it.name.equals(name, true) } ?: return@replace "null"
            var value: Any? = runCatching { org.json.JSONTokener(definition.valueJson).nextValue() }.getOrNull()
            if (path.isNotBlank()) path.split('.').filter { it.isNotBlank() }.forEach { part -> value = when (val c=value) { is JSONObject -> c.opt(part); is JSONArray -> part.toIntOrNull()?.let { c.opt(it) }; else -> null } }
            jsonLiteral(value)
        }
    }

    private fun resolveRuleEnv(rule: TableStyleRule): TableStyleRule = rule.copy(
        query = resolveEnvInQuery(rule.query),
        foregroundScript = resolveEnvInScript(rule.foregroundScript),
        backgroundScript = resolveEnvInScript(rule.backgroundScript),
        contentScript = resolveEnvInScript(rule.contentScript),
    )

    private fun toggleQueryMode() {
        queryMode = if (queryMode == TableQueryMode.FILTERING) TableQueryMode.FLAGGING else TableQueryMode.FILTERING
        mainViewModel.setQueryMode(queryMode)
        suppressQueryInputChange = true
        filterInput.setText(if (queryMode == TableQueryMode.FILTERING) filterQuery else flagQuery)
        suppressQueryInputChange = false
        updateQueryControls()
        Toast.makeText(this, if (queryMode == TableQueryMode.FILTERING) "Filtering mode" else "Flagging mode", Toast.LENGTH_SHORT).show()
    }

    private fun updateFilterToggle() = updateQueryControls()

    private fun updateQueryControls() {
        if (!::filterToggle.isInitialized) return
        val enabled = if (queryMode == TableQueryMode.FILTERING) filterEnabled else flagEnabled
        val modeName = if (queryMode == TableQueryMode.FILTERING) "Filter" else "Flag"
        filterToggle.text = "$modeName ${if (enabled) "ON" else "OFF"}"
        filterToggle.setTextColor(if (enabled) PRIMARY else MUTED)
        filterToggle.background = if (enabled) activeButtonBackground(PRIMARY) else inactiveActionBackground(PRIMARY)
        if (::filterInput.isInitialized) filterInput.hint = "SELECT * WHERE …"
        if (::filteringMethodButton.isInitialized) {
            filteringMethodButton.text = selectedFlagRule?.name?.takeIf { queryMode == TableQueryMode.FLAGGING }
                ?: if (queryMode == TableQueryMode.FILTERING) "Filtering method" else "Flagging method"
        }
    }

    private fun renderDynamicForm(data: TableData, preserved: Map<String, String> = emptyMap()) {
        dynamicForm.removeAllViews()
        formInputs.clear()
        if (data.keys.isEmpty()) {
            dynamicForm.addView(TextView(this).apply {
                text = "No fields inferred yet. Use + Add field / + Add imaginary field, or select a JSON file containing at least one object."
                setTextColor(MUTED)
                setPadding(dp(8), dp(8), dp(8), dp(10))
                AppFonts.apply(this)
            }, matchWidth())
            return
        }
        for (key in data.keys) {
            if (imaginaryKeys.any { it.equals(key, true) }) continue
            val config = schemaConfigCache.entries.firstOrNull { it.key.equals(key, true) }?.value
            if (config?.hidden == true) continue
            val initial = preserved[key] ?: when {
                key == data.dateKey -> currentDateTime()
                !config?.defaultValue.isNullOrBlank() -> config?.defaultValue.orEmpty()
                else -> ""
            }
            val input = inputForKey(key, initial, data)
            formInputs[key] = input
            dynamicForm.addView(formulaInputRow(key, input), spacedMatchWidth(4))
        }
    }

    private fun inputForKey(key: String, value: String, data: TableData): EditText {
        val config = schemaConfigCache.entries.firstOrNull { it.key.equals(key, true) }?.value
        var suggestions = if (config?.autoCompletion == false) emptyList() else suggestionsForKey(key, data)
        suggestions = when (config?.autoCompletionParsing?.lowercase()) {
            "uppercase" -> suggestions.map { it.uppercase() }.distinct()
            "lowercase" -> suggestions.map { it.lowercase() }.distinct()
            else -> suggestions
        }
        val placeholder = config?.placeholder?.takeIf { it.isNotBlank() } ?: "$key (optional)"
        val configuredInputType = if (config?.numberOnlyKeypad == true) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        } else InputType.TYPE_CLASS_TEXT
        return if (key == data.tagsKey && config?.numberOnlyKeypad != true) {
            MultiAutoCompleteTextView(this).apply {
                hint = placeholder; inputType = configuredInputType; isSingleLine = true; threshold = 1
                setTokenizer(MultiAutoCompleteTextView.CommaTokenizer()); setTextColor(WHITE); setHintTextColor(MUTED)
                backgroundTintList = inputTint(); setPadding(dp(8), dp(5), dp(8), dp(5)); minHeight = dp(46); AppFonts.apply(this)
                if (config?.autoCompletion != false) setAdapter(suggestionAdapter(suggestions)); setText(value)
            }
        } else {
            AutoCompleteTextView(this).apply {
                hint = placeholder; inputType = configuredInputType; isSingleLine = true; threshold = 1
                setTextColor(WHITE); setHintTextColor(MUTED); backgroundTintList = inputTint(); setPadding(dp(8), dp(5), dp(8), dp(5)); minHeight = dp(46); AppFonts.apply(this)
                if (config?.autoCompletion != false) setAdapter(suggestionAdapter(suggestions)); setText(value)
            }
        }
    }

    private fun formulaInputRow(key: String, input: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@MainActivity).apply {
            text = "ƒx"
            gravity = Gravity.CENTER
            setTextColor(PRIMARY)
            setPadding(dp(4), 0, dp(4), 0)
            background = inactiveActionBackground(PRIMARY)
            AppFonts.apply(this, bold = true, textScale = settings.textScale)
            tooltipController.attachHold(this, { "Formula for '$key'. JavaScript starts with ${xyz.x3ofiz4.exvia.domain.service.FormulaSupport.JS_PREFIX}; SQLite scalar formulas start with ${xyz.x3ofiz4.exvia.domain.service.FormulaSupport.SQL_PREFIX}." })
            setOnClickListener { showFieldFormulaEditor(key, input) }
        }, LinearLayout.LayoutParams(dp(42), dp(38)).apply { marginStart = dp(4) })
    }

    private fun showFieldFormulaEditor(key: String, target: EditText) {
        val parsed = xyz.x3ofiz4.exvia.domain.service.FormulaSupport.parse(target.text.toString())
        val languages = listOf("JavaScript", "SQLite")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, languages)
            backgroundTintList = inputTint()
            setSelection(if (parsed.kind == xyz.x3ofiz4.exvia.domain.service.FormulaSupport.Kind.SQLITE) 1 else 0)
        }
        val editor = JavaScriptCodeEditor(this).apply {
            hint = "Formula for $key"
            setText(when (parsed.kind) {
                xyz.x3ofiz4.exvia.domain.service.FormulaSupport.Kind.JAVASCRIPT,
                xyz.x3ofiz4.exvia.domain.service.FormulaSupport.Kind.SQLITE -> parsed.body
                else -> ""
            })
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("JavaScript exposes row, table, field, index, jsonFile, d3, Plot, aq, theme, helpers, context and ENV. SQLite uses SELECT <column|literal> [WHERE ...] with the same WHERE syntax as Filtering." ).apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(spinner, spacedMatchWidth(5))
            addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)))
        }
        val dialog = AlertDialog.Builder(this).setTitle("ƒx · $key").setView(body)
            .setNeutralButton("Clear formula", null).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { target.setText(""); dialog.dismiss() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = editor.text.toString().trim()
                if (code.isBlank()) { editor.error = "Enter a formula"; return@setOnClickListener }
                target.setText(if (spinner.selectedItemPosition == 1) {
                    xyz.x3ofiz4.exvia.domain.service.FormulaSupport.SQL_PREFIX + code
                } else {
                    xyz.x3ofiz4.exvia.domain.service.FormulaSupport.JS_PREFIX + code
                })
                dialog.dismiss()
            }
        }
        showDialog(dialog)
    }

    private fun coreTableData(): TableData {
        val keys = currentData.keys.filterNot { key -> imaginaryKeys.any { it.equals(key, true) } }
        val rows = currentData.rows.map { row ->
            row.copy(values = LinkedHashMap(row.values.filterKeys { key -> keys.any { it.equals(key, true) } }))
        }
        return currentData.copy(
            keys = keys,
            rows = rows,
            dateKey = settings.detectDateKey(keys),
            moneyKey = settings.detectMoneyKey(keys),
            tickerKey = settings.detectTickerKey(keys),
            tagsKey = settings.detectTagsKey(keys),
        )
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
                    settingsViewModel.saveSnippets(filterSnippets)
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
                settingsViewModel.saveSnippets(filterSnippets)
                dialog.dismiss(); showFilterSnippetManager()
            }
        }
        showDialog(dialog)
    }

    private fun showFlaggingRuleManager() {
        var managerDialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(BLACK)
        }
        list.addView(infoText("Flagging never removes rows. The syntax selects matching rows; .fore, .back, and .content alter the full MATCHING_ROW or a named cell.").apply {
            setTextColor(MUTED)
        }, spacedMatchWidth(6))
        list.addView(accordion("Showcase examples", initiallyOpen = false) { examples ->
            BuiltinExamples.flaggingRules.forEach { rule ->
                examples.addView(TextView(this).apply {
                    text = "${rule.name}\n${rule.query}"
                    maxLines = 3
                    textSize = 11.5f
                    setTextColor(WHITE)
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    AppFonts.apply(this, textScale = settings.textScale)
                    setOnClickListener { selectFlaggingRule(rule); managerDialog?.dismiss() }
                }, spacedMatchWidth(3))
            }
        }, spacedMatchWidth(5))

        list.addView(infoText("Saved flagging methods").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(3))
        if (flaggingRulesDraft.isEmpty()) list.addView(infoText("No saved flagging methods.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
        flaggingRulesDraft.forEach { rule ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = "${if (rule.enabled) "●" else "○"} ${rule.name}\n${rule.query}"
                maxLines = 3
                textSize = 11.5f
                setTextColor(if (rule.enabled) WHITE else MUTED)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                AppFonts.apply(this, textScale = settings.textScale)
                setOnClickListener { selectFlaggingRule(rule); managerDialog?.dismiss() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (rule.enabled) "On" else "Off"; gravity = Gravity.CENTER; setTextColor(PRIMARY)
                AppFonts.apply(this, textScale = settings.textScale)
                setOnClickListener {
                    flaggingRulesDraft = flaggingRulesDraft.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }.toMutableList()
                    persistTableRules("Flagging method updated.")
                    managerDialog?.dismiss(); showFlaggingRuleManager()
                }
            }, LinearLayout.LayoutParams(dp(42), dp(34)))
            row.addView(TextView(this).apply {
                text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this)
                setOnClickListener { managerDialog?.dismiss(); editTableStyleRule(rule, mapping = false) }
            }, LinearLayout.LayoutParams(dp(48), dp(34)))
            row.addView(TextView(this).apply {
                text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true)
                setOnClickListener {
                    flaggingRulesDraft.removeAll { it.id == rule.id }
                    if (selectedFlagRule?.id == rule.id) { selectedFlagRule = null; flagEnabled = false }
                    persistTableRules("Flagging method removed.")
                    managerDialog?.dismiss(); showFlaggingRuleManager()
                }
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            list.addView(row, matchWidth())
        }
        list.addView(styledButton("+ New flagging method").apply {
            setOnClickListener { managerDialog?.dismiss(); editTableStyleRule(null, mapping = false) }
        }, spacedMatchWidth(5))
        managerDialog = AlertDialog.Builder(this).setTitle("Flagging methods").setView(ScrollView(this).apply { addView(list, matchWidth()) }).setNegativeButton("Close", null).create()
        showDialog(managerDialog!!)
    }

    /** Regular-mode selector: names are visible while SQL/style implementation stays hidden. */
    private fun showFlaggingMethodManager() {
        var dialog: AlertDialog? = null
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(4)); setBackgroundColor(BLACK) }
        list.addView(infoText("Choose a flagging method. Matching rows remain in the table and only their presentation changes.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
        list.addView(accordion("Showcase methods", initiallyOpen = false) { body ->
            BuiltinExamples.flaggingRules.forEach { rule -> body.addView(TextView(this).apply {
                text = rule.name; setTextColor(WHITE); setPadding(dp(8), dp(6), dp(8), dp(6)); AppFonts.apply(this)
                setOnClickListener { selectFlaggingRule(rule); dialog?.dismiss() }
            }, matchWidth()) }
        }, spacedMatchWidth(5))
        list.addView(accordion("Saved methods", initiallyOpen = true) { body ->
            flaggingRulesDraft.filter { it.enabled }.forEach { rule -> body.addView(TextView(this).apply {
                text = rule.name; setTextColor(WHITE); setPadding(dp(8), dp(6), dp(8), dp(6)); AppFonts.apply(this)
                setOnClickListener { selectFlaggingRule(rule); dialog?.dismiss() }
            }, matchWidth()) }
            if (flaggingRulesDraft.none { it.enabled }) body.addView(infoText("No enabled flagging methods.").apply { setTextColor(MUTED) }, matchWidth())
        }, spacedMatchWidth(5))
        dialog = AlertDialog.Builder(this).setTitle("Flagging method").setView(ScrollView(this).apply { addView(list, matchWidth()) }).setNegativeButton("Close", null).create()
        showDialog(dialog!!)
    }

    private fun selectFlaggingRule(rule: TableStyleRule) {
        selectedFlagRule = rule
        flagQuery = rule.query
        suppressQueryInputChange = true
        if (::filterInput.isInitialized && queryMode == TableQueryMode.FLAGGING) filterInput.setText(rule.query)
        suppressQueryInputChange = false
        if (::filteringMethodButton.isInitialized && queryMode == TableQueryMode.FLAGGING) filteringMethodButton.text = rule.name
        if (flagEnabled) applyFlagAndRender(showStatus = true)
    }

    private fun showColorMappingManager() {
        var dialog: AlertDialog? = null
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(4)); setBackgroundColor(BLACK) }
        list.addView(infoText("Color Mapping is always evaluated when the table loads. Rules use Filtering syntax, but they never hide rows.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
        list.addView(accordion("Built-in Color Mapping examples", initiallyOpen = false) { box ->
            BuiltinExamples.colorMappingRules.forEach { example ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(infoText(example.name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply { text = "Use"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { dialog?.dismiss(); editTableStyleRule(null, mapping = true, template = example) } }, LinearLayout.LayoutParams(dp(52), dp(30)))
                box.addView(row, matchWidth())
            }
        }, spacedMatchWidth(5))
        if (colorMappingsDraft.isEmpty()) list.addView(infoText("No mappings. Use Restore defaults or create one.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
        colorMappingsDraft.forEach { rule ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = "${if (rule.enabled) "●" else "○"} ${rule.name}\n${rule.query}"
                maxLines = 3; textSize = 11.5f; setTextColor(if (rule.enabled) WHITE else MUTED); setPadding(dp(6), dp(4), dp(6), dp(4)); AppFonts.apply(this)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (rule.enabled) "On" else "Off"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this)
                setOnClickListener {
                    colorMappingsDraft = colorMappingsDraft.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }.toMutableList()
                    persistTableRules("Color Mapping updated."); dialog?.dismiss(); showColorMappingManager()
                }
            }, LinearLayout.LayoutParams(dp(42), dp(34)))
            row.addView(TextView(this).apply {
                text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this)
                setOnClickListener { dialog?.dismiss(); editTableStyleRule(rule, mapping = true) }
            }, LinearLayout.LayoutParams(dp(48), dp(34)))
            row.addView(TextView(this).apply {
                text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true)
                setOnClickListener { colorMappingsDraft.removeAll { it.id == rule.id }; persistTableRules("Color Mapping removed."); dialog?.dismiss(); showColorMappingManager() }
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            list.addView(row, matchWidth())
        }
        list.addView(styledButton("+ New mapping").apply { setOnClickListener { dialog?.dismiss(); editTableStyleRule(null, mapping = true) } }, spacedMatchWidth(5))
        list.addView(styledButton("Restore PRICE/CATEGORY defaults", accent = SECONDARY).apply {
            setOnClickListener {
                colorMappingsDraft = BuiltinExamples.defaultColorMappings.toMutableList()
                persistTableRules("Default Color Mapping restored.")
                dialog?.dismiss(); showColorMappingManager()
            }
        }, spacedMatchWidth(4))
        dialog = AlertDialog.Builder(this).setTitle("Color Mapping").setView(ScrollView(this).apply { addView(list, matchWidth()) }).setNegativeButton("Close", null).create()
        showDialog(dialog!!)
    }

    private fun editTableStyleRule(existing: TableStyleRule?, mapping: Boolean, template: TableStyleRule? = null) {
        val source = existing ?: template
        val name = styledInput("rule.name").apply { setText(source?.name.orEmpty()) }
        val query = styledInput("rule.syntax · SELECT * WHERE …").apply { isSingleLine = false; minLines = 3; gravity = Gravity.TOP; setText(source?.query ?: "SELECT * WHERE ") }
        val fore = styledInput("COLOR / .fore assignment").apply { isSingleLine = false; minLines = 2; gravity = Gravity.TOP; setText(source?.foregroundScript.orEmpty()) }
        val back = styledInput("BACKGROUND_COLOR / .back assignment").apply { isSingleLine = false; minLines = 2; gravity = Gravity.TOP; setText(source?.backgroundScript.orEmpty()) }
        val content = styledInput("CONTENT / .content assignment").apply { isSingleLine = false; minLines = 2; gravity = Gravity.TOP; setText(source?.contentScript.orEmpty()) }
        val example = infoText("Compact target: table.PRICE.fore = \"#f54900\" or table.back = \"#ff000044\". Legacy table['MATCHING_ROW']['PRICE'].fore remains supported. Content supports ${'$'}{COLUMN} and ${'$'}value.").apply { setTextColor(MUTED) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(3), dp(14), dp(3)); setBackgroundColor(BLACK)
            addView(example, spacedMatchWidth(6)); listOf(name, query, fore, back, content).forEach { addView(it, spacedMatchWidth(5)) }
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "New ${if (mapping) "Color Mapping" else "flagging method"}" else "Edit ${existing.name}")
            .setView(ScrollView(this).apply { addView(body, matchWidth()) }).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val q = query.text.toString().trim()
                if (n.isBlank()) { name.error = "Name is required"; return@setOnClickListener }
                if (q.isBlank()) { query.error = "Syntax is required"; return@setOnClickListener }
                if (fore.text.isBlank() && back.text.isBlank() && content.text.isBlank()) { back.error = "Add at least one visual/content modifier"; return@setOnClickListener }
                val next = TableStyleRule(existing?.id ?: UUID.randomUUID().toString(), n, q, fore.text.toString().trim(), back.text.toString().trim(), content.text.toString().trim(), existing?.enabled ?: true)
                if (mapping) {
                    colorMappingsDraft = if (existing == null) (colorMappingsDraft + next).toMutableList() else colorMappingsDraft.map { if (it.id == existing.id) next else it }.toMutableList()
                } else {
                    flaggingRulesDraft = if (existing == null) (flaggingRulesDraft + next).toMutableList() else flaggingRulesDraft.map { if (it.id == existing.id) next else it }.toMutableList()
                    selectedFlagRule = next
                    flagQuery = next.query
                }
                persistTableRules(if (mapping) "Color Mapping saved." else "Flagging method saved.")
                dialog.dismiss()
                if (mapping) showColorMappingManager() else showFlaggingRuleManager()
            }
        }
        showDialog(dialog)
    }

    private fun persistTableRules(message: String) {
        settings = settings.copy(
            flaggingRules = flaggingRulesDraft.toList(),
            colorMappings = colorMappingsDraft.toList(),
        )
        mainViewModel.updateStyleSettings(settings)
        if (flagEnabled) applyFlagAndRender(showStatus = false)
        setBusy(true, "Saving table rules to ${RepoConfig.TABLE_RULES_PATH}…")
        settingsViewModel.saveTableRules(settings)
        statusText.text = message
    }

    private fun promptAddField() {
        val keyInput = styledInput("Field key, e.g. merchant")
        val valueInput = styledInput("Optional value or formula")
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("Adds a real field to the current form. It becomes part of the JSON schema only after Amend/Edit commits a nonblank value. The optional value supports JavaScript (=) and SQLite (==) formulas.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(keyInput, spacedMatchWidth(5))
            addView(formulaInputRow("new field", valueInput), matchWidth())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add field")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = keyInput.text.toString().trim()
                if (key.isBlank()) { keyInput.error = "Key is required"; return@setOnClickListener }
                if (currentData.keys.any { it.equals(key, true) }) { keyInput.error = "Field already exists"; return@setOnClickListener }
                val preserved = collectFormValues()
                valueInput.text.toString().takeIf { it.isNotBlank() }?.let { preserved[key] = it }
                mainViewModel.replaceSchema(settings, coreTableData().keys + key)
                renderDynamicForm(mainViewModel.state.value.sourceData, preserved)
                dialog.dismiss()
            }
        }
        showDialog(dialog)
    }

    private fun showFieldOperationsManager() {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,dp(12),0) }
        list.addView(styledButton("+ Add field"), spacedMatchWidth(5))
        val keys = coreTableData().keys
        if (keys.isEmpty()) list.addView(infoText("No real fields in the current schema.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
        keys.forEach { key ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(infoText(key).apply { setTextColor(WHITE) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            row.addView(TextView(this).apply { text="×"; gravity=Gravity.CENTER; setTextColor(RED); AppFonts.apply(this,bold=true); setOnClickListener { confirmRemoveFieldDirect(key) } }, LinearLayout.LayoutParams(dp(40),dp(32)))
            list.addView(row, matchWidth())
        }
        val dialog=AlertDialog.Builder(this).setTitle("Fields").setView(ScrollView(this).apply{addView(list,matchWidth())}).setNegativeButton("Close",null).create()
        // replace add action with captured dialog
        (list.getChildAt(0) as? Button)?.setOnClickListener { dialog.dismiss(); promptAddField() }
        showDialog(dialog)
    }

    private fun showImaginaryFieldOperationsManager() {
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,dp(12),0) }
        val dialog=AlertDialog.Builder(this).setTitle("Imaginary fields").setView(ScrollView(this).apply{addView(list,matchWidth())}).setNegativeButton("Close",null).create()
        list.addView(styledButton("+ Add imaginary field").apply { setOnClickListener { dialog.dismiss(); promptAddImaginaryField() } }, spacedMatchWidth(5))
        if(imaginaryFieldsDraft.isEmpty()) list.addView(infoText("No imaginary fields configured.").apply{setTextColor(MUTED)},spacedMatchWidth(5))
        imaginaryFieldsDraft.forEach { field ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(infoText(field.name).apply{setTextColor(PRIMARY);setOnClickListener{dialog.dismiss();promptAddImaginaryField(existing=field)}},LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            row.addView(TextView(this).apply{text="×";gravity=Gravity.CENTER;setTextColor(RED);AppFonts.apply(this,bold=true);setOnClickListener{dialog.dismiss();confirmRemoveImaginaryDirect(field)}},LinearLayout.LayoutParams(dp(40),dp(32)))
            list.addView(row,matchWidth())
        }
        showDialog(dialog)
    }

    private fun confirmRemoveFieldDirect(key: String) {
        val affectedRows=coreTableData().rows.count{row->row.values.keys.any{it.equals(key,true)}}
        AlertDialog.Builder(this).setTitle("Remove $key?").setMessage(if(affectedRows==0) "Remove '$key' from the local form/schema?" else "Remove '$key' from $affectedRows row(s)? This modifies the selected JSON file.")
            .setNegativeButton("No",null).setPositiveButton("Yes"){_,_->
                if(affectedRows==0){ val preserved=collectFormValues().filterKeys{!it.equals(key,true)}.toMutableMap(); mainViewModel.replaceSchema(settings,coreTableData().keys.filterNot{it.equals(key,true)}); renderDynamicForm(mainViewModel.state.value.sourceData,preserved) }
                else { if(requireToken()==null)return@setPositiveButton; mainViewModel.removeField(settings,key) }
            }.create().also{showDialog(it)}
    }

    private fun confirmRemoveImaginaryDirect(field: ImaginaryFieldDefinition) {
        AlertDialog.Builder(this).setTitle("Remove ${field.name}?").setMessage("This removes the imaginary definition and manual values. Core JSON is unchanged.")
            .setNegativeButton("No",null).setPositiveButton("Yes"){_,_->imaginaryFieldsDraft.removeAll{it.id==field.id};persistImaginaryFields("Imaginary field '${field.name}' removed.")}.create().also{showDialog(it)}
    }

    private fun promptRemoveField() {
        val keys = coreTableData().keys
        if (keys.isEmpty()) {
            statusText.text = "There are no real JSON fields to remove."
            return
        }
        val labels = keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Remove field")
            .setItems(labels) { _, which ->
                val key = keys[which]
                val affectedRows = coreTableData().rows.count { row ->
                    row.values.keys.any { it.equals(key, ignoreCase = true) }
                }
                val message = if (affectedRows == 0) {
                    "Remove '$key' from the current form/schema? It is not currently stored in any JSON row."
                } else {
                    "Remove '$key' from $affectedRows row(s) in ${selectedPath?.substringAfterLast('/') ?: "the selected JSON file"}? This commits the modified JSON to GitHub."
                }
                AlertDialog.Builder(this)
                    .setTitle("Remove $key?")
                    .setMessage(message)
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes") { _, _ ->
                        if (affectedRows == 0) {
                            val preserved = collectFormValues().filterKeys { !it.equals(key, ignoreCase = true) }.toMutableMap()
                            mainViewModel.replaceSchema(settings, keys.filterNot { it.equals(key, ignoreCase = true) })
                            renderDynamicForm(mainViewModel.state.value.sourceData, preserved)
                            statusText.text = "Field '$key' removed from the local schema."
                        } else {
                            if (requireToken() == null) return@setPositiveButton
                            mainViewModel.removeField(settings, key)
                        }
                    }
                    .create().also { showDialog(it) }
            }
            .setNegativeButton("Cancel", null)
            .create().also { showDialog(it) }
    }

    private fun promptRemoveImaginaryField() {
        if (imaginaryFieldsDraft.isEmpty()) {
            statusText.text = "There are no imaginary fields to remove."
            return
        }
        val fields = imaginaryFieldsDraft.toList()
        AlertDialog.Builder(this)
            .setTitle("Remove imaginary field")
            .setItems(fields.map { it.name }.toTypedArray()) { _, which ->
                val field = fields[which]
                AlertDialog.Builder(this)
                    .setTitle("Remove ${field.name}?")
                    .setMessage("This removes the imaginary column definition and its manual values. The core JSON file is not modified.")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes") { _, _ ->
                        imaginaryFieldsDraft.removeAll { it.id == field.id }
                        persistImaginaryFields("Imaginary field '${field.name}' removed.")
                    }
                    .create().also { showDialog(it) }
            }
            .setNegativeButton("Cancel", null)
            .create().also { showDialog(it) }
    }

    private fun promptAddImaginaryField(
        existing: ImaginaryFieldDefinition? = null,
        snippet: ImaginaryFieldSnippet? = null,
    ) {
        val suggestedName = snippet?.name?.uppercase(Locale.US)?.replace(Regex("[^A-Z0-9]+"), "_")?.trim('_').orEmpty()
        val name = styledInput("imaginary_field.name").apply { setText(existing?.name ?: suggestedName) }
        val value = styledInput("imaginary_field.optional_value_or_formula").apply {
            isSingleLine = false
            minLines = 2
            gravity = Gravity.TOP
            setText(existing?.expression ?: snippet?.expression.orEmpty())
        }
        val formulaRow = formulaInputRow(existing?.name ?: "imaginary field", value)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("Imaginary fields never modify the Financial JSON. The value is optional: leave it blank to create a manual-only column. Use = for JavaScript and == for a SQLite scalar expression. A manual value entered from Edit row overrides the formula for that row.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(accordion("Built-in snippets", initiallyOpen = false) { examples ->
                BuiltinExamples.imaginaryFieldSnippets.forEach { item ->
                    examples.addView(TextView(this@MainActivity).apply {
                        text = "${item.name}\n${item.description}"
                        textSize = 11.5f
                        setTextColor(WHITE)
                        setPadding(dp(6), dp(5), dp(6), dp(5))
                        AppFonts.apply(this, textScale = settings.textScale)
                        setOnClickListener {
                            if (name.text.isBlank()) name.setText(item.name.uppercase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_").trim('_'))
                            value.setText(item.expression)
                        }
                    }, spacedMatchWidth(3))
                }
            }, spacedMatchWidth(6))
            addView(name, spacedMatchWidth(5))
            addView(formulaRow, matchWidth())
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add imaginary field" else "Edit imaginary field")
            .setView(ScrollView(this).apply { addView(body, matchWidth()) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fieldName = name.text.toString().trim()
                val expression = value.text.toString()
                if (fieldName.isBlank()) { name.error = "Name is required"; return@setOnClickListener }
                val coreKeys = coreTableData().keys
                if (coreKeys.any { it.equals(fieldName, true) }) {
                    name.error = "A real JSON field already uses this name"
                    return@setOnClickListener
                }
                val duplicate = imaginaryFieldsDraft.firstOrNull {
                    it.id != existing?.id && it.name.equals(fieldName, ignoreCase = true)
                }
                val base = ImaginaryFieldDefinition(
                    id = existing?.id ?: duplicate?.id ?: UUID.randomUUID().toString(),
                    name = fieldName,
                    expression = expression,
                    manualValues = existing?.manualValues ?: duplicate?.manualValues ?: emptyMap(),
                    enabled = existing?.enabled ?: duplicate?.enabled ?: true,
                )
                fun save(replaceId: String? = existing?.id ?: duplicate?.id) {
                    imaginaryFieldsDraft = if (replaceId == null) {
                        (imaginaryFieldsDraft + base).toMutableList()
                    } else {
                        imaginaryFieldsDraft.map { if (it.id == replaceId) base.copy(id = replaceId) else it }.toMutableList()
                    }
                    persistImaginaryFields("Imaginary field saved.")
                    dialog.dismiss()
                }
                if (duplicate != null && existing == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Replace ${duplicate.name}?")
                        .setMessage("An imaginary field with this column name already exists. Replace its definition? Existing manual row values will be preserved.")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Yes") { _, _ -> save(duplicate.id) }
                        .create().also { showDialog(it) }
                } else save()
            }
        }
        showDialog(dialog)
    }

    private fun showImaginaryFieldManager() {
        var dialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(BLACK)
        }
        list.addView(infoText("Imaginary fields extend only Exvia's effective table clone. Their headers use Primary. Blank expressions create manual-only columns; = runs JavaScript; == runs a SQLite scalar formula.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
        list.addView(accordion("Built-in snippets", initiallyOpen = false) { examples ->
            BuiltinExamples.imaginaryFieldSnippets.forEach { snippet ->
                examples.addView(TextView(this).apply {
                    text = "${snippet.name}\n${snippet.description}"
                    maxLines = 3
                    textSize = 11.5f
                    setTextColor(WHITE)
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    AppFonts.apply(this, textScale = settings.textScale)
                    setOnClickListener { dialog?.dismiss(); promptAddImaginaryField(snippet = snippet) }
                }, spacedMatchWidth(3))
            }
        }, spacedMatchWidth(6))
        imaginaryFieldsDraft.forEach { field ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val mode = if (field.expression.isBlank()) "manual" else when (xyz.x3ofiz4.exvia.domain.service.FormulaSupport.parse(field.expression).kind) {
                xyz.x3ofiz4.exvia.domain.service.FormulaSupport.Kind.JAVASCRIPT -> "js"
                xyz.x3ofiz4.exvia.domain.service.FormulaSupport.Kind.SQLITE -> "sql"
                xyz.x3ofiz4.exvia.domain.service.FormulaSupport.Kind.VALUE -> "value"
            }
            row.addView(infoText("${if (field.enabled) "●" else "○"} ${field.name} · $mode").apply { setTextColor(if (field.enabled) PRIMARY else MUTED) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (field.enabled) "On" else "Off"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this)
                setOnClickListener {
                    imaginaryFieldsDraft = imaginaryFieldsDraft.map { if (it.id == field.id) it.copy(enabled = !it.enabled) else it }.toMutableList()
                    persistImaginaryFields("Imaginary field updated."); dialog?.dismiss(); showImaginaryFieldManager()
                }
            }, LinearLayout.LayoutParams(dp(42), dp(32)))
            row.addView(TextView(this).apply { text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this); setOnClickListener { dialog?.dismiss(); promptAddImaginaryField(field) } }, LinearLayout.LayoutParams(dp(48), dp(32)))
            row.addView(TextView(this).apply {
                text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true)
                setOnClickListener {
                    imaginaryFieldsDraft.removeAll { it.id == field.id }
                    persistImaginaryFields("Imaginary field removed."); dialog?.dismiss(); showImaginaryFieldManager()
                }
            }, LinearLayout.LayoutParams(dp(34), dp(32)))
            list.addView(row, matchWidth())
        }
        if (imaginaryFieldsDraft.isEmpty()) list.addView(infoText("No imaginary fields configured.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
        list.addView(styledButton("+ Add imaginary field").apply { setOnClickListener { dialog?.dismiss(); promptAddImaginaryField() } }, spacedMatchWidth(4))
        dialog = AlertDialog.Builder(this).setTitle("Imaginary fields").setView(ScrollView(this).apply { addView(list, matchWidth()) }).setNegativeButton("Close", null).create()
        showDialog(dialog!!)
    }

    private fun persistImaginaryFields(message: String) {
        settings = settings.copy(imaginaryFields = imaginaryFieldsDraft.toList())
        lastImaginaryEvaluationSignature = null
        settingsViewModel.saveImaginaryFields(settings)
        scheduleImaginaryFieldEvaluation(mainViewModel.state.value)
        statusText.text = message
    }

    private fun amend() {
        val path = selectedPath ?: run {
            statusText.text = "Select or create a JSON file first."
            showTab(Tab.FILES)
            return
        }
        if (requireToken() == null) return
        val rawValues = collectFormValues()
        setBusy(true, "Evaluating field formulas…")
        fieldFormulaEngine.resolveInputValues(rawValues, null, coreTableData(), path.substringAfterLast('/')) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { values ->
                        setBusy(false)
                        val transformedValues = applySchemaOutputTransforms(values)
                        val preview = transformedValues.entries.filter { it.value.isNotBlank() }.take(8)
                            .joinToString("\n") { "${it.key}: ${it.value}" }
                            .ifBlank { "All fields are blank; the repository writer may only add an inferred date." }
                        AlertDialog.Builder(this)
                            .setTitle(if (settings.automaticAmend) "Amend expense?" else "Stage expense?")
                            .setMessage(if (settings.automaticAmend) {
                                "$preview\n\nCommit this change to ${path.substringAfterLast('/')}?"
                            } else {
                                "$preview\n\nSave this change to Exvia's local stage? It will not reach GitHub until Git → Amend is used."
                            })
                            .setNegativeButton("No", null)
                            .setPositiveButton("Yes") { _, _ -> mainViewModel.amend(settings, transformedValues) }
                            .create().also { showDialog(it) }
                    },
                    onFailure = { error ->
                        setBusy(false)
                        Toast.makeText(this, "Formula error: ${error.message}", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    private fun collectFormValues(): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
        formInputs.forEach { (key, view) -> put(key, view.text.toString()) }
    }

    private fun renderTable(data: TableData, resetPage: Boolean = true) {
        paginatedTableData = data
        if (resetPage) currentPageIndex = 0
        renderCurrentTablePage()
    }

    private fun renderCurrentTablePage() {
        if (!::table.isInitialized) return
        table.removeAllViews()
        val rowsPerPage = settings.rowsPerPage.coerceIn(1, 500)
        val pageCount = maxOf(1, (paginatedTableData.rows.size + rowsPerPage - 1) / rowsPerPage)
        currentPageIndex = currentPageIndex.coerceIn(0, pageCount - 1)

        if (paginatedTableData.keys.isNotEmpty()) {
            val header = TableRow(this).apply {
                setBackgroundColor(BLACK)
                paginatedTableData.keys.forEach { key ->
                    addView(cell(key.uppercase(), header = true, textColor = if (imaginaryKeys.any { it.equals(key, true) }) PRIMARY else WHITE))
                }
                addView(cell("", header = true))
            }
            addTableRow(header)

            val start = currentPageIndex * rowsPerPage
            val end = minOf(start + rowsPerPage, paginatedTableData.rows.size)
            paginatedTableData.rows.subList(start.coerceAtMost(end), end).forEach { row ->
                val resolvedRow = tableStyles.rows[row.originalIndex]
                val rowForeground = resolvedRow?.foreground?.let(::parseRuleColorOrNull) ?: WHITE
                val rowBackground = resolvedRow?.background?.let(::parseRuleColorOrNull) ?: BLACK
                val tr = TableRow(this).apply { setBackgroundColor(rowBackground) }
                paginatedTableData.keys.forEach { key ->
                    val cellStyle = resolvedRow?.cells?.entries?.firstOrNull { it.key.equals(key, true) }?.value
                    val rawValue = row.values[key].orEmpty()
                    val shownValue = cellStyle?.content ?: resolvedRow?.content ?: rawValue
                    val textColor = cellStyle?.foreground?.let(::parseRuleColorOrNull) ?: rowForeground
                    val backgroundColor = cellStyle?.background?.let(::parseRuleColorOrNull) ?: rowBackground
                    tr.addView(cell(shownValue, textColor = textColor, backgroundColor = backgroundColor, onClick = { editRow(row) }))
                }
                tr.addView(cell("×", textColor = PRIMARY, backgroundColor = rowBackground, onClick = { confirmDeleteRow(row) }).apply {
                    gravity = Gravity.CENTER
                    AppFonts.apply(this, bold = true)
                })
                addTableRow(tr)
            }
        }
        updatePaginationControls(pageCount)
    }

    private fun tablePageCount(): Int {
        val rowsPerPage = settings.rowsPerPage.coerceIn(1, 500)
        return maxOf(1, (paginatedTableData.rows.size + rowsPerPage - 1) / rowsPerPage)
    }

    private fun beginPageJump() {
        if (busy || pageJumpInput != null || !::pageIndicatorText.isInitialized) return
        val parent = pageIndicatorText.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(pageIndicatorText)
        if (index < 0) return

        val pageCount = tablePageCount()
        val originalParams = pageIndicatorText.layoutParams
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_GO
            gravity = Gravity.CENTER
            setText((currentPageIndex + 1).toString())
            hint = "1–$pageCount"
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            setSelectAllOnFocus(true)
            setPadding(dp(4), 0, dp(4), 0)
            background = activeButtonBackground(PRIMARY)
            AppFonts.apply(this, bold = true, textScale = settings.textScale)
            setOnEditorActionListener { _, actionId, event ->
                val enterReleased = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enterReleased) {
                    submitPageJump(this)
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && pageJumpInput === this) cancelPageJump()
            }
        }

        pageJumpInput = input
        parent.removeViewAt(index)
        parent.addView(input, index, originalParams)
        input.requestFocus()
        input.selectAll()
        input.post {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun submitPageJump(input: EditText) {
        if (pageJumpInput !== input) return
        val pageCount = tablePageCount()
        val requestedPage = input.text.toString().trim().toIntOrNull()
        if (requestedPage == null || requestedPage !in 1..pageCount) {
            Toast.makeText(this, "Enter a page from 1 to $pageCount.", Toast.LENGTH_SHORT).show()
            input.selectAll()
            return
        }

        restorePageIndicator(input)
        currentPageIndex = requestedPage - 1
        renderCurrentTablePage()
        showPageOverlay()
    }

    private fun cancelPageJump() {
        val input = pageJumpInput ?: return
        restorePageIndicator(input)
        updatePaginationControls(tablePageCount())
    }

    private fun restorePageIndicator(input: EditText) {
        val parent = input.parent as? ViewGroup
        val index = parent?.indexOfChild(input) ?: -1
        val params = input.layoutParams
        pageJumpInput = null

        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(input.windowToken, 0)

        if (parent != null && index >= 0) {
            parent.removeViewAt(index)
            parent.addView(pageIndicatorText, index, params)
        }
    }

    private fun changeTablePage(delta: Int) {
        if (busy) return
        val pageCount = tablePageCount()
        val next = (currentPageIndex + delta).coerceIn(0, pageCount - 1)
        if (next == currentPageIndex) return
        currentPageIndex = next
        renderCurrentTablePage()
        showPageOverlay()
    }

    private fun updatePaginationControls(pageCount: Int) {
        if (!::pageIndicatorText.isInitialized) return
        val pageNumber = currentPageIndex + 1
        pageIndicatorText.text = "Page $pageNumber / $pageCount"
        previousPageButton.isEnabled = !busy && currentPageIndex > 0
        nextPageButton.isEnabled = !busy && currentPageIndex < pageCount - 1
        previousPageButton.alpha = if (previousPageButton.isEnabled) 1f else 0.38f
        nextPageButton.alpha = if (nextPageButton.isEnabled) 1f else 0.38f
    }

    private fun showPageOverlay() {
        if (!::pageOverlayText.isInitialized) return
        pageOverlayHideRunnable?.let(uiHandler::removeCallbacks)
        pageOverlayText.animate().cancel()
        pageOverlayText.text = "Page ${currentPageIndex + 1}"
        pageOverlayText.alpha = 1f
        pageOverlayText.visibility = View.VISIBLE
        val hide = Runnable {
            pageOverlayText.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction {
                    pageOverlayText.visibility = View.GONE
                    pageOverlayText.alpha = 1f
                }
                .start()
        }
        pageOverlayHideRunnable = hide
        uiHandler.postDelayed(hide, 850L)
    }

    private fun editRow(row: DynamicRow) {
        if (selectedPath == null || requireToken() == null) return
        val editInputs = linkedMapOf<String, EditText>()
        val imaginaryInputs = linkedMapOf<String, EditText>()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(BLACK)
        }
        currentData.keys.filterNot { key -> imaginaryKeys.any { it.equals(key, true) } }.filterNot { key -> schemaConfigCache.entries.firstOrNull { it.key.equals(key, true) }?.value?.hidden == true }.forEach { key ->
            val input = inputForKey(key, row.values[key].orEmpty(), currentData)
            editInputs[key] = input
            form.addView(formulaInputRow(key, input), matchWidth())
        }
        val imaginaryDefinitions = imaginaryFieldsDraft.filter { it.enabled }
        if (imaginaryDefinitions.isNotEmpty()) {
            form.addView(infoText("Imaginary values · manual overrides").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(7))
            imaginaryDefinitions.forEach { definition ->
                val input = styledInput(definition.name).apply { setText(row.values[definition.name].orEmpty()) }
                imaginaryInputs[definition.name] = input
                form.addView(input, spacedMatchWidth(4))
            }
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
                setBusy(true, "Evaluating field formulas…")
                fieldFormulaEngine.resolveInputValues(values, row, coreTableData(), selectedPath?.substringAfterLast('/') ?: "current.json") { result ->
                    runOnUiThread {
                        result.fold(
                            onSuccess = { resolved ->
                                saveImaginaryManualValues(row, imaginaryInputs)
                                dialog.dismiss()
                                mainViewModel.updateRow(settings, row, applySchemaOutputTransforms(resolved))
                            },
                            onFailure = { error ->
                                setBusy(false)
                                Toast.makeText(this, "Formula error: ${error.message}", Toast.LENGTH_LONG).show()
                            },
                        )
                    }
                }
            }
        }
        showDialog(dialog)
    }

    private fun saveImaginaryManualValues(row: DynamicRow, inputs: Map<String, EditText>) {
        if (inputs.isEmpty()) return
        val fileName = selectedPath?.substringAfterLast('/') ?: return
        val storageKey = "$fileName#${row.originalIndex}"
        imaginaryFieldsDraft = imaginaryFieldsDraft.map { definition ->
            val input = inputs.entries.firstOrNull { it.key.equals(definition.name, true) }?.value ?: return@map definition
            val nextValues = definition.manualValues.toMutableMap()
            input.text.toString().takeIf { it.isNotBlank() }?.let { nextValues[storageKey] = it } ?: nextValues.remove(storageKey)
            definition.copy(manualValues = nextValues)
        }.toMutableList()
        persistImaginaryFields("Imaginary row values saved.")
    }

    private fun confirmDeleteRow(row: DynamicRow) {
        if (selectedPath == null || requireToken() == null) return
        val preview = currentData.keys.take(4).joinToString("\n") { "$it: ${row.values[it].orEmpty()}" }
        val deleteMessage = if (settings.automaticAmend) {
            "$preview\n\nThis commits the deletion to ${settings.branch}."
        } else {
            "$preview\n\nThis stages the deletion locally. It will not reach GitHub until Git → Amend is used."
        }
        AlertDialog.Builder(this)
            .setTitle("Delete row?")
            .setMessage(deleteMessage)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> mainViewModel.deleteRow(settings, row) }
            .create().also { showDialog(it) }
    }

    private fun renderStats(data: TableData) {
        statContent.removeAllViews()
        scheduleMetricColorEvaluation(data)
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
                    val finance = statisticsViewModel.financeStats(reportData) ?: return@forEach
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
            val stats = statisticsViewModel.keyStats(values)
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
                val modeNumber = stats.mode?.let { statisticsViewModel.parseNumber(it) }
                val modeColor = when { stats.mode == null -> MUTED; modeNumber != null && modeNumber < 0.0 -> RED; else -> GREEN }
                addMetric(container, "Mode", stats.mode ?: "N/A", nameColor = modeColor, valueColor = modeColor, tooltip = statTooltip("Mode"))
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
                addMetric(container, "n", stats.n.toString(), nameColor = GREEN, valueColor = GREEN, tooltip = statTooltip("n"))
                addMetric(container, "n unique", stats.nUnique.toString(), nameColor = GREEN, valueColor = GREEN, tooltip = statTooltip("n unique"))
                addStatisticMetric(container, "Variance", stats.variance, STAT_SPREAD)
                if (stats.numericN != stats.n) addMetric(container, "Numeric n", stats.numericN.toString(), nameColor = GREEN, valueColor = GREEN, tooltip = "Number of non-empty values that could be parsed as numbers.")
            }, matchWidth())
        }

        renderCustomStatGroups(effectiveJson)

    }

    /**
     * Custom Stat accordions are top-level Stat siblings. `::` creates nested
     * accordions (Anki-deck style), while Plotting always renders before Metrics.
     */
    private fun renderCustomStatGroups(effectiveJson: JSONObject) {
        val enabledPlots = customPlotsDraft.filter { it.enabled }
        val enabledMetrics = customMetricsDraft.filter { it.enabled }
        if (enabledPlots.isEmpty() && enabledMetrics.isEmpty()) return

        val roots = linkedMapOf<String, CustomStatNode>()
        normalizedScriptGroups().forEach { group ->
            val hasContent = enabledPlots.any { it.groupId == group.id } || enabledMetrics.any { it.groupId == group.id }
            if (!hasContent) return@forEach
            val parts = group.name.split("::").map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("Default") }
            var children = roots
            var path = ""
            var node: CustomStatNode? = null
            parts.forEach { part ->
                path = if (path.isBlank()) part else "$path::$part"
                node = children.getOrPut(part.lowercase(Locale.US)) { CustomStatNode(part, path) }
                children = node!!.children
            }
            node?.groupIds?.add(group.id)
        }

        fun renderNode(parent: LinearLayout, node: CustomStatNode, depth: Int) {
            parent.addView(accordion(node.name, initiallyOpen = node.fullPath.equals("Default", true)) { box ->
                val plots = enabledPlots.filter { it.groupId in node.groupIds }
                val metrics = enabledMetrics.filter { it.groupId in node.groupIds }
                if (plots.isNotEmpty()) {
                    box.addView(infoText("# Plotting").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(3))
                    plots.forEach { plot -> renderCustomPlotCard(box, plot, effectiveJson) }
                }
                if (metrics.isNotEmpty()) {
                    box.addView(infoText("# Metrics").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(5))
                    metrics.forEach { metric -> renderCustomMetricCard(box, metric, effectiveJson) }
                }
                node.children.values.forEach { child -> renderNode(box, child, depth + 1) }
            }, matchWidth())
        }

        roots.values.forEach { root -> renderNode(statContent, root, 0) }
    }

    private fun renderCustomPlotCard(container: LinearLayout, definition: CustomPlotDefinition, effectiveJson: JSONObject) {
        container.addView(accordion(definition.name, tooltip = "${definition.engine} script using the current visible/filtered JSON file.") { plotRoot ->
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
            plotRoot.addView(infoText("Runtime: ${definition.engine} · d3, Plot, aq, jsonFile, context, theme, helpers, and ENV. Filtering changes jsonFile.content.").apply { setTextColor(MUTED) }, matchWidth())
        }, matchWidth())
    }

    private fun scheduleMetricColorEvaluation(data: TableData) {
        val rules = metricColorMappingsDraft.filter { it.enabled }
        if (rules.isEmpty()) { metricColorCache = emptyMap(); metricColorSignature = ""; return }
        val items = mutableListOf<Pair<String, Double?>>()
        val money = data.moneyKey ?: settings.detectMoneyKey(data.keys)
        if (money != null) {
            val st = statisticsViewModel.keyStats(data.rows.map { it.values[money].orEmpty() })
            items += listOf("Mean" to st.mean, "Median" to st.median, "Mean − Median gap" to st.meanMedianGap, "Sum" to st.sum, "STDV" to st.stdv, "Minimum" to st.minimum, "Maximum" to st.maximum, "Range" to st.range, "Q1" to st.q1, "Q2" to st.q2, "Q3" to st.q3, "IQR" to st.iqr, "Skew" to st.skew, "Kurtosis" to st.kurtosis, "Variance" to st.variance)
        }
        statisticsViewModel.financeStats(data)?.let { f -> items += listOf("Net Cash Flow" to f.netCashFlow, "Savings Rate" to f.savingsRate, "Expense Ratio" to f.expenseRatio, "Total Income" to f.totalIncome, "Total Expenses" to f.totalExpenses, "Expense Growth Rate" to f.expenseGrowthRate, "Income growth rate" to f.incomeGrowthRate, "Cash Burn Rate" to f.cashBurnRate, "Average Daily Balance" to f.averageDailyBalance) }
        val signature = rules.joinToString("|") { it.id + it.script.hashCode() } + ":" + items.hashCode()
        if (signature == metricColorSignature) return
        metricColorSignature = signature
        metricColorEngine.evaluate(rules, items) { result -> runOnUiThread { result.onSuccess { next -> if (next != metricColorCache) { metricColorCache = next; renderStats(data) } } } }
    }

    private fun renderCustomMetricCard(container: LinearLayout, metric: CustomMetricDefinition, effectiveJson: JSONObject) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(3), 0, dp(6)) }
        val valueView = infoText("Evaluating…").apply { gravity = Gravity.END; setTextColor(MUTED) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nameView = infoText(metric.name).apply { setTextColor(metricColorInt(metric.name, true, GREEN)); tooltipController.attachHold(this, { "Custom JavaScript metric. Inputs below auto-save and are restored on the next app launch." }) }
        header.addView(nameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(valueView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(header, matchWidth()); container.addView(card, matchWidth())

        fun evaluate() {
            val inputs = customMetricInputsDraft.filterKeys { it.startsWith("${metric.id}:") }.mapKeys { it.key.substringAfter(':') }
            customMetricEngine.evaluateObject(metric, effectiveJson, inputs) { evaluated -> runOnUiThread {
                evaluated.fold(onFailure = { valueView.text = "Error: ${it.message}"; valueView.setTextColor(RED) }, onSuccess = { envelope ->
                    val raw = envelope.opt("value")
                    card.removeViews(1, (card.childCount - 1).coerceAtLeast(0))
                    val obj = raw as? JSONObject
                    val label = obj?.optString("label")?.takeIf { it.isNotBlank() } ?: metric.name
                    nameView.text = label
                    val displayValue = when (val v = obj?.opt("value") ?: raw) { null, JSONObject.NULL -> "N/A"; is JSONObject, is JSONArray -> v.toString(); else -> v.toString() }
                    valueView.text = displayValue
                    val numeric = displayValue.replace("%","").trim().toDoubleOrNull()
                    val baseCustomColor = if ((numeric ?: 0.0) < 0) RED else GREEN
                    valueView.setTextColor(metricColorInt(label, false, baseCustomColor))
                    nameView.setTextColor(metricColorInt(label, true, baseCustomColor))
                    if (metricColorMappingsDraft.any { it.enabled }) {
                        metricColorEngine.evaluate(metricColorMappingsDraft, listOf(label to numeric)) { colorResult -> runOnUiThread {
                            colorResult.getOrNull()?.get(label)?.let { colors ->
                                colors.key?.let(::parseRuleColorOrNull)?.let(nameView::setTextColor)
                                colors.value?.let(::parseRuleColorOrNull)?.let(valueView::setTextColor)
                            }
                        } }
                    }
                    val defs = obj?.optJSONArray("inputs") ?: JSONArray()
                    for (i in 0 until defs.length()) {
                        val spec = defs.optJSONObject(i) ?: continue
                        val inputName = spec.optString("name").ifBlank { "input_$i" }
                        val storageKey = "${metric.id}:$inputName"
                        val saved = customMetricInputsDraft[storageKey]
                        val default = spec.optString("default")
                        val envBinding = spec.optString("env")
                        val edit = styledInput(spec.optString("placeholder").ifBlank { inputName }).apply {
                            setText(saved ?: envBinding.takeIf { it.startsWith("ENV.") }?.let(::readEnvironmentBinding) ?: default)
                            setSingleLine(true)
                            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) {
                                customMetricInputsDraft[storageKey] = text.toString()
                                if (envBinding.startsWith("ENV.")) writeEnvironmentBinding(envBinding, text.toString())
                                persistCustomMetricInputs()
                                evaluate()
                            } }
                            setOnEditorActionListener { _, action, event ->
                                val done = action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER)
                                if (done) { clearFocus(); true } else false
                            }
                        }
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                        row.addView(infoText(spec.optString("label").ifBlank { inputName }).apply { setTextColor(MUTED) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .42f))
                        row.addView(edit, LinearLayout.LayoutParams(0, dp(40), .58f))
                        card.addView(row, matchWidth())
                    }
                })
            } }
        }
        evaluate()
    }

    private fun readEnvironmentBinding(binding: String): String? {
        val parts = binding.removePrefix("ENV.").split('.').filter { it.isNotBlank() }; if (parts.isEmpty()) return null
        val definition = environmentVariablesDraft.firstOrNull { it.name.equals(parts.first(), true) } ?: return null
        var value: Any? = runCatching { org.json.JSONTokener(definition.valueJson).nextValue() }.getOrNull()
        parts.drop(1).forEach { part -> value = when (val current = value) { is JSONObject -> current.opt(part); is JSONArray -> part.toIntOrNull()?.let { current.opt(it) }; else -> null } }
        return value?.takeUnless { it == JSONObject.NULL }?.toString()
    }

    private fun writeEnvironmentBinding(binding: String, rawValue: String) {
        val parts = binding.removePrefix("ENV.").split('.').filter { it.isNotBlank() }; if (parts.isEmpty()) return
        val index = environmentVariablesDraft.indexOfFirst { it.name.equals(parts.first(), true) }; if (index < 0) return
        val definition = environmentVariablesDraft[index]
        var root = runCatching { org.json.JSONTokener(definition.valueJson).nextValue() as? JSONObject }.getOrNull() ?: JSONObject()
        var cursor = root
        parts.drop(1).dropLast(1).forEach { part -> cursor = cursor.optJSONObject(part) ?: JSONObject().also { cursor.put(part, it) } }
        val parsed: Any = rawValue.toDoubleOrNull() ?: when (rawValue.lowercase()) { "true" -> true; "false" -> false; else -> rawValue }
        if (parts.size == 1) environmentVariablesDraft[index] = definition.copy(valueJson = jsonLiteral(parsed))
        else { cursor.put(parts.last(), parsed); environmentVariablesDraft[index] = definition.copy(valueJson = root.toString()) }
        persistEnvironmentVariables("ENV input updated.")
    }

    private fun metricColorInt(name: String, key: Boolean, fallback: Int): Int {
        val colors = metricColorCache.entries.firstOrNull { it.key.equals(name, true) }?.value ?: return fallback
        val value = if (key) colors.key else colors.value
        return value?.let(::parseRuleColorOrNull) ?: fallback
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
            addFinancialMetric(c, "Total Expenses", amount(finance.totalExpenses), "Total absolute value of non-+ money rows in the visible dataset.", signed = finance.totalExpenses?.let { -it })
        }, matchWidth())

        container.addView(accordion("Expense", tooltip = "Spending size, frequency, growth, volatility, recurrence, and subscription load.") { c ->
            addFinancialMetric(c, "Average and Median Expense", meanTemplate(finance.averageExpense, finance.expenseStdv, finance.medianExpense), "Mean ± population standard deviation of expenses, with median in parentheses.", signed = finance.averageExpense?.let { -it })
            addFinancialMetric(c, "Largest Expense", amount(finance.largestExpense), "Largest single expense amount in the visible dataset.", signed = finance.largestExpense?.let { -it })
            addFinancialMetric(c, "Expense Frequency per day", finance.expenseFrequencyPerDay?.let(::fmt) ?: "N/A", "Expense transaction count divided by the number of calendar days covered by the visible dated data.", signed = finance.expenseFrequencyPerDay?.let { -it })
            addFinancialMetric(c, "Expense Growth Rate", pct(finance.expenseGrowthRate), "Percentage change from the first dated expense amount to the last dated expense amount.", signed = finance.expenseGrowthRate?.let { -it })
            addFinancialMetric(c, "Expense Volatility", amount(finance.expenseVolatility), "Population standard deviation of individual expense amounts.", signed = finance.expenseVolatility?.let { -it })
            addFinancialMetric(c, "Recurring Expense Ratio", pct(finance.recurringExpenseRatio), "Share of expense value whose row text looks recurring, such as recurring, subscription, rent, mortgage, utility, or monthly.", signed = finance.recurringExpenseRatio?.let { -it })
            addFinancialMetric(c, "Subscription Burden", pct(finance.subscriptionBurden), "Subscription-like spending divided by total income.", signed = finance.subscriptionBurden?.let { -it })
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
                finance.categorySpending.forEach { (name, value) -> addFinancialMetric(c, name, fmt(value), "Total visible expense assigned to this category/ticker.", signed = -value) }
            }
        }, matchWidth())

        container.addView(accordion("Liquidity", tooltip = "Observed cash burn, reserve coverage, reconstructed balance, and simple runway estimate.") { c ->
            addFinancialMetric(c, "Cash Burn Rate", finance.cashBurnRate?.let { "${fmt(it)}/day" } ?: "N/A", "Average daily expenses minus average daily income over the visible period. Positive means cash is being consumed.", signed = finance.cashBurnRate?.let { -it })
            addFinancialMetric(c, "Cash Reserve Days", days(finance.cashReserveDays), "Observed positive net cash flow divided by average daily expenses. It is a surplus-coverage estimate, not your actual bank balance.")
            addFinancialMetric(c, "Average Daily Balance", amount(finance.averageDailyBalance), "Average reconstructed daily closing balance when the selected period starts at zero and applies visible income/expenses chronologically.", signed = finance.averageDailyBalance)
            addFinancialMetric(c, "Days Until Cash Runs Out", days(finance.daysUntilCashRunsOut), "Simple forecast: reconstructed ending balance divided by positive daily cash burn. N/A when there is no positive balance or no current burn.", signed = finance.daysUntilCashRunsOut)
        }, matchWidth())
    }

    private fun addFinancialMetric(container: LinearLayout, name: String, value: String, tooltip: String, signed: Double? = null) {
        val inferred = signed ?: value.replace("%", "").substringBefore('/').trim().toDoubleOrNull()
        val defaultColor = when { inferred != null && inferred < 0.0 -> RED; else -> GREEN }
        addMetric(container, name, value, nameColor = metricColorInt(name, true, defaultColor), valueColor = metricColorInt(name, false, defaultColor), tooltip = tooltip)
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
            val x = statisticsViewModel.parseDate(rawDate)
            if (x == null) { missingDates += 1; return@mapNotNull null }
            val y = statisticsViewModel.parseNumber(row.values[key].orEmpty())
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
        val values = data.rows.mapNotNull { statisticsViewModel.parseNumber(it.values[key].orEmpty()) }
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
        val nameView = infoText(name).apply { setTextColor(metricColorInt(name, true, nameColor)) }
        tooltip?.let { description -> tooltipController.attachHold(nameView, { description }) }
        row.addView(nameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = infoText(value).apply { gravity = Gravity.END; setTextColor(metricColorInt(name, false, valueColor)) }
        tooltip?.let { description -> tooltipController.attachHold(valueView, { description }) }
        row.addView(valueView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(row, matchWidth())
    }

    private fun addStatisticMetric(container: LinearLayout, name: String, value: Double?, metricColor: Int) {
        val color = when {
            value == null || value.isNaN() || value.isInfinite() -> MUTED
            value < 0.0 -> RED
            else -> GREEN
        }
        addMetric(container, name, value?.let(::fmt) ?: "N/A", nameColor = color, valueColor = color, tooltip = statTooltip(name))
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

    private fun showFileScriptManager() {
        var dialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(BLACK)
        }
        list.addView(infoText("SQLite file scripts run against an ephemeral ALL_FILES table. __file is the JSON filename and __row is its source row index. The final SELECT result is written to a JSON file under ${settings.folder}/. Exvia metadata columns are stripped from the output.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
        list.addView(accordion("Built-in examples", initiallyOpen = false) { examples ->
            BuiltinExamples.fileScripts.forEach { script ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(TextView(this).apply {
                    text = script.name
                    setTextColor(WHITE)
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    AppFonts.apply(this, textScale = settings.textScale)
                    setOnClickListener { dialog?.dismiss(); editFileScript(null, script) }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(TextView(this).apply {
                    text = "Run"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this, bold = true)
                    setOnClickListener { dialog?.dismiss(); executeFileScript(script) }
                }, LinearLayout.LayoutParams(dp(48), dp(32)))
                examples.addView(row, matchWidth())
            }
        }, spacedMatchWidth(6))
        list.addView(infoText("Saved scripts").apply { setTextColor(PRIMARY); AppFonts.apply(this, bold = true) }, spacedMatchWidth(4))
        if (fileScriptsDraft.isEmpty()) list.addView(infoText("No saved file scripts.").apply { setTextColor(MUTED) }, spacedMatchWidth(5))
        fileScriptsDraft.forEach { script ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = script.name; setTextColor(WHITE); setPadding(dp(6), dp(5), dp(6), dp(5)); AppFonts.apply(this)
                setOnClickListener { dialog?.dismiss(); executeFileScript(script) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "Edit"; gravity = Gravity.CENTER; setTextColor(PRIMARY); AppFonts.apply(this)
                setOnClickListener { dialog?.dismiss(); editFileScript(script) }
            }, LinearLayout.LayoutParams(dp(48), dp(32)))
            row.addView(TextView(this).apply {
                text = "×"; gravity = Gravity.CENTER; setTextColor(RED); AppFonts.apply(this, bold = true)
                setOnClickListener {
                    fileScriptsDraft.removeAll { it.id == script.id }
                    persistFileScripts("File script removed.")
                    dialog?.dismiss(); showFileScriptManager()
                }
            }, LinearLayout.LayoutParams(dp(34), dp(32)))
            list.addView(row, matchWidth())
        }
        list.addView(styledButton("+ New SQLite script").apply { setOnClickListener { dialog?.dismiss(); editFileScript(null, null) } }, spacedMatchWidth(5))
        dialog = AlertDialog.Builder(this).setTitle("File SQLite scripts").setView(ScrollView(this).apply { addView(list, matchWidth()) }).setNegativeButton("Close", null).create()
        showDialog(dialog!!)
    }

    private fun editFileScript(existing: FileScriptDefinition?, template: FileScriptDefinition? = null) {
        val name = styledInput("file_script.name").apply { setText(existing?.name ?: template?.name.orEmpty()) }
        val script = styledInput("file_script.sqlite").apply {
            isSingleLine = false; minLines = 10; gravity = Gravity.TOP
            setText(existing?.script ?: template?.script ?: "SELECT * FROM ALL_FILES;")
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(BLACK)
            addView(infoText("The final statement must be SELECT or WITH … SELECT. CREATE TEMP VIEW / DROP VIEW may appear before it. Use -- @exact-schema with __file IN (...) when a merge must reject files whose columns differ.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(name, spacedMatchWidth(5)); addView(script, matchWidth())
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "New SQLite file script" else "Edit ${existing.name}")
            .setView(ScrollView(this).apply { addView(body, matchWidth()) }).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val sql = script.text.toString().trim()
                if (n.isBlank()) { name.error = "Name is required"; return@setOnClickListener }
                if (sql.isBlank()) { script.error = "SQLite script is required"; return@setOnClickListener }
                val next = FileScriptDefinition(existing?.id ?: UUID.randomUUID().toString(), n, sql, true)
                fileScriptsDraft = if (existing == null) (fileScriptsDraft + next).toMutableList()
                else fileScriptsDraft.map { if (it.id == existing.id) next else it }.toMutableList()
                persistFileScripts("File script saved.")
                dialog.dismiss(); showFileScriptManager()
            }
        }
        showDialog(dialog)
    }

    private fun persistFileScripts(message: String) {
        settings = settings.copy(fileScripts = fileScriptsDraft.toList())
        settingsViewModel.saveFileScripts(settings)
        statusText.text = message
    }

    private fun executeFileScript(definition: FileScriptDefinition) {
        if (requireToken() == null) return
        if (files.isEmpty()) { statusText.text = "There are no JSON files to query."; return }
        val output = styledInput("Output JSON file").apply { setText("query-result.json"); selectAll() }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0)
            addView(infoText("Execute '${definition.name}' over all selectable JSON files. If the output file already exists, its row array will be replaced after confirmation.").apply { setTextColor(MUTED) }, spacedMatchWidth(6))
            addView(output, matchWidth())
        }
        val dialog = AlertDialog.Builder(this).setTitle("Execute SQLite script?").setView(body)
            .setNegativeButton("Cancel", null).setPositiveButton("Execute", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var name = output.text.toString().trim()
                if (name.isBlank()) { output.error = "Output file is required"; return@setOnClickListener }
                if (!name.endsWith(".json", true)) name += ".json"
                dialog.dismiss()
                mainViewModel.executeFileScript(settings, resolveEnvInQuery(definition.script), name)
                showTab(Tab.FILES)
            }
        }
        showDialog(dialog)
    }

    private fun promptCreateFile() {
        if (requireToken() == null) return
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
                val name = input.text.toString().trim()
                if (name.isBlank()) { input.error = "File name is required"; return@setOnClickListener }
                dialog.dismiss()
                mainViewModel.createFile(settings, name)
                showTab(Tab.TABLE)
            }
        }
        showDialog(dialog)
    }

    private fun confirmRemoveSelectedFile() {
        val path = selectedPath ?: run { statusText.text = "No file is selected."; return }
        val file = files.firstOrNull { it.path == path } ?: return
        if (requireToken() == null) return
        AlertDialog.Builder(this)
            .setTitle("Remove ${file.name}?")
            .setMessage("This deletes the entire JSON file from ${settings.branch} and commits the change.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> mainViewModel.deleteFile(settings, file) }
            .create().also { showDialog(it) }
    }

    private fun renderFiles() {
        val title = filesScreen.findViewWithTag<TextView>("folder_title")
        title?.text = "${settings.folder.trimEnd('/')}/"
        filesList.removeAllViews()
        val visibleFiles = files.filterNot { it.name.startsWith(".") || it.name.equals(RepoConfig.CONFIG_FILE_NAME, true) }
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

    private fun cell(
        textValue: String,
        header: Boolean = false,
        textColor: Int = WHITE,
        backgroundColor: Int = BLACK,
        onClick: (() -> Unit)? = null,
    ): TextView = TextView(this).apply {
            text = textValue
            setTextColor(if (header && textColor == WHITE) MUTED else textColor)
            setBackgroundColor(if (header) BLACK else backgroundColor)
            setPadding(dp(8), 3, dp(8), 3)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            AppFonts.apply(this, bold = header, textScale = settings.textScale)
            if (onClick != null) setOnClickListener { if (!busy) onClick() }
        }

    /** Rule scripts use CSS-style #RRGGBBAA for 8-digit colors. */
    private fun parseRuleColorOrNull(value: String): Int? = try {
        val clean = value.trim()
        if (Regex("^#[0-9A-Fa-f]{8}$").matches(clean)) {
            val rr = clean.substring(1, 3)
            val gg = clean.substring(3, 5)
            val bb = clean.substring(5, 7)
            val aa = clean.substring(7, 9)
            Color.parseColor("#$aa$rr$gg$bb")
        } else Color.parseColor(clean)
    } catch (_: IllegalArgumentException) { null }

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
        if (::selectedFileText.isInitialized) {
            selectedFileText.text = path?.substringAfterLast('/') ?: "No file selected"
        }
    }

    private fun requireToken(): String? {
        if (!settings.isConfigured()) {
            statusText.text = "Configure GitHub owner/repository in Settings."
            drawerRoot.openDrawer()
            return null
        }
        val token = settingsViewModel.token()
        if (token == null) {
            statusText.text = "Add a GitHub PAT in Settings."
            drawerRoot.openDrawer()
        }
        return token
    }

    private fun statusColor(message: String): Int {
        val value = message.lowercase(Locale.US)
        return when {
            listOf("error", "failed", "failure", "authentication", "invalid", "conflict", "not found", "cannot", "unable").any(value::contains) -> RED
            listOf("saving", "syncing", "loading", "creating", "evaluating", "refreshing", "pulling", "reverting", "staging", "working").any(value::contains) -> LOG_YELLOW
            listOf("saved", "synchronized", "created", "loaded", "removed", "updated", "complete", "success", "reverted", "amended", "restored").any(value::contains) -> LOG_GREEN
            else -> LOG_BLUE
        }
    }

    private fun setBusy(isBusy: Boolean, message: String? = null) {
        busy = isBusy
        if (::amendButton.isInitialized) amendButton.isEnabled = !isBusy && selectedPath != null
        if (::createFileButton.isInitialized) createFileButton.isEnabled = !isBusy
        if (::removeFileButton.isInitialized) removeFileButton.isEnabled = !isBusy && selectedPath != null
        if (::fileScriptButton.isInitialized) fileScriptButton.isEnabled = !isBusy
        if (::resyncButton.isInitialized) {
            resyncButton.isEnabled = !isBusy
            resyncButton.alpha = if (isBusy) 0.45f else 1f
        }
        if (::gitButton.isInitialized) {
            gitButton.isEnabled = !isBusy
            gitButton.alpha = if (isBusy) 0.45f else 1f
        }
        formInputs.values.forEach { it.isEnabled = !isBusy }
        if (isBusy && pageJumpInput != null) cancelPageJump()
        if (::pageIndicatorText.isInitialized) updatePaginationControls(tablePageCount())
        if (message != null) statusText.text = message
    }

    private fun handleError(error: Exception, prefix: String) {
        setBusy(false)
        if (error is GitHubHttpException && error.statusCode == 401) {
            settingsViewModel.clearToken()
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

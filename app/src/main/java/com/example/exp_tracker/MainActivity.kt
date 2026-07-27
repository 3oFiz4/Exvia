package com.example.exp_tracker

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
<<<<<<< HEAD
import android.graphics.Typeface
=======
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
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
<<<<<<< HEAD
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.time.YearMonth
import java.time.format.DateTimeFormatter
=======
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val PREFS = "exp_tracker_ui"
        private const val PREF_SELECTED_PATH = "selected_path"
<<<<<<< HEAD
        private val BLACK = Color.BLACK
        private val WHITE = Color.WHITE
        private val RED = Color.rgb(255, 59, 48)
        private val GREEN = Color.rgb(52, 199, 89)
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var tokenStore: TokenStore

    private lateinit var priceInput: EditText
    private lateinit var tickerInput: AutoCompleteTextView
    private lateinit var descriptionInput: AutoCompleteTextView
    private lateinit var tagsInput: MultiAutoCompleteTextView
    private lateinit var amendButton: Button
    private lateinit var statusText: TextView
    private lateinit var selectedFileText: TextView
    private lateinit var table: TableLayout
    private lateinit var tableScreen: LinearLayout
    private lateinit var filesScreen: LinearLayout
    private lateinit var filesList: LinearLayout
    private lateinit var tableTabButton: Button
    private lateinit var filesTabButton: Button
    private lateinit var createFileButton: Button
    private lateinit var removeFileButton: Button

    private var selectedPath: String? = null
    private var files: List<RepoFile> = emptyList()
    private var showingFiles = false
    private var busy = false
    private var currentRows: List<ExpenseRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BLACK
        window.navigationBarColor = BLACK
        window.decorView.systemUiVisibility = 0

        tokenStore = TokenStore(this)
        setContentView(buildUi())

        amendButton.setOnClickListener { amend() }
        tableTabButton.setOnClickListener { showTableTab() }
        filesTabButton.setOnClickListener { showFilesTab() }
        createFileButton.setOnClickListener { promptCreateFile() }
        removeFileButton.setOnClickListener { confirmRemoveSelectedFile() }

        if (!isRepoConfigured()) {
            statusText.text = "Edit RepoConfig.kt, then rebuild the app."
            setBusy(true)
            return
        }

        val token = tokenStore.load()
        if (token == null) promptForToken() else refreshFilesAndTable(token)
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

<<<<<<< HEAD
    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(6))
            setBackgroundColor(BLACK)
        }

        root.addView(TextView(this).apply {
            text = "exp_tracker"
            textSize = 22f
            setTextColor(WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(5))
        })

        statusText = TextView(this).apply {
            text = ""
            setTextColor(WHITE)
            setPadding(0, 0, 0, dp(5))
        }
        root.addView(statusText, matchWidth())

        val contentHost = FrameLayout(this).apply { setBackgroundColor(BLACK) }
        tableScreen = buildTableScreen()
        filesScreen = buildFilesScreen().apply { visibility = View.GONE }
        contentHost.addView(tableScreen, frameMatch())
        contentHost.addView(filesScreen, frameMatch())
        root.addView(
            contentHost,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BLACK)
        }
        tableTabButton = Button(this).apply {
            text = "Table"
            isAllCaps = false
        }
        filesTabButton = Button(this).apply {
            text = "Files"
            isAllCaps = false
        }
        tabs.addView(tableTabButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        tabs.addView(filesTabButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(tabs, matchWidth())

        updateTabButtons()
        return root
    }

    private fun buildTableScreen(): LinearLayout {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }

        selectedFileText = TextView(this).apply {
            setTextColor(WHITE)
            text = "No file selected"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(3))
        }
        screen.addView(selectedFileText, matchWidth())

        priceInput = styledInput("PRICE", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        tickerInput = styledAutoCompleteInput("TICKER (optional)")
        descriptionInput = styledAutoCompleteInput("DESCRIPTION (optional)")
        tagsInput = styledTagsInput("TAGS (optional) — e.g. non_cash, food, big")
        screen.addView(priceInput, matchWidth())
        screen.addView(tickerInput, matchWidth())
        screen.addView(descriptionInput, matchWidth())
        screen.addView(tagsInput, matchWidth())

        amendButton = styledButton("Amend")
        screen.addView(amendButton, matchWidth())

        table = TableLayout(this).apply {
            isStretchAllColumns = false
            setBackgroundColor(BLACK)
        }
        val horizontal = HorizontalScrollView(this).apply {
            setBackgroundColor(BLACK)
            addView(table, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val vertical = ScrollView(this).apply {
            setBackgroundColor(BLACK)
            addView(horizontal, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        screen.addView(vertical, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        renderTable(emptyList())
        return screen
    }

    private fun buildFilesScreen(): LinearLayout {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }

        screen.addView(TextView(this).apply {
            text = "${RepoConfig.EXPENSE_FOLDER.trimEnd('/')}/"
            setTextColor(WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(5))
        }, matchWidth())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BLACK)
        }
        createFileButton = styledButton("Create")
        removeFileButton = styledButton("Remove selected")
        actions.addView(createFileButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(removeFileButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        screen.addView(actions, matchWidth())

        filesList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BLACK)
            addView(filesList, matchWidth())
        }
        screen.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return screen
    }

    private fun styledInput(hintText: String, inputTypeValue: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = inputTypeValue
            isSingleLine = true
            setTextColor(WHITE)
            setHintTextColor(WHITE)
            backgroundTintList = ColorStateList.valueOf(WHITE)
            setPadding(dp(4), 0, dp(4), 0)
        }

    private fun styledAutoCompleteInput(hintText: String): AutoCompleteTextView =
        AutoCompleteTextView(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            threshold = 1
            setTextColor(WHITE)
            setHintTextColor(WHITE)
            backgroundTintList = ColorStateList.valueOf(WHITE)
            setPadding(dp(4), 0, dp(4), 0)
        }

    private fun styledTagsInput(hintText: String): MultiAutoCompleteTextView =
        MultiAutoCompleteTextView(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            threshold = 1
            setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
            setTextColor(WHITE)
            setHintTextColor(WHITE)
            backgroundTintList = ColorStateList.valueOf(WHITE)
            setPadding(dp(4), 0, dp(4), 0)
        }

    private fun styledButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(BLACK)
        setBackgroundColor(WHITE)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun frameMatch() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun promptForToken() {
        val input = styledInput("github_pat_...", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        val dialog = AlertDialog.Builder(this)
            .setTitle("GitHub token")
            .setMessage("Paste a fine-grained token with Contents: read and write access. It is encrypted locally with Android Keystore.")
            .setView(input)
            .setNegativeButton("Cancel") { _, _ ->
                statusText.text = "GitHub token is required."
            }
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val token = input.text.toString().trim()
                if (token.isBlank()) {
                    input.error = "Token is required"
                } else {
                    tokenStore.save(token)
                    dialog.dismiss()
                    refreshFilesAndTable(token)
                }
            }
        }
        dialog.show()
    }

    private fun refreshFilesAndTable(token: String) {
        setBusy(true, "Loading ${RepoConfig.EXPENSE_FOLDER}/…")
        executor.execute {
            try {
                val api = GitHubApi(token)
                val loadedFiles = api.listExpenseFiles()
                val savedPath = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SELECTED_PATH, null)
                val defaultPath = RepoConfig.pathFor(RepoConfig.DEFAULT_JSON)
                val selected = loadedFiles.firstOrNull { it.path == savedPath }
                    ?: loadedFiles.firstOrNull { it.path == defaultPath }
                    ?: loadedFiles.firstOrNull()
                val rows = selected?.let { api.fetchExpenses(it.path) }.orEmpty()

=======
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
        tabs.addView(tableTabButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(2) })
        tabs.addView(statTabButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(2) })
        tabs.addView(filesTabButton, LinearLayout.LayoutParams(0, dp(48), 1f))
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
        addView(amendButton, spacedMatchWidth(10))

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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                runOnUiThread {
                    files = loadedFiles
                    setSelectedPath(selected?.path)
                    renderFiles()
<<<<<<< HEAD
                    renderTable(rows)
                    statusText.text = if (selected == null) {
                        "No .json files found in ${RepoConfig.EXPENSE_FOLDER}/."
                    } else {
                        "Loaded ${rows.size} row(s) from ${selected.name}."
                    }
=======
                    applyTableData(data)
                    statusText.text = if (selected == null) "No .json files found in ${settings.folder}/." else "Loaded ${data.rows.size} row(s) from ${selected.name}."
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Could not load files") }
            }
        }
    }

<<<<<<< HEAD
    private fun refreshSelected(token: String, successMessage: String? = null) {
        val path = selectedPath ?: run {
            renderTable(emptyList())
            statusText.text = "Select or create a JSON file first."
            return
        }
        setBusy(true, "Loading ${path.substringAfterLast('/')}…")
        executor.execute {
            try {
                val rows = GitHubApi(token).fetchExpenses(path)
                runOnUiThread {
                    renderTable(rows)
                    statusText.text = successMessage ?: "Loaded ${rows.size} row(s) from ${path.substringAfterLast('/')}."
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Could not load selected file") }
            }
        }
    }

<<<<<<< HEAD
    private fun amend() {
        val path = selectedPath
        val token = tokenStore.load()
        if (token == null) {
            promptForToken()
            return
        }
        if (path == null) {
            statusText.text = "Select or create a JSON file first."
            showFilesTab()
            return
        }
        if (priceInput.text.toString().isBlank()) {
            priceInput.error = "PRICE is required"
            return
        }

        val price = priceInput.text.toString()
        val ticker = tickerInput.text.toString()
        val description = descriptionInput.text.toString()
        val tags = tagsInput.text.toString()
        setBusy(true, "Committing expense to ${path.substringAfterLast('/')}…")
        executor.execute {
            try {
                val api = GitHubApi(token)
                val date = api.appendExpense(path, price, ticker, description, tags)
                val rows = api.fetchExpenses(path)
                runOnUiThread {
                    priceInput.text.clear()
                    tickerInput.text.clear()
                    descriptionInput.text.clear()
                    tagsInput.text.clear()
                    renderTable(rows)
                    statusText.text = "Expense committed at $date."
=======
    private fun applyTableData(data: TableData) {
        currentData = data
        renderDynamicForm(data)
        renderTable(data)
        renderStats(data)
        if (::drawerRoot.isInitialized) AppFonts.applyToTree(drawerRoot)
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
                renderTable(currentData)
                renderStats(currentData)
                dialog.dismiss()
            }
        }
        showDialog(dialog)
    }

    private fun amend() {
        val path = selectedPath ?: run { statusText.text = "Select or create a JSON file first."; showTab(Tab.FILES); return }
        val token = requireToken() ?: return
        val values = collectFormValues()
        setBusy(true, "Committing to ${path.substringAfterLast('/')}…")
        executor.execute {
            try {
                val api = GitHubApi(token, settings)
                val date = api.appendRow(path, values)
                val data = api.fetchTable(path)
                runOnUiThread {
                    applyTableData(data)
                    statusText.text = "Committed at $date."
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Amend failed") }
            }
        }
    }

<<<<<<< HEAD
    private fun editExpense(row: ExpenseRow) {
        val path = selectedPath ?: return
        val token = tokenStore.load() ?: run {
            promptForToken()
            return
        }

        val date = styledInput("DATE").apply { setText(row.date) }
        val price = styledInput("PRICE", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED).apply { setText(row.price) }
        val ticker = styledAutoCompleteInput("TICKER").apply { setText(row.ticker); configureSingleAutocomplete(this, tickerSuggestions(currentRows)) }
        val description = styledAutoCompleteInput("DESCRIPTION").apply { setText(row.description); configureSingleAutocomplete(this, descriptionSuggestions(currentRows)) }
        val tags = styledTagsInput("TAGS — e.g. non_cash, food, big").apply { setText(row.tags); configureTagAutocomplete(this, tagSuggestions(currentRows)) }
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(BLACK)
<<<<<<< HEAD
            addView(date, matchWidth())
            addView(price, matchWidth())
            addView(ticker, matchWidth())
            addView(description, matchWidth())
            addView(tags, matchWidth())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit expense")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (date.text.toString().isBlank()) {
                    date.error = "DATE is required"
                    return@setOnClickListener
                }
                if (price.text.toString().isBlank()) {
                    price.error = "PRICE is required"
                    return@setOnClickListener
                }
                dialog.dismiss()
                setBusy(true, "Updating expense…")
                executor.execute {
                    try {
                        val api = GitHubApi(token)
                        api.updateExpense(
                            path = path,
                            originalRow = row,
                            date = date.text.toString(),
                            priceText = price.text.toString(),
                            ticker = ticker.text.toString(),
                            description = description.text.toString(),
                            tags = tags.text.toString(),
                        )
                        val rows = api.fetchExpenses(path)
                        runOnUiThread {
                            renderTable(rows)
                            statusText.text = "Expense updated."
                            setBusy(false)
                        }
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Update failed") }
                    }
                }
            }
        }
<<<<<<< HEAD
        dialog.show()
    }

    private fun confirmDeleteExpense(row: ExpenseRow) {
        val path = selectedPath ?: return
        val token = tokenStore.load() ?: run {
            promptForToken()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete expense?")
            .setMessage("${row.date}\n${row.price}  ${row.ticker}  ${row.description}${if (row.tags.isBlank()) "" else "\nTags: ${row.tags}"}\n\nThis commits the deletion to ${RepoConfig.BRANCH}.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                setBusy(true, "Deleting expense…")
                executor.execute {
                    try {
                        val api = GitHubApi(token)
                        api.deleteExpense(path, row)
                        val rows = api.fetchExpenses(path)
                        runOnUiThread {
                            renderTable(rows)
                            statusText.text = "Expense deleted."
                            setBusy(false)
                        }
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Delete failed") }
                    }
                }
<<<<<<< HEAD
            }
            .show()
    }

    private fun promptCreateFile() {
        val token = tokenStore.load() ?: run {
            promptForToken()
            return
        }
        val suggested = YearMonth.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".json"
        val input = styledInput("File name").apply { setText(suggested); selectAll() }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create JSON file")
            .setMessage("Creates an empty expense array under ${RepoConfig.EXPENSE_FOLDER}/.")
=======
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
            "Timestamp totals: rows sharing an exact datetime are summed before statistics; ${datedNumeric.size} numeric row(s) became ${series.size} timestamp observation(s). Real gaps in dates are preserved. Q1–Q3 = solid box · green when current total > previous total, otherwise red · first box neutral · median = solid contrast line · mean = dotted contrast line · whiskers = mean ± 1σ and follow box color · hollow ○ = Tukey outlier · blue ■ = total at that datetime.$omissionText Tap a box to inspect; pinch to zoom; drag to pan; double-tap to reset.",
        )
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
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(SURFACE)
                setStroke(dp(1).coerceAtLeast(1), if (initiallyOpen) PRIMARY else SECONDARY)
                cornerRadius = dp(3).toFloat()
            }
            AppFonts.apply(this, bold = true)
            setOnClickListener {
                val opening = content.visibility != View.VISIBLE
                content.visibility = if (opening) View.VISIBLE else View.GONE
                text = if (opening) "▾ $title" else "▸ $title"
                setTextColor(if (opening) PRIMARY else WHITE)
                background = GradientDrawable().apply {
                    setColor(SURFACE)
                    setStroke(dp(1).coerceAtLeast(1), if (opening) PRIMARY else SECONDARY)
                    cornerRadius = dp(3).toFloat()
                }
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()
<<<<<<< HEAD

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = "File name is required"
                    return@setOnClickListener
                }
=======
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().trim().isBlank()) { input.error = "File name is required"; return@setOnClickListener }
                val name = input.text.toString().trim()
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                dialog.dismiss()
                setBusy(true, "Creating file…")
                executor.execute {
                    try {
<<<<<<< HEAD
                        val api = GitHubApi(token)
                        val created = api.createExpenseFile(name)
                        val loadedFiles = api.listExpenseFiles()
                        val rows = api.fetchExpenses(created.path)
=======
                        val api = GitHubApi(token, settings)
                        val created = api.createExpenseFile(name)
                        val loadedFiles = api.listExpenseFiles()
                        val data = api.fetchTable(created.path)
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(created.path)
                            renderFiles()
<<<<<<< HEAD
                            renderTable(rows)
                            showTableTab()
=======
                            applyTableData(data)
                            showTab(Tab.TABLE)
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                            statusText.text = "Created and selected ${created.name}."
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Create file failed") }
                    }
                }
            }
        }
<<<<<<< HEAD
        dialog.show()
    }

    private fun confirmRemoveSelectedFile() {
        val path = selectedPath ?: run {
            statusText.text = "No file is selected."
            return
        }
        val file = files.firstOrNull { it.path == path } ?: return
        val token = tokenStore.load() ?: run {
            promptForToken()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Remove ${file.name}?")
            .setMessage("This deletes the entire JSON file from ${RepoConfig.BRANCH}. This action is committed to GitHub.")
=======
        showDialog(dialog)
    }

    private fun confirmRemoveSelectedFile() {
        val path = selectedPath ?: run { statusText.text = "No file is selected."; return }
        val file = files.firstOrNull { it.path == path } ?: return
        val token = requireToken() ?: return
        AlertDialog.Builder(this)
            .setTitle("Remove ${file.name}?")
            .setMessage("This deletes the entire JSON file from ${settings.branch} and commits the change.")
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                setBusy(true, "Removing ${file.name}…")
                executor.execute {
                    try {
<<<<<<< HEAD
                        val api = GitHubApi(token)
                        api.deleteExpenseFile(file)
                        val loadedFiles = api.listExpenseFiles()
                        val next = loadedFiles.firstOrNull()
                        val rows = next?.let { api.fetchExpenses(it.path) }.orEmpty()
=======
                        val api = GitHubApi(token, settings)
                        api.deleteExpenseFile(file)
                        val loadedFiles = api.listExpenseFiles()
                        val next = loadedFiles.firstOrNull()
                        val data = next?.let { api.fetchTable(it.path) } ?: TableData(emptyList(), emptyList(), null, null, null, null)
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(next?.path)
                            renderFiles()
<<<<<<< HEAD
                            renderTable(rows)
                            statusText.text = if (next == null) {
                                "Removed ${file.name}. No JSON files remain."
                            } else {
                                "Removed ${file.name}. Selected ${next.name}."
                            }
=======
                            applyTableData(data)
                            statusText.text = if (next == null) "Removed ${file.name}. No JSON files remain." else "Removed ${file.name}. Selected ${next.name}."
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Remove file failed") }
                    }
                }
<<<<<<< HEAD
            }
            .show()
    }

    private fun renderFiles() {
        filesList.removeAllViews()
        if (files.isEmpty()) {
            filesList.addView(TextView(this).apply {
                text = "No .json files"
                setTextColor(WHITE)
                setPadding(dp(6), dp(10), dp(6), dp(10))
            }, matchWidth())
            removeFileButton.isEnabled = false
            return
        }

        removeFileButton.isEnabled = !busy && selectedPath != null
        for (file in files) {
            val selected = file.path == selectedPath
            val item = TextView(this).apply {
                text = file.name
                textSize = 17f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setTextColor(if (selected) BLACK else WHITE)
                background = outlinedBackground(selected)
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                setOnClickListener {
                    if (busy) return@setOnClickListener
                    setSelectedPath(file.path)
                    renderFiles()
<<<<<<< HEAD
                    showTableTab()
                    tokenStore.load()?.let { refreshSelected(it) }
                }
            }
            filesList.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 1 // exactly one physical pixel between file rows
            })
        }
    }

    private fun renderTable(rows: List<ExpenseRow>) {
        currentRows = rows
        updateAutocompleteSuggestions(rows)
        table.removeAllViews()
        addTableRow(tableHeader())
        for (row in rows) addTableRow(expenseTableRow(row))
    }

    private fun tableHeader(): TableRow = TableRow(this).apply {
        setBackgroundColor(BLACK)
        addView(cell("DATE", header = true))
        addView(cell("PRICE", header = true))
        addView(cell("TICKER", header = true))
        addView(cell("DESCRIPTION", header = true))
        addView(cell("TAGS", header = true))
        addView(cell("", header = true))
    }

    private fun expenseTableRow(row: ExpenseRow): TableRow = TableRow(this).apply {
        setBackgroundColor(BLACK)
        addView(cell(row.date, onClick = { editExpense(row) }))
        addView(cell(
            row.price,
            textColor = if (row.price.trim().startsWith("+")) GREEN else RED,
            onClick = { editExpense(row) },
        ))
        addView(cell(
            row.ticker,
            textColor = tickerColor(row.ticker),
            onClick = { editExpense(row) },
        ))
        addView(cell(row.description, onClick = { editExpense(row) }))
        addView(cell(row.tags, onClick = { editExpense(row) }))
        addView(cell("×", onClick = { confirmDeleteExpense(row) }).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        })
    }

    private fun addTableRow(row: TableRow) {
        table.addView(
            row,
            TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 1 // one physical pixel; intentionally not dp
            },
        )
    }

    private fun cell(
        text: String,
        header: Boolean = false,
        textColor: Int = WHITE,
        onClick: (() -> Unit)? = null,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextColor(textColor)
        setBackgroundColor(BLACK)
        setPadding(dp(6), 2, dp(6), 2) // 2 physical px vertically for a dense table
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        if (header) setTypeface(typeface, Typeface.BOLD)
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            setOnClickListener { if (!busy) onClick() }
        }
    }

    private fun updateAutocompleteSuggestions(rows: List<ExpenseRow>) {
        if (::tickerInput.isInitialized) configureSingleAutocomplete(tickerInput, tickerSuggestions(rows))
        if (::descriptionInput.isInitialized) configureSingleAutocomplete(descriptionInput, descriptionSuggestions(rows))
        if (::tagsInput.isInitialized) configureTagAutocomplete(tagsInput, tagSuggestions(rows))
    }

    private fun tickerSuggestions(rows: List<ExpenseRow>): List<String> =
        frequencySorted(rows.map { it.ticker }.filter { it.isNotBlank() })

    private fun descriptionSuggestions(rows: List<ExpenseRow>): List<String> =
        frequencySorted(rows.map { it.description }.filter { it.isNotBlank() })

    private fun tagSuggestions(rows: List<ExpenseRow>): List<String> = frequencySorted(
        rows.flatMap { row ->
            row.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        },
    )

    private fun frequencySorted(values: List<String>): List<String> {
        val counts = linkedMapOf<String, Pair<String, Int>>()
        for (value in values) {
            val clean = value.trim()
            if (clean.isBlank()) continue
            val key = clean.lowercase()
            val previous = counts[key]
            counts[key] = clean to ((previous?.second ?: 0) + 1)
        }
        return counts.values
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            .map { it.first }
    }

    private fun configureSingleAutocomplete(view: AutoCompleteTextView, suggestions: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions))
    }

    private fun configureTagAutocomplete(view: MultiAutoCompleteTextView, suggestions: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suggestions))
    }

    private fun tickerColor(ticker: String): Int {
        val configured = RepoConfig.TICKER_COLORS.entries.firstOrNull {
            it.key.equals(ticker.trim(), ignoreCase = true)
        }?.value ?: RepoConfig.DEFAULT_TICKER_COLOR
        return try {
            Color.parseColor(configured)
        } catch (_: IllegalArgumentException) {
            WHITE
        }
    }

    private fun outlinedBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (selected) WHITE else BLACK)
        setStroke(1, WHITE)
    }

    private fun showTableTab() {
        showingFiles = false
        tableScreen.visibility = View.VISIBLE
        filesScreen.visibility = View.GONE
        updateTabButtons()
    }

    private fun showFilesTab() {
        showingFiles = true
        tableScreen.visibility = View.GONE
        filesScreen.visibility = View.VISIBLE
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
        updateTabButtons()
    }

    private fun updateTabButtons() {
<<<<<<< HEAD
        if (!::tableTabButton.isInitialized || !::filesTabButton.isInitialized) return
        styleTab(tableTabButton, active = !showingFiles)
        styleTab(filesTabButton, active = showingFiles)
    }

    private fun styleTab(button: Button, active: Boolean) {
        button.setTextColor(if (active) BLACK else WHITE)
        button.setBackgroundColor(if (active) WHITE else BLACK)
=======
        if (!::tableTabButton.isInitialized) return
        styleTab(tableTabButton, activeTab == Tab.TABLE)
        styleTab(statTabButton, activeTab == Tab.STAT)
        styleTab(filesTabButton, activeTab == Tab.FILES)
    }

    private fun styleTab(button: Button, active: Boolean) {
        val accent = if (active) PRIMARY else SECONDARY
        button.setTextColor(if (active) PRIMARY else MUTED)
        button.background = buttonBackground(accent)
        AppFonts.apply(button, bold = active)
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    private fun setSelectedPath(path: String?) {
        selectedPath = path
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            if (path == null) remove(PREF_SELECTED_PATH) else putString(PREF_SELECTED_PATH, path)
        }.apply()
<<<<<<< HEAD
        if (::selectedFileText.isInitialized) {
            selectedFileText.text = path?.substringAfterLast('/') ?: "No file selected"
        }
=======
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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    private fun setBusy(isBusy: Boolean, message: String? = null) {
        busy = isBusy
        if (::amendButton.isInitialized) amendButton.isEnabled = !isBusy && selectedPath != null
<<<<<<< HEAD
        if (::priceInput.isInitialized) priceInput.isEnabled = !isBusy
        if (::tickerInput.isInitialized) tickerInput.isEnabled = !isBusy
        if (::descriptionInput.isInitialized) descriptionInput.isEnabled = !isBusy
        if (::tagsInput.isInitialized) tagsInput.isEnabled = !isBusy
        if (::createFileButton.isInitialized) createFileButton.isEnabled = !isBusy
        if (::removeFileButton.isInitialized) removeFileButton.isEnabled = !isBusy && selectedPath != null
        if (message != null && ::statusText.isInitialized) statusText.text = message
=======
        if (::createFileButton.isInitialized) createFileButton.isEnabled = !isBusy
        if (::removeFileButton.isInitialized) removeFileButton.isEnabled = !isBusy && selectedPath != null
        formInputs.values.forEach { it.isEnabled = !isBusy }
        if (message != null) statusText.text = message
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    private fun handleError(error: Exception, prefix: String) {
        setBusy(false)
        if (error is GitHubHttpException && error.statusCode == 401) {
            tokenStore.clear()
<<<<<<< HEAD
            statusText.text = "$prefix: authentication failed. Enter a new token."
            promptForToken()
            return
        }

        val extra = when (error) {
            is GitHubHttpException -> when (error.statusCode) {
                409 -> " The branch/file changed; reload and try again."
                403 -> " Check token permissions or repository rules."
                404 -> " The selected folder/file was not found."
=======
            statusText.text = "$prefix: authentication failed. Update the PAT in Settings."
            drawerRoot.openDrawer()
            return
        }
        val extra = when (error) {
            is GitHubHttpException -> when (error.statusCode) {
                409 -> " The branch/file changed; reload and try again."
                403 -> " Check PAT permissions or repository rules."
                404 -> " The configured repository/folder/file was not found."
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
                else -> ""
            }
            else -> ""
        }
        statusText.text = "$prefix: ${error.message ?: error.javaClass.simpleName}.$extra"
    }

<<<<<<< HEAD
    private fun isRepoConfigured(): Boolean =
        !RepoConfig.OWNER.startsWith("YOUR_") && !RepoConfig.REPO.startsWith("YOUR_")
=======
    private fun showDialog(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.decorView?.let { AppFonts.applyToTree(it) }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let {
            it.setTextColor(PRIMARY)
            AppFonts.apply(it, bold = true)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let {
            it.setTextColor(MUTED)
            AppFonts.apply(it)
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let {
            it.setTextColor(MUTED)
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
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.BLACK)
        background = buttonBackground(accent)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        minHeight = dp(46)
        AppFonts.apply(this, bold = true)
    }

    private fun buttonBackground(strokeColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.BLACK)
        setStroke(dp(1).coerceAtLeast(1), strokeColor)
        cornerRadius = dp(3).toFloat()
    }

    private fun outlinedBackground(selected: Boolean, strokeColor: Int = if (selected) PRIMARY else SECONDARY): GradientDrawable = GradientDrawable().apply {
        setColor(BLACK)
        setStroke(dp(1).coerceAtLeast(1), strokeColor)
        cornerRadius = dp(3).toFloat()
    }

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
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
}

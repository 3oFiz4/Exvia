package com.example.exp_tracker

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val PREFS = "exp_tracker_ui"
        private const val PREF_SELECTED_PATH = "selected_path"
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
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

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

                runOnUiThread {
                    files = loadedFiles
                    setSelectedPath(selected?.path)
                    renderFiles()
                    renderTable(rows)
                    statusText.text = if (selected == null) {
                        "No .json files found in ${RepoConfig.EXPENSE_FOLDER}/."
                    } else {
                        "Loaded ${rows.size} row(s) from ${selected.name}."
                    }
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Could not load files") }
            }
        }
    }

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
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Could not load selected file") }
            }
        }
    }

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
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { handleError(e, "Amend failed") }
            }
        }
    }

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
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(BLACK)
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
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Update failed") }
                    }
                }
            }
        }
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
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Delete failed") }
                    }
                }
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
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = "File name is required"
                    return@setOnClickListener
                }
                dialog.dismiss()
                setBusy(true, "Creating file…")
                executor.execute {
                    try {
                        val api = GitHubApi(token)
                        val created = api.createExpenseFile(name)
                        val loadedFiles = api.listExpenseFiles()
                        val rows = api.fetchExpenses(created.path)
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(created.path)
                            renderFiles()
                            renderTable(rows)
                            showTableTab()
                            statusText.text = "Created and selected ${created.name}."
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Create file failed") }
                    }
                }
            }
        }
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
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                setBusy(true, "Removing ${file.name}…")
                executor.execute {
                    try {
                        val api = GitHubApi(token)
                        api.deleteExpenseFile(file)
                        val loadedFiles = api.listExpenseFiles()
                        val next = loadedFiles.firstOrNull()
                        val rows = next?.let { api.fetchExpenses(it.path) }.orEmpty()
                        runOnUiThread {
                            files = loadedFiles
                            setSelectedPath(next?.path)
                            renderFiles()
                            renderTable(rows)
                            statusText.text = if (next == null) {
                                "Removed ${file.name}. No JSON files remain."
                            } else {
                                "Removed ${file.name}. Selected ${next.name}."
                            }
                            setBusy(false)
                        }
                    } catch (e: Exception) {
                        runOnUiThread { handleError(e, "Remove file failed") }
                    }
                }
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
                setOnClickListener {
                    if (busy) return@setOnClickListener
                    setSelectedPath(file.path)
                    renderFiles()
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
        updateTabButtons()
    }

    private fun updateTabButtons() {
        if (!::tableTabButton.isInitialized || !::filesTabButton.isInitialized) return
        styleTab(tableTabButton, active = !showingFiles)
        styleTab(filesTabButton, active = showingFiles)
    }

    private fun styleTab(button: Button, active: Boolean) {
        button.setTextColor(if (active) BLACK else WHITE)
        button.setBackgroundColor(if (active) WHITE else BLACK)
    }

    private fun setSelectedPath(path: String?) {
        selectedPath = path
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            if (path == null) remove(PREF_SELECTED_PATH) else putString(PREF_SELECTED_PATH, path)
        }.apply()
        if (::selectedFileText.isInitialized) {
            selectedFileText.text = path?.substringAfterLast('/') ?: "No file selected"
        }
    }

    private fun setBusy(isBusy: Boolean, message: String? = null) {
        busy = isBusy
        if (::amendButton.isInitialized) amendButton.isEnabled = !isBusy && selectedPath != null
        if (::priceInput.isInitialized) priceInput.isEnabled = !isBusy
        if (::tickerInput.isInitialized) tickerInput.isEnabled = !isBusy
        if (::descriptionInput.isInitialized) descriptionInput.isEnabled = !isBusy
        if (::tagsInput.isInitialized) tagsInput.isEnabled = !isBusy
        if (::createFileButton.isInitialized) createFileButton.isEnabled = !isBusy
        if (::removeFileButton.isInitialized) removeFileButton.isEnabled = !isBusy && selectedPath != null
        if (message != null && ::statusText.isInitialized) statusText.text = message
    }

    private fun handleError(error: Exception, prefix: String) {
        setBusy(false)
        if (error is GitHubHttpException && error.statusCode == 401) {
            tokenStore.clear()
            statusText.text = "$prefix: authentication failed. Enter a new token."
            promptForToken()
            return
        }

        val extra = when (error) {
            is GitHubHttpException -> when (error.statusCode) {
                409 -> " The branch/file changed; reload and try again."
                403 -> " Check token permissions or repository rules."
                404 -> " The selected folder/file was not found."
                else -> ""
            }
            else -> ""
        }
        statusText.text = "$prefix: ${error.message ?: error.javaClass.simpleName}.$extra"
    }

    private fun isRepoConfigured(): Boolean =
        !RepoConfig.OWNER.startsWith("YOUR_") && !RepoConfig.REPO.startsWith("YOUR_")
}

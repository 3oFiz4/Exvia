package xyz.x3ofiz4.exvia.presentation.widget

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView
import android.widget.TextView
import androidx.core.content.ContextCompat
import xyz.x3ofiz4.exvia.R
import xyz.x3ofiz4.exvia.app.ExviaApplication
import xyz.x3ofiz4.exvia.presentation.common.AppFonts

/**
 * Interactive native Android dialog activity triggered from the home screen widget.
 * Matches prototype/widget_idea.html pixel-perfect with native Android UI,
 * including dynamic columns, live autocompletion, auto-date population, and MVVM amend.
 */
class WidgetEntryActivity : Activity() {

    private lateinit var viewModel: WidgetViewModel
    private val formInputs = mutableMapOf<String, EditText>()
    private var renderedKeys: List<String> = emptyList()

    private lateinit var rootLayout: FrameLayout
    private lateinit var cardLayout: LinearLayout
    private lateinit var fileTextView: TextView
    private lateinit var inputsContainer: LinearLayout
    private lateinit var amendButton: Button
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_entry)

        rootLayout = findViewById(R.id.widget_entry_root)
        cardLayout = findViewById(R.id.widget_entry_card)
        fileTextView = findViewById(R.id.widget_entry_file)
        inputsContainer = findViewById(R.id.widget_entry_inputs)
        amendButton = findViewById(R.id.widget_entry_btn_amend)
        logTextView = findViewById(R.id.widget_entry_log)

        AppFonts.apply(fileTextView)
        AppFonts.apply(amendButton, bold = true)
        AppFonts.apply(logTextView)

        // Dismiss when tapping outside the card
        rootLayout.setOnClickListener { finish() }
        cardLayout.setOnClickListener { /* Consume click */ }

        val container = (applicationContext as ExviaApplication).container
        viewModel = WidgetViewModel(container)

        viewModel.state.observe { uiState ->
            runOnUiThread { render(uiState) }
        }

        amendButton.setOnClickListener {
            performAmend()
        }

        viewModel.loadInitial()
    }

    private fun render(state: WidgetUiState) {
        fileTextView.text = "--> ${state.targetFile}"
        logTextView.text = "log: ${state.logMessage}"
        logTextView.setTextColor(
            if (state.isLogSuccess) Color.parseColor("#86EFAC") else Color.parseColor("#F72323")
        )

        if (renderedKeys != state.keys) {
            renderedKeys = state.keys
            buildForm(state)
        }
    }

    private fun buildForm(state: WidgetUiState) {
        inputsContainer.removeAllViews()
        formInputs.clear()

        val focusTarget = intent.getStringExtra(ExpenseAppWidgetProvider.EXTRA_FOCUS_KEY)
        var viewToFocus: EditText? = null

        for (key in state.keys) {
            val isDate = key.equals(state.dateKey, ignoreCase = true)
            val isMoney = key.equals(state.moneyKey, ignoreCase = true)
            val isTags = key.equals(state.tagsKey, ignoreCase = true)

            val suggestions = state.suggestions[key] ?: emptyList()

            val input: EditText = if (isTags) {
                MultiAutoCompleteTextView(this).apply {
                    setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
                    if (suggestions.isNotEmpty()) {
                        setAdapter(createSuggestionAdapter(suggestions))
                    }
                }
            } else {
                AutoCompleteTextView(this).apply {
                    if (suggestions.isNotEmpty()) {
                        setAdapter(createSuggestionAdapter(suggestions))
                    }
                }
            }

            input.apply {
                hint = when {
                    isMoney -> "0.00"
                    isDate -> "date (optional)"
                    else -> "$key (optional)"
                }
                inputType = if (isMoney) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                } else {
                    InputType.TYPE_CLASS_TEXT
                }
                isSingleLine = true
                if (this is AutoCompleteTextView) {
                    threshold = 1
                }
                setTextColor(Color.parseColor("#EDEDED"))
                setHintTextColor(Color.parseColor("#7D7D7D"))
                background = ContextCompat.getDrawable(this@WidgetEntryActivity, R.drawable.widget_input_underline)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                minHeight = dp(38)
                AppFonts.apply(this)

                if (isDate) {
                    setText(state.currentDate)
                }
            }

            formInputs[key] = input

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            inputsContainer.addView(input, params)

            if (viewToFocus == null) {
                if (focusTarget != null && key.equals(focusTarget, ignoreCase = true)) {
                    viewToFocus = input
                } else if (focusTarget == null && isMoney) {
                    viewToFocus = input
                }
            }
        }

        viewToFocus?.let { target ->
            target.post {
                target.requestFocus()
                if (target.text.isNotEmpty()) {
                    target.setSelection(target.text.length)
                }
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun performAmend() {
        val rawValues = formInputs.mapValues { it.value.text.toString() }

        amendButton.isEnabled = false
        logTextView.text = "log: Amending…"
        logTextView.setTextColor(Color.parseColor("#86EFAC"))

        viewModel.amend(this, rawValues) { success, message ->
            runOnUiThread {
                amendButton.isEnabled = true
                if (success) {
                    logTextView.text = "log: $message"
                    logTextView.setTextColor(Color.parseColor("#86EFAC"))
                    amendButton.postDelayed({ finish() }, 600)
                } else {
                    logTextView.text = "log: $message"
                    logTextView.setTextColor(Color.parseColor("#F72323"))
                }
            }
        }
    }

    private fun createSuggestionAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(
            this, android.R.layout.simple_dropdown_item_1line, items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.parseColor("#EDEDED"))
                    setBackgroundColor(Color.parseColor("#1F1F1F"))
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    AppFonts.apply(this)
                }
            }
        }

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        viewModel.close()
    }
}

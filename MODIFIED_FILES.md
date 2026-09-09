# Exvia v1.13.7 modified and created files

## Modified

### `app/build.gradle.kts`
- Bumped `versionCode` to `24`.
- Bumped `versionName` to `"1.13.7"`.

### `app/src/main/AndroidManifest.xml`
- Registered `ExpenseAppWidgetProvider` receiver with `APPWIDGET_UPDATE` action and `@xml/expense_app_widget_info` metadata.
- Registered `WidgetEntryActivity` with `@style/WidgetEntryTheme` and soft input handling.

### `app/src/main/java/xyz/x3ofiz4/exvia/presentation/main/MainActivity.kt`
- Added call to `ExpenseAppWidgetProvider.updateAllWidgets(this, "Data amended")` on `event.amend` automation events.
- Added call to `ExpenseAppWidgetProvider.updateAllWidgets(this)` on data revisions / state changes.

### `app/src/main/res/values/strings.xml`
- Added `widget_name` ("Exvia Quick Amend") and `widget_description`.

### `app/src/main/res/values/styles.xml`
- Added `WidgetEntryTheme` translucent floating dialog style.

## Created

### Presentation / Widget
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/ExpenseAppWidgetProvider.kt`: AppWidgetProvider managing Home Screen widget RemoteViews, dynamic columns, click PendingIntents, and live updates.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetEntryActivity.kt`: Interactive native Android dialog activity with dynamic columns, auto-completer (`AutoCompleteTextView` / `MultiAutoCompleteTextView`), current date auto-population, JetBrains Mono typography, and Amend action.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetViewModel.kt`: MVVM ViewModel coordinating repository queries, schema evaluations, suggestion generation, and amendments.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetUiState.kt`: Immutable UI state representation for widget components.

### Resources & Layouts
- `app/src/main/res/xml/expense_app_widget_info.xml`: AppWidgetProviderInfo configuration.
- `app/src/main/res/layout/widget_expense_layout.xml`: Home screen RemoteViews layout matching `prototype/widget_idea.html`.
- `app/src/main/res/layout/widget_column_item.xml`: Layout for dynamic column rows in RemoteViews.
- `app/src/main/res/layout/activity_widget_entry.xml`: Layout for interactive native entry dialog.
- `app/src/main/res/drawable/bg_widget_card.xml`: Black background card with red border and rounded corners.
- `app/src/main/res/drawable/widget_input_underline.xml`: Red bottom-border underline for form inputs.
- `app/src/main/res/drawable/bg_widget_button.xml`: Black background button with red border and rounded corners.
- `app/src/main/res/drawable/bg_widget_button_pressed.xml`: Pressed state background for Amend button.
- `app/src/main/res/drawable/selector_widget_button.xml`: State selector for Amend button.

### Testing
- `app/src/test/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetViewModelTest.kt`: Unit tests verifying widget UI state defaults, date formatting, frequency-based suggestion computation, and path display.

### Specifications
- `V1.13.7_CHANGE_SPEC.md`: Full change specification for Exvia v1.13.7.

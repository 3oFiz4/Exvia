# Exvia v1.13.7 modified and created files

## Modified

### `app/build.gradle.kts`
- Bumped `versionCode` to `24`.
- Bumped `versionName` to `"1.13.7"`.

### `app/src/main/AndroidManifest.xml`
- Registered `ExpenseAppWidgetProvider` receiver with `APPWIDGET_UPDATE` action and `@xml/expense_app_widget_info` metadata.
- Registered `WidgetEntryActivity` with `@style/WidgetEntryTheme` and soft input handling.
- Registered `ExpenseWidgetRemoteViewsService` with `BIND_REMOTEVIEWS` permission for scrollable ListView.

### `app/src/main/java/xyz/x3ofiz4/exvia/presentation/main/MainActivity.kt`
- Added call to `ExpenseAppWidgetProvider.updateAllWidgets(this, "Data amended")` on `event.amend` automation events.
- Added call to `ExpenseAppWidgetProvider.updateAllWidgets(this)` on data revisions / state changes.

### `app/src/main/res/values/strings.xml`
- Added `widget_name` ("Exvia Quick Amend") and `widget_description`.

### `app/src/main/res/values/styles.xml`
- Added `WidgetEntryTheme` translucent floating dialog style.

### `app/src/main/res/drawable/bg_widget_card.xml`
- Set non-radial outer border (zero border-radius) with red stroke `#F72323` and solid black background.

### `app/src/main/res/layout/widget_expense_layout.xml`
- Replaced static container with vertical scrollable `ListView` (`@id/widget_fields_list`) and empty state view.

## Created

### Presentation / Widget
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/ExpenseAppWidgetProvider.kt`: AppWidgetProvider managing Home Screen widget RemoteViews, scrollable list configuration, click PendingIntents, and live updates.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/ExpenseWidgetRemoteViewsService.kt`: RemoteViewsService and Factory dynamically generating all possible fields for the target file in the widget ListView.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetEntryActivity.kt`: Interactive native Android dialog activity with dynamic columns, auto-completer (`AutoCompleteTextView` / `MultiAutoCompleteTextView`), current date auto-population, JetBrains Mono typography, and Amend action.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetViewModel.kt`: MVVM ViewModel coordinating repository queries, schema evaluations, suggestion generation, and amendments.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetUiState.kt`: Immutable UI state representation for widget components.

### Resources & Layouts
- `app/src/main/res/xml/expense_app_widget_info.xml`: AppWidgetProviderInfo configuration.
- `app/src/main/res/layout/widget_expense_layout.xml`: Home screen RemoteViews layout matching `prototype/widget_idea.html` with non-radial border and scrollable field list.
- `app/src/main/res/layout/widget_column_item.xml`: Layout for ListView field item rows in RemoteViews.
- `app/src/main/res/layout/activity_widget_entry.xml`: Layout for interactive native entry dialog with ScrollView.
- `app/src/main/res/drawable/bg_widget_card.xml`: Non-radial black card with red border.
- `app/src/main/res/drawable/widget_input_underline.xml`: Red bottom-border underline for form inputs.
- `app/src/main/res/drawable/bg_widget_button.xml`: Black background button with red border.
- `app/src/main/res/drawable/bg_widget_button_pressed.xml`: Pressed state background for Amend button.
- `app/src/main/res/drawable/selector_widget_button.xml`: State selector for Amend button.

### Testing
- `app/src/test/java/xyz/x3ofiz4/exvia/presentation/widget/WidgetViewModelTest.kt`: Unit tests verifying widget UI state defaults, date formatting, frequency-based suggestion computation, path display, and field item mappings.

### Specifications
- `V1.13.7_CHANGE_SPEC.md`: Full change specification for Exvia v1.13.7.

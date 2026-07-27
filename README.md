<<<<<<< HEAD
<img width="270" height="600" alt="Screenshot_2026-07-27-12-18-33-819_com example exp_tracker" src="https://github.com/user-attachments/assets/f6872adc-246d-423b-be1a-19ec83de9ff0" />
<img width="270" height="600" alt="Screenshot_2026-07-27-12-18-28-953_com example exp_tracker" src="https://github.com/user-attachments/assets/579140af-2640-48b4-b2e6-6a7e0f53b513" />

# exp_tracker

Minimal Android expense editor backed directly by JSON files in a GitHub repository.

The app uses GitHub's REST Contents API. A write replaces the selected JSON file using its latest SHA, and GitHub creates the commit directly on `main`. No Git executable or local `.git` directory is stored on the phone.

# Installation
1. Create your own GitHub repository, and ensure it is private, and your GitHub PAT is initialized
2. Within the GitHub repository, initialize a folder within, it can be anything, I personally created `Financial/`
3. Within the folder, create your first expenses `.json`. It track any .json file under `Financial/`, I personally name it this way `@MMMM_expenses.json`
4. For the template, copy and paste this into the expenses file:
```
[
  {
    "date": "Test",
    "price": "999",
    "code": "Food",
    "description": "coffee"
  }
]
```
5. Ensure every changes it commited, now git clone this repository on your Android Studio `git clone https://github.com/3oFiz4/exp_tracker`
6. Go to `app/src/main/java/com/example/exp_tracker/RepoConfig.kt` and modify the configuration, as to what we did before.
7. Build the application via command line
8. Install the apk
9. When you open the app for the first time, it will require you to input your GitHub PAT, copy your GitHub PAT and paste it there. This is the "password" or "token" to access your repository, because it is private.
10. Wait for a minute, and the file will be loaded

## Current behavior

- AMOLED UI: `#000000` background and `#FFFFFF` base text.
- Bottom tabs: **Table** and **Files**.
- Table displays only the currently selected JSON file from `Financial/`.
- Columns: DATE, PRICE, TICKER, DESCRIPTION, TAGS.
- New rows use the Android device's local date/time in `d/M/yy @ HH:mm`, for example `3/7/26 @ 14:05`.
- Existing dates such as `3/7/26` remain supported and sortable.
- Table order is newest -> oldest. A date without a time is treated as midnight for sorting.
- A price beginning with `+` is green. A price without `+` is red.
- Row create/edit/delete and file create/delete commit directly to GitHub.
- TICKER, DESCRIPTION, and TAGS provide autocomplete learned from the currently selected file.
- Autocomplete suggestions are ranked by how often a value appears.
- Tags are edited as `non_cash, food, big`, but stored in the repository as the string `['non_cash', 'food', 'big']`.
- Ticker/category colors are configurable in `RepoConfig.kt`.
- Table rows use one physical pixel of spacing.

## 1. Configure the repository

Open:

`app/src/main/java/com/example/exp_tracker/RepoConfig.kt`

Set:

```kotlin
const val OWNER = "YOUR_GITHUB_USERNAME_OR_ORG"
const val REPO = "YOUR_REPOSITORY"
const val BRANCH = "main"
const val EXPENSE_FOLDER = "Financial"
const val DEFAULT_JSON = "expenses.json"
```

The Files tab lists `.json` files directly inside `Financial/`. Selecting one makes it the active Table file and the destination for **Amend**.

## 2. Configure ticker colors

```kotlin
val TICKER_COLORS = mapOf(
    "FD" to "#FFB300",
    "BVG" to "#29B6F6",
)
const val DEFAULT_TICKER_COLOR = "#FFFFFF"
```

Matching is case-insensitive.

## 3. JSON format

Example:

```json
[
  {
    "date": "3/7/26 @ 14:05",
    "price": "12.5",
    "ticker": "FD",
    "description": "Lunch",
    "tags": "['non_cash', 'food']"
  },
  {
    "date": "3/7/26",
    "price": "+50",
    "ticker": "REFUND",
    "description": "Refund",
    "tags": "['cashback']"
  }
]
```

The app also accepts an object containing an `expenses` array.

Existing numeric `price` values are readable. New/edited prices are strings so an explicit leading `+` can be preserved.

For tags, the expected repository value is a JSON string containing a Python-style list:

```json
"tags": "['non_cash', 'food', 'big']"
```

The app displays that as:

```text
non_cash, food, big
```

It also tolerates a real JSON array for reading, such as `"tags": ["non_cash", "food"]`; editing the row converts it to the string form above.

Schema keys are configurable:

```kotlin
const val DATE_KEY = "date"
const val PRICE_KEY = "price"
const val TICKER_KEY = "ticker"
const val DESCRIPTION_KEY = "description"
const val TAGS_KEY = "tags"
```

## 4. Date and sorting

New rows use local device time:
=======
# exp_tracker v1.7

Minimal schema-driven Android JSON editor backed by a GitHub repository. The default theme remains AMOLED, with optional Ayu and light presets.

The app uses GitHub's REST Contents API. Each create/update/delete replaces the selected JSON file using the latest SHA; GitHub creates the commit directly on the configured branch.

## Major change: schema-driven UI

The app no longer assumes fields such as PRICE/TICKER/DESCRIPTION/TAGS in the input form.

If the selected file contains:

```json
[
  {"name": "A", "tor": "1", "legend": "x"},
  {"name": "B", "tor": "2", "legend": "y"}
]
```

the Table input form and table columns become:

```text
name | tor | legend
```

The schema is the union of keys found across all object rows, preserving the discovered order as much as the JSON parser allows. Every field is optional. Blank form fields are omitted from the new JSON object.

For an empty `[]` file there is nothing to infer, so **+ Add field** lets you seed fields manually. Once a row is committed with that field, it is inferred normally on future loads.

## Tabs

The bottom navigation contains:

- **Table** — inferred inputs, current-file table, row CRUD.
- **Stat** — per-key statistics, cumulative distribution box timelines, and finance metrics.
- **Files** — select/create/delete `.json` files in the configured folder.

The default theme is AMOLED and uses the configurable six-role palette described below. Money values beginning with `+` remain green; ordinary money values remain red as a semantic finance signal. Ticker/category colors remain configurable.


## Theme system

Settings now separates configuration into accordions:

- **GitHub** — owner/org, repository, branch, JSON folder, default file, and masked PAT.
- **Color** — theme preset and the six editable palette roles.
- **Schema & Display** — object-array fallback, inferred-key overrides, and ticker/category color mapping.

The initial **Default theme** palette is:

```text
Primary     #F72323   rgb(247, 35, 35)
Secondary   #CC0000   rgb(204, 0, 0)
Tertiary    #000000   rgb(0, 0, 0)
Quaternary  #1F1F1F   rgb(31, 31, 31)
Quinary     #7D7D7D   rgb(125, 125, 125)
Senary      #EDEDED   rgb(237, 237, 237)
```

The roles are applied by visual priority rather than using red everywhere:

- **Primary** is reserved for active/focused states, focused input underlines, selected navigation, and destructive affordances. Statistical graphs use distinct semantic colors so mean/median/quartiles/spread remain visually separable.
- **Secondary** marks inactive outlines and lower-priority boundaries.
- **Tertiary** is the main app background.
- **Quaternary** is the low-contrast surface/grid/dropdown color that separates groups without competing with data.
- **Quinary** is muted information: hints, table headers, status text, axes, and inactive navigation text.
- **Senary** is normal high-legibility text.

Buttons intentionally do **not** fill with Primary. Buttons and bottom-navigation controls use a black fill with Primary or Secondary borders. This keeps the strongest red as an attention/focus signal rather than turning every action into a visual alarm.

Available presets are **Ayu**, **Default theme**, **Ayu-Light**, and **Default theme-light**. Choosing a preset fills the six color fields; every field remains editable as `#RRGGBB` or Android-style `#AARRGGBB`. Saving Settings recreates the activity so all surfaces, system-bar icon contrast, charts, and controls pick up the new palette.

### JetRoboto

All app-created text, dialog text/buttons, autocomplete rows, and graph labels use JetRoboto through `AppFonts`.

Place your font at:

```text
app/src/main/assets/fonts/JetRoboto.ttf
```

The repository intentionally does not include a font file. Until you place the real TTF there, the app falls back to Android sans-serif, so the project remains buildable.

### Interactive cumulative timestamp-total box timelines

Numeric keys use an interactive cumulative statistical candlestick/box timeline. **Before any statistics are calculated, rows with the same parsed datetime are grouped and their numeric values are summed.** For the inferred money key (`PRICE`, `amount`, etc.), the blue observation marker therefore represents the **total PRICE at that exact datetime**, not an individual expense row.

Example:

```text
3/7/26 @ 10:00   5
3/7/26 @ 10:00   7
3/7/26 @ 11:00   2
```

becomes timestamp observations:

```text
3/7/26 @ 10:00  -> 12
3/7/26 @ 11:00  -> 2
```

The cumulative distributions are then `[12]`, followed by `[12, 2]`. The same grouping rule is applied to any other numeric key shown in Stat.

For every timestamp snapshot:

- **Q1 → Q3** is a fully opaque box. There is no transparency.
- **Box + whisker direction color:** current timestamp total `>` previous timestamp total is **green**; current `<=` previous is **red**.
- The **first** timestamp has no previous value, so its box/whiskers use a neutral theme-axis color.
- **Median / Q2** is a solid horizontal high-contrast line. Its tint follows the red/green box family but is deliberately near-white for legibility.
- **Mean** is another horizontal line at the mean, but dotted. It uses the same contrast family as the median.
- **Mean ± 1 population STDV** is the vertical whisker with caps, using exactly the same red/green/neutral direction color as the box.
- **Outliers** are hollow amber circles (`○`) using Tukey fences: below `Q1 - 1.5 × IQR` or above `Q3 + 1.5 × IQR`.
- The **actual timestamp observation** is a filled blue rectangle (`■`). It is the sum of every numeric row at that parsed datetime. Its blue color does not change with box direction, so the observed total remains visually distinct.
- If that timestamp total is itself an outlier, the hollow circle and blue rectangle are both rendered at the same Y value.

Sparse dates are supported intentionally. The chart does **not** synthesize missing dates or pretend observations are evenly spaced: each X coordinate uses its real parsed epoch timestamp, so missing dates become visible gaps. X-axis labels wrap into two lines (`d/M/yy` over `HH:mm`) and are thinned automatically to reduce collisions when many timestamps are visible. Rows whose date is missing or cannot be parsed are excluded only from the timeline and reported in the graph note; they remain available to the rest of the table/stat calculations.

Touch navigation:

- **Pinch** to zoom, up to 40×.
- **Drag** to pan through the zoomed viewport.
- **Double-tap** to reset to the full data range.
- **Tap a box** to inspect timestamp total, number of raw rows merged into that timestamp, cumulative timestamp count, Q1, median, mean, Q3, σ, outlier count, and whether the current timestamp total is itself an outlier.

The graph captures touch gestures while it is manipulated so the surrounding Stat `ScrollView` does not steal pinch/pan gestures.

## Date handling

The app recognizes existing dates such as:

```text
3/7/26
```

and timestamped values such as:
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)

```text
3/7/26 @ 14:05
```

<<<<<<< HEAD
Supported sorting formats include:

- `3/7/26 @ 14:05`
- `3/7/26`
- older ISO timestamps written by previous app versions

A date-only row is interpreted as `00:00` on that date for sorting. Unparseable or blank dates go to the bottom.

## 5. Autocomplete

Autocomplete is rebuilt whenever the selected JSON file loads or changes.

- **TICKER:** unique ticker values, most frequent first.
- **DESCRIPTION:** unique descriptions, most frequent first.
- **TAGS:** individual tags, most frequent first. Tags are comma-separated, so autocomplete continues after each comma.

For example, if `FD` occurs 40 times and `BVG` occurs 10 times, `FD` is ranked before `BVG` in TICKER suggestions.

Autocomplete is based only on the currently selected file, so each month's file can develop its own relevant suggestions.

## 6. Amend behavior

PRICE is required. TICKER, DESCRIPTION, and TAGS are optional.

Accepted prices include `12`, `12.50`, `+12`, and `+12.50`. Negative prices are rejected.

For a new row, the commit message remains:

```text
Expense at {{date}}: {{PRICE}}, ({{TICKER}}) {{DESCRIPTION}}
```

Example:

```text
Expense at 3/7/26 @ 14:05: 12.5, (FD) Lunch
```

Tags are stored in the row but intentionally do not change this commit-message format.

## 7. Row CRUD

Create: enter PRICE and any optional TICKER/DESCRIPTION/TAGS, then press **Amend**.

Read: the selected file is parsed into the Table.

Update: tap any DATE, PRICE, TICKER, DESCRIPTION, or TAGS cell. The edit dialog supports the same autocomplete for ticker, description, and tags.

Delete: tap `×` and confirm.

## 8. Files tab

The Files tab manages JSON files under `Financial/`.

Selecting a file makes it active, loads only it into Table, and switches back to Table.

**Create** creates an empty JSON array. The suggested filename is next month as `YYYY-MM.json`; you can replace it with another name.

**Remove selected** deletes the selected JSON file after confirmation.

## 9. GitHub token

Use a fine-grained personal access token with repository **Contents: Read and write** permission.

The app requests it at runtime and encrypts it using Android Keystore. Do not hardcode the token into the source.

## 10. Build

1. Unzip the project.
2. Open the project folder in Android Studio.
3. Let Gradle sync.
4. Set `RepoConfig.kt`.
5. Build a debug APK with **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**, or run:
=======
If a date key is inferred, the Amend form pre-fills it with current local device time in:

```text
d/M/yy @ HH:mm
```

The table sorts newest to oldest using the inferred date key. Supported date parsing also retains compatibility with ISO timestamps from older app versions.

Date-key inference checks common names such as `date`, `datetime`, `timestamp`, `time`, and `created_at`. You can override it in Settings.

## Dynamic autocomplete

Autocomplete is now available for every inferred field and is learned from the currently selected JSON file.

Values are ranked by occurrence count, most frequent first. A detected Tags key uses comma-token autocomplete so each individual tag is suggested separately.

For example, repository storage:

```json
"tags": "['non_cash', 'food', 'big']"
```

is shown and edited as:

```text
non_cash, food, big
```

and is converted back to the string-list format when saved.

Tags-key inference checks `tags`, `tag`, `labels`, and `label`, or you can override the key in Settings.

## Stat tab

Each JSON key gets an accordion. Numeric keys show the cumulative box timeline; every key also shows:

- Mean
- Median
- Mean − Median gap
- Mode
- Sum
- STDV
- Minimum
- Maximum
- Range
- Q1
- Q2
- Q3
- IQR
- Skew
- Kurtosis
- n
- n unique
- Variance

Numeric strings are treated as numbers for statistics, including values with a leading `+`.

For non-numeric keys, numeric statistics show `N/A`, while Mode, n, and n unique remain useful.

Implementation details:

- Quartiles use linear interpolation at `p * (n - 1)`.
- STDV and Variance are population measures (`/ n`).
- Skew is the population standardized third moment.
- Kurtosis is excess population kurtosis (`fourth standardized moment - 3`).
- Mode is `N/A` when every value occurs only once, except for a one-item dataset.
- **Mean − Median gap** is signed as `mean - median`; a negative gap is red, a positive gap is green.
- Negative numeric statistic values are red. Mean, median, quartile, spread, and shape metrics otherwise use distinct semantic colors for faster scanning.

### Graphs

The X axis is the inferred date key and increases left → right using the **real datetime spacing**. For every numeric key, rows sharing the same parsed timestamp are summed first. The cumulative box at each X position summarizes the sequence of timestamp totals up to and including that point.

For example, when the money key is `PRICE`, three expense rows entered at one exact datetime produce **one** blue observation rectangle whose Y value is their total. The next timestamp compares its total against the previous timestamp total to choose green (`>`) or red (`<=`) box/whisker direction.

The graph intentionally does not fill missing dates. This prevents a sparse month from looking like a continuous daily series. Date/time labels are wrapped to two lines and a subset is displayed when needed to prevent overlap.

Categorical/string keys do not receive an ordinal box plot because quartiles/mean of arbitrary category indices would be misleading. Their accordions still show Mode, n, and n unique. The date key itself is the X axis and is therefore not plotted as a distribution.

If no date key can be inferred, the accordion explains that a Date key override can be set in Settings. Missing/unparseable dates and dated non-numeric rows are omitted from that key's timeline and reported in its graph note.

## Personal finance metrics

When a money-like key is inferred, the Stat tab also includes a **Personal finance** accordion.

Money-key inference checks common names such as `price`, `amount`, `cost`, `expense`, `value`, `total`, and `money`. It can be overridden in Settings.

The finance section shows:

- Total income
- Total expenses
- Net cash flow
- Savings rate
- Average expense
- Average income
- Largest expense
- Largest income
- Transaction count
- Earliest → latest date
- Spending by inferred ticker/category, when available

The existing sign convention is retained:

- `+50` = inflow/income
- `50` = outflow/expense
- negative numeric values, if already present in a file, are treated as outflow by absolute magnitude

If the file uses another convention, these finance metrics may not represent the intended semantics; use a compatible money column/convention or leave the finance section informational.

## Settings drawer

Swipe **left → right from the left edge** to open Settings. You can also tap `Settings ›` at the top.

Settings can change without rebuilding the APK. GitHub, Color, and Schema/Display options are grouped into separate accordions. This includes repository configuration, inferred-key overrides, the masked PAT, ticker/category mappings, theme preset, and all six palette values.

The PAT input never displays the stored token. When a PAT exists, the field shows a masked `************` hint. Leaving the field blank keeps the current encrypted token. **Clear stored PAT** removes it.

The token continues to be encrypted locally using Android Keystore and is not stored in the repository settings preferences.

### Ticker/category color mapping

Use one mapping per line:

```text
FD=#FFB300
BVG=#29B6F6
REFUND=#34C759
```

Matching is case-insensitive. Unmapped values use white.

## JSON roots

Supported root formats are:

```json
[
  {"date": "3/7/26", "amount": "12"}
]
```

or an object containing an array:

```json
{
  "expenses": [
    {"date": "3/7/26", "amount": "12"}
  ]
}
```

For an object root, the app first tries the configured **Object array key** (default `expenses`). If it is absent, it uses the first array property it finds.

## Table CRUD

- **Create:** fill any inferred fields and tap **Amend**.
- **Read:** select a file from Files.
- **Update:** tap any table cell, edit any field, and Save.
- **Delete:** tap `×` and confirm.

Blank values are omitted when a row is written. Numeric JSON columns remain numeric when the existing column is purely numeric; a value beginning with `+` is stored as a string so the plus sign is preserved.

The table keeps one physical pixel between rows.

## Files

The Files tab lists `.json` files directly in the configured folder, defaulting to `Financial/`.

- Selecting a file loads it into Table and Stat.
- Create suggests next month as `YYYY-MM.json` and creates `[]`.
- Remove selected asks for confirmation and commits deletion.

Git has no empty folders, so if the final file is deleted the app treats a missing configured folder as an empty file list and still allows a new file to be created.

## Commit messages

For schemas where a money/ticker/description-like field can be inferred, new rows keep the expense-style message:

```text
Expense at {{date}}: {{money}}, ({{ticker}}) {{description}}
```

For fully unrelated schemas, Amend uses a generic message such as:

```text
Amend data.json at 3/7/26 @ 14:05
```

Edits and removals use concise update/removal commit messages.

## GitHub PAT

Use a fine-grained GitHub personal access token with repository **Contents: Read and write** access to the target repository.

Do not hardcode a PAT into source control. Enter it from the Settings drawer.

## Build

1. Open the project folder in Android Studio.
2. Let Gradle sync.
3. Run the app or build an APK.
4. On first launch, swipe right from the left edge and configure the repository and PAT.

Windows terminal:
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)

```powershell
.\gradlew.bat assembleDebug
```

<<<<<<< HEAD
The APK is normally at:
=======
macOS/Linux:

```bash
./gradlew assembleDebug
```

The debug APK is normally:
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)

```text
app/build/outputs/apk/debug/app-debug.apk
```

<<<<<<< HEAD
Minimum Android version: Android 8.0 / API 26.

## Validation note

This environment cannot download Gradle dependencies and does not have a configured Android SDK, so a definitive Android build could not be run here. Android Studio should perform the final SDK/AGP compilation on your machine.
=======
Minimum Android version remains Android 8.0 / API 26.

## Validation note

The pure Kotlin statistics/models layer and the GitHub API layer have been compiler-checked here with lightweight Android/JSON stubs. XML and archive integrity are also checked before packaging. This environment does not have a complete Android SDK installation, so Android Studio on your machine remains the definitive full Android build check.
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)

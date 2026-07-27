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

```text
3/7/26 @ 14:05
```

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

```powershell
.\gradlew.bat assembleDebug
```

macOS/Linux:

```bash
./gradlew assembleDebug
```

The debug APK is normally:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version remains Android 8.0 / API 26.

## Validation note

The pure Kotlin statistics/models layer and the GitHub API layer have been compiler-checked here with lightweight Android/JSON stubs. XML and archive integrity are also checked before packaging. This environment does not have a complete Android SDK installation, so Android Studio on your machine remains the definitive full Android build check.

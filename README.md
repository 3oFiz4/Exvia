<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-49-277_com example exp_tracker" src="https://github.com/user-attachments/assets/e417c56f-1fd5-41bd-bbe3-1b6430d128b8" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-44-433_com example exp_tracker" src="https://github.com/user-attachments/assets/8ade00a9-a140-4b4d-a73f-02d709558bd1" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-21-522_com example exp_tracker" src="https://github.com/user-attachments/assets/d891fdde-f4d8-4d39-9374-05e0fcfaaaa9" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-11-768_com example exp_tracker" src="https://github.com/user-attachments/assets/88162336-62d9-4f94-9b5e-76b800f04b45" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-44-56-760_com example exp_tracker" src="https://github.com/user-attachments/assets/5de0cf18-48ee-4b0e-8f8e-bc3c10aa9d82" />
<img width="216" height="480" alt="Screenshot_2026-07-27-20-54-26-980_com example exp_tracker" src="https://github.com/user-attachments/assets/4eb9d212-0926-4046-9fc3-b506b882b48f" />
<img width="216" height="480" alt="Screenshot_2026-07-27-20-54-17-746_com example exp_tracker" src="https://github.com/user-attachments/assets/33aa3094-fc28-4ff5-b6d3-367ab9c6fcdf" />
<img width="216" height="480" alt="Screenshot_2026-07-27-20-54-09-403_com example exp_tracker" src="https://github.com/user-attachments/assets/7b625048-0fcd-4e15-8808-29696acbb826" />



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

## v1.8 changes

### Graph styling

The cumulative statistical timeline still groups rows by exact parsed datetime before computing statistics. If several rows share `3/7/26 @ 10:00`, their numeric values are summed and that sum becomes the observation for that timestamp.

For each cumulative snapshot:

- Q1→Q3 is an opaque box.
- Current timestamp total `>` previous total: green box + whiskers.
- Current timestamp total `<=` previous total: red box + whiskers.
- First box is neutral because it has no prior observation.
- Median is a solid high-contrast horizontal line.
- Mean is a dotted high-contrast horizontal line.
- Whiskers are `mean ± 1 population σ`.
- Tukey outliers (`Q1 - 1.5×IQR`, `Q3 + 1.5×IQR`) are **tiny red hollow circles**.
- The actual aggregated timestamp observation is a **blue, slightly wide rhombus/diamond**.
- A **40%-opacity blue line** connects the timestamp observations from left to right.
- Extra X padding is reserved so the first and last box/observation are not half-clipped by the plot bounds.
- Missing dates are not synthesized. Real time gaps remain real gaps.
- X labels wrap as `d/M/yy` above `HH:mm` and are thinned when necessary.

The box plot remains interactive: pinch to zoom, drag to pan, double-tap to reset, and tap a box to inspect its cumulative values.

### Additional PRICE plots

When a money-like key such as `PRICE`, `amount`, or `cost` is inferred, its Stat accordion also includes:

1. **Cumulative PRICE** — an interactive running sum over timestamp-level totals.
2. **Normal distribution of PRICE** — an interactive fitted normal probability-density curve using the currently displayed numeric PRICE subset. Small ticks at the bottom show the observed PRICE samples.

These plots use the same filtered dataset as the table and all statistics.

### Table filtering

A compact filter row appears directly below **Amend**, separated by a margin to reduce accidental taps. It contains a one-line query field and `Filter ON/OFF` control.

When filtering is ON, the filtered subset replaces the table **and** becomes the source for Stat, Personal finance, cumulative plots, normal-distribution plots, and per-key metrics. Turning filtering OFF immediately restores the original selected JSON dataset.

The filter accepts a SQLite-style WHERE subset. You can type the full compact form:

```sql
SELECT * WHERE price >= 10 AND ticker = 'FD'
```

or only the WHERE expression:

```sql
price >= 10 AND ticker = 'FD'
```

Also accepted:

```sql
SELECT * FROM rows WHERE description LIKE '%coffee%'
SELECT * WHERE ticker IN ('FD', 'BVG')
SELECT * WHERE tags IS NOT NULL
SELECT * WHERE REGEX(description, '(?i)food|lunch')
SELECT * WHERE description REGEXP '(?i)coffee|tea'
SELECT * WHERE NOT (ticker = 'FD' OR price < 5)
```

Supported filter features:

- `AND`, `OR`, `NOT`, parentheses
- `=`, `!=`, `<>`, `>`, `>=`, `<`, `<=`
- `LIKE` with `%` and `_`
- `IN (...)`
- `IS NULL`, `IS NOT NULL`
- `REGEX(value, pattern)` / `REGEXP(value, pattern)`
- `column REGEX 'pattern'` / `column REGEXP 'pattern'`
- quoted strings
- backtick or `[bracket]` quoted column names

This is intentionally a safe WHERE-expression evaluator over the loaded JSON rows, not an arbitrary SQLite execution console: it does not execute mutations, JOINs, DDL, subqueries, GROUP BY, etc. Numeric-looking values compare numerically; other values compare as case-insensitive strings.

If an enabled query is syntactically invalid, the table/stat subset is temporarily empty and the status area reports the filter error rather than silently showing unfiltered data.

### Amend confirmation

**Amend** now opens a Yes/No confirmation dialog with a preview of the nonblank values before any GitHub write occurs.

### Button treatment

Buttons are shorter and visually quieter:

- no border while idle
- black/theme-background surface remains unchanged
- a border appears only for an active state such as pressed/focused/selected
- active bottom tab, selected file, enabled filter, and expanded accordion keep an active border
- collapsed accordions and inactive navigation have text only, with no outline

Dialog buttons use the same compact active-on-focus/press treatment.

### Font

The intended font is **JetBrains**, not JetRoboto.

Place your TTF at:

```text
app/src/main/assets/fonts/JetBrains.ttf
```

If absent, the app falls back to Android sans-serif so the project remains buildable.

## Existing schema-driven behavior

The app does not hardcode PRICE/TICKER/DESCRIPTION/TAGS fields. Inputs and table columns are inferred from the union of object keys in the selected JSON file. Every field is optional.

Example:

```json
[
  {"name":"A","tor":"1","legend":"x"},
  {"name":"B","tor":"2","legend":"y"}
]
```

produces inputs/table columns for `name`, `tor`, and `legend`.

For a new empty `[]` file, use **+ Add field** to seed a schema.

Common key names still receive special behavior automatically:

- date: `date`, `datetime`, `timestamp`, `time`, `created_at`, ...
- money: `price`, `amount`, `cost`, `expense`, `value`, `total`, `money`
- ticker/category: `ticker`, `category`, `code`, `type`
- tags: `tags`, `tag`, `labels`, `label`

Each can be overridden in Settings.

## Dates

Existing values such as:

```text
3/7/26
```

and:

```text
3/7/26 @ 14:05
```

are parsed. New dated rows use local device time in `d/M/yy @ HH:mm` when the inferred date field is blank.

The table sorts newest → oldest when a date key is available. Sparse/missing dates do not break plotting; unparseable/missing dates are omitted from time plots and reported in their graph notes.

## Tags

A tags value stored as:

```json
"tags": "['non_cash', 'food', 'big']"
```

is displayed/edited as:

```text
non_cash, food, big
```

and converted back to the repository string-list representation when written.

## Autocomplete

Autocomplete is inferred from values in the selected file and ranked by occurrence count. Ticker/category, descriptions, generic dynamic fields, and individual comma-separated tags can all use learned suggestions.

## Statistics

Each key has an accordion containing applicable metrics:

- Mean
- Median
- Mean − Median gap
- Mode
- Sum
- STDV
- Minimum
- Maximum
- Range
- Q1 / Q2 / Q3
- IQR
- Skew
- Kurtosis
- n
- n unique
- Variance

Negative numeric statistics are red; other statistic families use distinct semantic colors.

Implementation details:

- quartiles use linear interpolation at `p × (n - 1)`
- variance/STDV are population measures (`/ n`)
- skew is population standardized third moment
- kurtosis is excess population kurtosis

Categorical keys retain useful Mode/n/n-unique results and do not receive meaningless ordinal box plots.

## Personal finance metrics

When a money key is inferred, the Personal finance accordion includes total income, total expenses, net cash flow, savings rate, averages, largest values, transaction count, date period, and category spending.

Current sign convention:

- `+50` = income/inflow
- `50` = expense/outflow

The table also colors explicit `+` money values green and ordinary money values red.

## Tabs and files

Bottom tabs:

- **Table** — schema-driven inputs, compact filter, table CRUD
- **Stat** — finance metrics, cumulative box timeline, cumulative PRICE plot, normal PRICE distribution, per-key statistics
- **Files** — select/create/remove `.json` files under the configured folder (default `Financial/`)

Create suggests next month as `YYYY-MM.json`. File removal asks for confirmation. If the last file is removed, a missing Git folder is treated as an empty list so you can immediately create another JSON file.

## Settings drawer

Swipe left → right from the left edge, or tap `Settings ›`.

Settings remain grouped into accordions:

- **GitHub** — owner/org, repository, branch, JSON folder, default file, masked PAT
- **Color** — theme preset plus six configurable palette roles
- **Schema & Display** — inferred-key overrides and ticker/category color mappings

The GitHub PAT is stored through Android Keystore and is never displayed back in plaintext. A stored PAT is shown only as a masked hint.

Available theme presets:

- Default theme
- Ayu
- Ayu-Light
- Default theme-light

Default six-role palette:

```text
Primary     #F72323
Secondary   #CC0000
Tertiary    #000000
Quaternary  #1F1F1F
Quinary     #7D7D7D
Senary      #EDEDED
```

## GitHub writes

The app uses GitHub's Contents API rather than embedding a Git client. File replacement with the latest SHA creates the repository commit directly on the configured branch.

Use a fine-grained PAT with repository **Contents: Read and write** permission. Do not hardcode the token in source control.

## Build

Open the project folder in Android Studio, let Gradle sync, then run or build an APK.

Windows:

```powershell
.\gradlew.bat assembleDebug
```

macOS/Linux:

```bash
./gradlew assembleDebug
```

Expected debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android: API 26 / Android 8.0.

## Validation note

The new pure-Kotlin filtering/statistics logic was compiler-tested, including numeric comparisons, LIKE, IN, REGEX, duplicate-timestamp accumulation, and normal-curve generation. Kotlin source was also checked for syntax-level errors and the packaged project is integrity-checked. A full Android Gradle build cannot be completed in this environment because the Gradle distribution/Android SDK cannot be downloaded here; Android Studio on your machine remains the definitive full build check.

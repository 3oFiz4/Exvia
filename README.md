<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-49-277_com example exp_tracker" src="https://github.com/user-attachments/assets/e417c56f-1fd5-41bd-bbe3-1b6430d128b8" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-44-433_com example exp_tracker" src="https://github.com/user-attachments/assets/8ade00a9-a140-4b4d-a73f-02d709558bd1" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-21-522_com example exp_tracker" src="https://github.com/user-attachments/assets/d891fdde-f4d8-4d39-9374-05e0fcfaaaa9" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-46-11-768_com example exp_tracker" src="https://github.com/user-attachments/assets/88162336-62d9-4f94-9b5e-76b800f04b45" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-44-56-760_com example exp_tracker" src="https://github.com/user-attachments/assets/5de0cf18-48ee-4b0e-8f8e-bc3c10aa9d82" />
<img width="216" height="480" alt="Screenshot_2026-07-27-18-45-19-762_com example exp_tracker" src="https://github.com/user-attachments/assets/62b28dcd-f8dd-453f-bcc6-f73cb62cbb11" />


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

```text
3/7/26 @ 14:05
```

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

```powershell
.\gradlew.bat assembleDebug
```

The APK is normally at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: Android 8.0 / API 26.

## Validation note

This environment cannot download Gradle dependencies and does not have a configured Android SDK, so a definitive Android build could not be run here. Android Studio should perform the final SDK/AGP compilation on your machine.

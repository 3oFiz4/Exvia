# Exvia v1.11 modified and created files

## Modified

### `app/src/main/java/com/example/exp_tracker/MainActivity.kt`

- Added triple-tap Developer Options toggle.
- Added regular-mode Filtering Method selector that hides query syntax.
- Added Interface scaling fields.
- Added Report dialog and GitHub Issue submission flow.
- Made advanced Settings sections conditional on Developer Options.
- Collapsed built-in filtering and custom-metric examples.
- Changed Save settings to synchronize the hidden config file before reload.
- Added a defensive Files-section filter for dot-prefixed/config JSON files.
- Applied UI scale to density-based dimensions and text scale to the view tree/dialogs.

### `app/src/main/java/com/example/exp_tracker/Models.kt`

- Added `reportRepo`, `uiScale`, and `textScale` to `RepoSettings`.

### `app/src/main/java/com/example/exp_tracker/SettingsStore.kt`

- Persisted report repository and interface scale values.
- Added Developer Options persistence with default `true`.
- Added sync-safe configuration JSON serialization.

### `app/src/main/java/com/example/exp_tracker/GitHubApi.kt`

- Added hidden `.exvia-config.json` exclusion.
- Reserved dot-prefixed expense filenames.
- Added generic text-file upsert for synchronized configuration.
- Added GitHub Issue creation support.

### `app/src/main/java/com/example/exp_tracker/AppFonts.kt`

- Added non-compounding text scaling for complete view trees.
- Preserved text scale when only font weight changes later.

### `app/build.gradle.kts`

- Updated `versionName` to `1.11`.
- Updated `versionCode` to `12`.

### `README.md`

- Updated build, Developer Options, filtering, config sync, scaling, and Report instructions.

### `MODIFIED_FILES.md`

- Replaced the v1.10 list with this v1.11 file inventory.

## Created

### `V1.11_CHANGE_SPEC.md`

- Exact behavior and implementation notes for the v1.11 features.

## Runtime-created file

Exvia creates or updates this file in the configured GitHub repository when Settings are saved:

```text
Financial/.exvia-config.json
```

It is not a source-tree file and is hidden from the Files section. It never contains the GitHub PAT.

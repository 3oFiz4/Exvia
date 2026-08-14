# Exvia v1.13.6 icon/UI patch — modified and created files

## Modified

- `app/src/main/java/xyz/x3ofiz4/exvia/domain/model/settings/RepoSettings.kt`
  - Persists the chosen icon/text display mode.
- `app/src/main/java/xyz/x3ofiz4/exvia/data/local/SettingsStore.kt`
  - Loads/saves `icon_mode` and synchronizes `display.iconMode`.
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/main/MainActivity.kt`
  - Adds icon/text configuration, icon-aware buttons, action controls, accordion headers, and configuration dropdowns.
- `app/build.gradle.kts`
  - Uses an optional launcher logo only when it is a non-empty, decodable image.
- `README.md`
  - Documents the icon asset system and display modes.

## Created

- `app/src/main/java/xyz/x3ofiz4/exvia/domain/model/settings/UiIconMode.kt`
- `app/src/main/java/xyz/x3ofiz4/exvia/presentation/common/UiIconSupport.kt`
- `app/src/main/assets/icons/README.md`
- `app/src/main/assets/icons/*.png`
  - Valid transparent, replaceable placeholders for the known UI controls.
- `V1.13.6_ICON_UI_PATCH.md`
- `MODIFIED_FILES_ICON_UI.md`

## Unchanged

- `versionName = "1.13.6"`
- `versionCode = 23`
- Existing `logo.png` locations remain excluded from the replacement ZIP.

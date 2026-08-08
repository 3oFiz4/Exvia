# Exvia MVVM architecture

## 1. Dependency rule

Dependencies point inward:

```text
presentation → domain ← data
       ↓          ↑
      core ───────┘
```

- `domain` contains models, repository contracts, and deterministic services.
- `data` implements domain contracts using Android storage and GitHub HTTP operations.
- `presentation` renders immutable state and translates interaction into ViewModel intents.
- `core` contains narrowly scoped application primitives shared across layers.
- `app` is the composition root and is the only place where concrete implementations are assembled.

The domain layer does not import `android.*`, WebView classes, or `org.json`.

## 2. Composition root

`ExviaApplication` owns a lazily initialized `ExviaContainer` for the process. `ExviaContainer` creates:

- `TokenStore`
- `SettingsStore`
- `ExviaFileCache`
- `SelectedFileStore`
- `GitHubExpenseRepository`
- `ConfigurationRepositoryImpl`

The Activity receives repository interfaces from the container and passes them into ViewModel constructors.

## 3. Main feature flow

### Loading

1. `MainActivity` sends `loadInitial(settings)` to `MainViewModel`.
2. `MainViewModel` invokes `ExpenseRepository.loadInitial` on its background executor.
3. `GitHubExpenseRepository` uses cache first and falls back to GitHub when required.
4. The ViewModel publishes a new `MainUiState`.
5. The Activity observes it on the UI thread and renders the form, table, statistics, and files.

### Filtering

1. The View sends the enabled state and query to `MainViewModel.setFilter`.
2. `SqlLikeFilter` transforms only the effective rows.
3. `sourceData` remains the complete selected JSON table.
4. `visibleData` becomes the filtered table used by Table and Stat.
5. Pagination remains presentation-only and operates after filtering.

### Mutations

Amend, edit, delete-row, create-file, and delete-file actions follow the same route:

```text
View intent
→ MainViewModel
→ ExpenseRepository
→ GitHubApi + cache update
→ WorkspaceSnapshot
→ MainUiState
→ View render
```

The View never edits cached files or calls GitHub directly.

## 4. Settings flow

`SettingsViewModel` coordinates:

- local settings and token persistence;
- developer mode;
- filtering snippet persistence;
- synchronized `.exvia-config.json` writes;
- first-run repository creation;
- issue-report submission.

`ConfigurationRepository` hides whether an operation uses `SharedPreferences`, Android Keystore, or GitHub.

## 5. State and effects

`ObservableState<T>` stores the latest immutable state and immediately publishes it to new observers.

`EventStream<T>` carries one-shot effects that should not be represented as durable screen state, such as:

- errors;
- toast messages;
- reload requests;
- repository-created notifications;
- report-created URLs.

Activity subscriptions are closed in `onDestroy`.

## 6. Threading

Network, cache mutation, and configuration synchronization run on single-thread executors owned by their ViewModels. ViewModel observers may be notified from the worker thread; `MainActivity` marshals rendering back through `runOnUiThread`.

This preserves mutation ordering and avoids concurrent GitHub writes while keeping the UI responsive.

## 7. Testing seams

Both ViewModels depend on domain interfaces rather than concrete GitHub classes. Tests can provide fake implementations of:

```text
ExpenseRepository
ConfigurationRepository
```

This allows state transitions, filtering behavior, errors, and effects to be tested without Android UI, GitHub, a PAT, or network access.

## 8. Why MainActivity still contains UI code

Exvia builds its interface programmatically rather than through XML layouts or Compose. `MainActivity` therefore remains the View implementation and contains widget construction and rendering functions. The refactor intentionally leaves that visual implementation intact to avoid changing spacing, gestures, plots, dialogs, pagination, and settings behavior.

The MVVM boundary is behavioral rather than cosmetic: UI creation stays in the View, while repository access, state ownership, filtering, synchronization, and mutations live outside it.

# Exvia UI icon assets

Exvia looks for icons in this directory. The `.png` files shipped in this patch are intentionally empty placeholders so you can drop in your preferred rounded-outline icons without changing Kotlin code.

Missing, empty, corrupt, or undecodable icon files are treated as `null`; the surrounding control still works and the application does not fail.

Recommended replacement format: **24×24 or 32×32 transparent PNG**, monochrome rounded-outline glyph. Exvia recolors every decoded icon with the currently active `$primary` UI color at runtime, so the source icon only needs useful alpha/transparency.

Display mode: **Settings → Interface → Icon / text display**. Available values are `Icon only`, `Text only`, and `Icon and text`. Dropdown popup rows keep their text even in Icon-only mode so their choices remain distinguishable.

Known asset names:

- `Accumulation.png`
- `AddENVVariable.png`
- `AddField.png`
- `AddImaginaryField.png`
- `AddMetricColorRule.png`
- `AddNotificationRule.png`
- `AddSchemaRule.png`
- `Amend.png`
- `AmendStageCommitPush.png`
- `Apply.png`
- `AutomationENV.png`
- `Behavior.png`
- `BuiltInColorMappingExamples.png`
- `BuiltInENVExamples.png`
- `BuiltInExamples.png`
- `BuiltInMethods.png`
- `BuiltInMetricColorExamples.png`
- `BuiltInMetricExamples.png`
- `BuiltInNotificationExamples.png`
- `BuiltInPlotExamples.png`
- `BuiltInSchemaExamples.png`
- `BuiltInSnippets.png`
- `Cancel.png`
- `ChevronDown.png`
- `ChevronUp.png`
- `Clear.png`
- `ClearFormula.png`
- `ClearStoredPAT.png`
- `Close.png`
- `CloseSettings.png`
- `Color.png`
- `ColorMapping.png`
- `Core.png`
- `Create.png`
- `CustomAccordions.png`
- `CustomStat.png`
- `Delete.png`
- `DeleteSelected.png`
- `DiscardPull.png`
- `Dropdown.png`
- `Edit.png`
- `Expense.png`
- `ExviaSettings.png`
- `Fields.png`
- `Files.png`
- `Filtering.png`
- `FilteringMethod.png`
- `Flagging.png`
- `Formula.png`
- `Git.png`
- `Github.png`
- `HideInputControls.png`
- `History.png`
- `ImaginaryFields.png`
- `Income.png`
- `Info.png`
- `Interface.png`
- `KeySchemaScripts.png`
- `Liquidity.png`
- `Metric.png`
- `MetricColorMapping.png`
- `NewAccordion.png`
- `NewFlaggingMethod.png`
- `NewMapping.png`
- `NewSQLiteScript.png`
- `NewSnippet.png`
- `Next.png`
- `No.png`
- `NormalDistribution.png`
- `Notifications.png`
- `PersonalFinance.png`
- `Plot.png`
- `PlotTheme.png`
- `Plotting.png`
- `Previous.png`
- `Redo.png`
- `RemoveSelected.png`
- `Report.png`
- `RestorePRICECATEGORYDefaults.png`
- `Resync.png`
- `Revert.png`
- `SQLiteFileScripts.png`
- `Save.png`
- `SaveCurrentAsTheme.png`
- `SaveSettingsAndReload.png`
- `SavedMethods.png`
- `SchemaDisplay.png`
- `Settings.png`
- `ShowInputControls.png`
- `ShowcaseExamples.png`
- `ShowcaseMethods.png`
- `SqliteFileScripts.png`
- `Stat.png`
- `Submit.png`
- `Table.png`
- `Theme.png`
- `UiDisplayMode.png`
- `Undo.png`
- `Use.png`
- `VariablesENV.png`
- `Yes.png`

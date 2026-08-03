Back to main [README](../README.md)

# The shared Figma Excel file

`uploadFromFigma`, `compareWithFigma` (web/Android/iOS), and `cleanFigmaExcel` (see
[UploadFromFigma.md](UploadFromFigma.md)) all read from and write back into **one
file, in place** — there's no copying between stage-specific files. One row = one
Figma design (a full page or a single component), paired 1-to-1 with where to find
it in the real app: a **URL** for web, or a **screen name** for mobile (a mobile app
has no URL to navigate to directly). The file can have any number of rows; every
program iterates every row, skipping any marked `Skip`.

Default path: `<FIGMACOMPARE_CONFIG_DIR>/figma_visual_tests.xlsx` (see
[Configuration.md](Configuration.md) for how to override the path or the config dir).

## Column reference

Columns are grouped: manual input first, then write-back results grouped by which
stage produces them (`ExcelHelper.ALL_COLUMNS` writes them in exactly this order).

### Manual input (never overwritten by any program)

| Column | Required? | Description |
|---|---|---|
| `Figma URL` | **Yes** | A Figma share link to a specific frame/component, e.g. right-click a frame in Figma → *Copy link to selection*. Must contain a `node-id` — a link to a whole file/page isn't supported. |
| `Platform` | **Yes** | `Web`, `Android`, or `iOS`. |
| `App URL / Screen Name` | **Yes** | For `Web`: the UAT/production URL, opened directly with Selenium. For `Android`/`iOS`: a screen name/identifier, not a URL — reaching it needs a registered `ScenarioFlow` (see [CompareWithFigma.md](CompareWithFigma.md)). |
| `Scenario Name` | **Required for every Android/iOS row; optional for Web** | Names the Applitools test this row belongs to. Shared by **consecutive** rows to upload/compare them as the ordered steps of one multi-step test instead of one test per row (matches how the Applitools Figma plugin itself exports a multi-frame scenario). Blank means the row is a standalone, one-step test. For Android/iOS, must also match a scenario registered in `AndroidScenarioRegistry`/`IosScenarioRegistry` — see [CompareWithFigma.md](CompareWithFigma.md) for why mobile requires this even for a single screen. |
| `Test Name` | No | Overrides the auto-derived test/step name. If left blank, `uploadFromFigma` derives it from the Figma node's name (sanitized to letters/digits/`-`/`_`). For a scenario, this is the step's name within the shared test, not the whole test's name. Once a run writes a derived value here, it's not re-derived on the next run - it becomes the value from then on. |
| `Baseline Env Name` | No | Overrides the Applitools baseline environment name. If left blank, it's derived as `{testName}-baseline` (standalone) or `{scenarioName}-baseline` (scenario) - and, like `Test Name`, that derived value then persists across runs (see the "sticky" note below). For a scenario, only needs to be set on one row - it's shared across the whole group, and re-checked for consistency across the group before anything runs. |
| `Viewport` | No | Overrides the viewport size, format `WIDTHxHEIGHT` (e.g. `1280x1024`). If left blank, `uploadFromFigma` derives it from the downloaded image's pixel dimensions, and that value then persists the same way. |
| `Scale` | No | Figma export scale, e.g. `1`, `2`, `3`. Defaults to `1` if blank. |
| `Format` | No | Figma export format: `png`, `jpg`, `svg`, `pdf`. Defaults to `png` if blank. |
| `Skip` | No | Set to `true`/`t`/`yes`/`y`/`skip` (case-insensitive) to exclude this row from a run without deleting it. To run only specific rows, mark everything else as `Skip`. |
| `Locator` | No | Web only. A CSS selector (or an XPath, if it starts with `/`) scoping the comparison to just that element instead of the full page. Filled in after reviewing the uploaded baseline (Step 3 in the full workflow) - blank means full-page. |

Only `Figma URL`, `Platform`, and `App URL / Screen Name` are truly required to get
started - everything else can be left blank and the tools fill in sensible defaults.

**"Sticky" fields**: `Test Name`, `Baseline Env Name`, and `Viewport` are auto-derived
*only when blank* - once a run writes a value, later runs reuse it rather than
re-deriving. This means renaming a `Scenario Name` does **not** automatically update
an already-populated `Baseline Env Name` for those rows - if you want a fresh
baseline under the new name, clear that cell manually (`cleanFigmaExcel` deliberately
does not touch these three columns - see [UploadFromFigma.md](UploadFromFigma.md)).

### Write-back: `uploadFromFigma` stage

| Column | Written when |
|---|---|
| `App Name` | Every `uploadFromFigma` run, from `APP_NAME` config (or a row's own pre-existing value, if non-blank - same "override once set" behavior as the manual-input columns above). |
| `Baseline Batch URL` | On success - links directly to the uploaded baseline in the Applitools dashboard. |
| `Status` | `Success` or `Failed`. |
| `Error Message` | Only when `Status` is `Failed` - check console output for the full stack trace. |

### Write-back: `compareWithFigma` stage

| Column | Written when |
|---|---|
| `Comparison Batch URL` | Every comparison run - links to that run's result in the Applitools dashboard. |
| `Validation Status` | `Passed`, `Unresolved`, or `Failed` (the real `TestResultsStatus` from the Applitools SDK - see the `saveNewTests` note in [UploadFromFigma.md](UploadFromFigma.md) for why a brand-new baseline shows `Unresolved` here rather than `Passed`). |

For a scenario (several rows sharing a `Scenario Name`), every write-back column is
written identically onto **every row in the group** - they describe the one shared
test, not an individual step.

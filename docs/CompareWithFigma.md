Back to main [README](../README.md)

# `compareWithFigma`

Compares a live web/Android/iOS implementation against the baselines
[uploadFromFigma](UploadFromFigma.md) uploaded, and writes the result back into the
[shared Excel file](ExcelSchema.md), in place. One runner class per platform -
`WebCompareRunner`, `AndroidCompareRunner`, `IosCompareRunner` - all plain Java, no
TestNG (or any other test framework) dependency; a consuming repo wraps each in a
thin TestNG/JUnit shim that just supplies a `@DataProvider`/`beforeSuite`/`afterSuite`
around the runner's own methods.

## Pre-flight validation ("dry run")

Before doing any real work (Figma/Applitools API calls, browser/app launches), every
entry point (`uploadFromFigma`, `compareWithFigma` for any platform) validates the
*entire* file via `FigmaValidation` and reports every problem found at once - nothing
runs partially. Checks include:

- `Figma URL` (with a `node-id`), `Platform`, and `App URL / Screen Name` present on
  every non-`Skip` row; `Viewport` (if set) matches `WIDTHxHEIGHT`.
- `Scenario Name` is required for every Android/iOS row - a hard error if blank.
- Scenario rows are contiguous - a `Scenario Name` reused by non-adjacent rows is a
  hard error naming the offending row numbers.
- Scenario metadata is consistent - `Baseline Env Name`/`App Name`/`Viewport` must
  agree across every row in a scenario; a hard error names the conflicting values
  and rows.
- Step names are unique within a scenario.
- For Android/iOS specifically: every distinct `Scenario Name` used by a row has a
  matching entry registered in `AndroidScenarioRegistry`/`IosScenarioRegistry` (from
  *any* provider class).

## Web - `WebCompareRunner`

Fully generic - **no code needed for any web row**, ever. Data-driven from the Excel
file: one invocation per group of non-`Skip` `Platform=Web` rows (a standalone row is
a group of one). For each row/step in a group, it opens `App URL / Screen Name`
directly with Selenium (in one continuous browser session for the whole group) and
submits an Applitools Eyes comparison against the group's `Baseline Env Name`
baseline - full page if `Locator` is blank, otherwise just that CSS/XPath-selected
region.

One `VisualGridRunner` and one `BatchInfo` are shared across the whole run - creating
a new one per group would repeatedly start/stop the Ultrafast Grid's background
process and hang. Because results from a shared runner are only available once every
submitted check has finished, groups just submit their checks via `closeAsync()`;
the actual pass/fail, `Comparison Batch URL`, and `Validation Status` are collected
once at the end of the run, matched back to each group's rows by test/scenario name,
and written to the Excel file - the run fails there if anything mismatched.

**Driver setup** (`Driver.java`): creates a local `WebDriver` for Chrome, Firefox,
Edge, or Safari.
- `BROWSER` env var selects the browser, defaults to Chrome. Selenium Manager
  resolves the matching driver automatically - just have that browser installed.
- `HEADLESS=true` runs headless (Chrome/Firefox/Edge only, not Safari) - defaults to
  headed, which is what you want locally; CI typically sets this, since there's no
  display on the runner.

## Android / iOS - `AndroidCompareRunner` / `IosCompareRunner`

Every mobile test is bespoke, dispatched by `Scenario Name` - unlike web, there's no
generic way to navigate a native app to an arbitrary screen; reaching even one screen
can need login/menu navigation specific to that app. So `Scenario Name` is
**required on every Android/iOS row**, even a single-screen one, and every scenario
needs a small amount of registration code:

- **The runner** (`AndroidCompareRunner`/`IosCompareRunner`) is the same for every
  app on that platform - one invocation per group of rows sharing a `Scenario Name`.
  It looks up that name in `AndroidScenarioRegistry`/`IosScenarioRegistry`, launches
  the registered app (an APK for Android, an unzipped `.app` bundle - not a `.zip` -
  for iOS), hands the group to the registered `ScenarioFlow` in one continuous app
  session (no relaunch between steps), then does the Applitools comparison and Excel
  write-back, same as the web path.
- **Scenario providers** - app-specific classes that are *not* tests themselves, just
  a static initializer registering `(scenarioName, appPath, appName, ScenarioFlow)`
  tuples into the registry:
  ```java
  AndroidScenarioRegistry.register("android-home-screen", APK_NAME, APP_NAME, (driver, eyes, rows) -> {
      // whatever this app's real login/navigation needs, then:
      eyes.checkWindow(resolveStepName(rows.get(0)));
      // for a multi-screen scenario, navigate further and call eyes.checkWindow(...) again per step
  });
  ```
  A scenario is looked up purely by name - the runner doesn't know or care which
  provider class registered it, so a scenario referenced in the Excel can be
  implemented in *any* class file. A `ScenarioFlow` must be fully self-contained: it
  owns the whole sequence for its scenario's rows, however many `eyes.checkWindow()`
  calls it makes, in whatever order.
- A provider class's static initializer only runs once that class is
  loaded/referenced by something - an unreferenced provider class silently registers
  nothing. A consuming project typically keeps an explicit list of provider classes
  to load (see that project's own docs for its exact mechanism).

Both platforms reuse the same shared utilities: `AppiumServerSupport`,
`AndroidDriverFactory`/`IosDriverFactory`, `BatchSupport`,
`ComparisonResultRecorder`, `FigmaExcelFile`, `FigmaValidation`.

**Known extensibility gap**: `AndroidDriverFactory`/`IosDriverFactory` currently only
take `(apkPath/appPath, fullReset)` - a fixed, minimal Appium capability set. An app
needing extra capabilities, or different Eyes config (batch/match-level/etc. are
currently global per platform, not per-app), would need a small extension to those
factories/runners first.

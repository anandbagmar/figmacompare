Back to main [README](../README.md)

# Configuration

All settings below are `AppConfig.get(key, default)` lookups: an environment variable
of the same name, else the same key in `config.properties`, else the listed default
(env var always wins if both are set - useful for CI, where you'd rather not commit a
`config.properties`). `AppConfig.CONFIG_DIR` (see below) is the one exception - it has
to be resolved before `config.properties` can even be located, so it's env
var/system-property only.

Copy `templates/config.properties.example` (from a consumer's own
`FIGMACOMPARE_CONFIG_DIR`) to `config.properties` to get started - see a consumer's
own setup docs for the exact path convention it uses.

## Required

| Setting | Notes |
|---|---|
| `FIGMA_TOKEN` | Figma personal access token - **Account Settings → Security → Personal access tokens** in Figma. Must have access to the file(s) being exported. Hard-fails with a clear message if unset when `uploadFromFigma` needs it. |
| `APPLITOOLS_API_KEY` | From the Applitools dashboard - **Account Settings → API Key**. Hard-fails with a clear message if unset (`AppConfig.requireApplitoolsApiKey()`). |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Android `compareWithFigma` path only - standard Android SDK env vars, not `AppConfig`-mediated. |

## Overridable paths and Applitools settings

Everything below has a sensible default, so an existing consumer needs to change
nothing to get started.

| Setting | Default | Notes |
|---|---|---|
| `FIGMACOMPARE_CONFIG_DIR` | `figma-visual-testing` | Where `config.properties`/`templates/` live. **Env var or `-Dfigmacompare.configDir=...` system property only** - can't live in `config.properties` itself, since it determines where that file is. |
| `FIGMA_EXCEL_FILE` | `<configDir>/figma_visual_tests.xlsx` | The [shared Excel file](ExcelSchema.md) - see `FigmaExcelFile`. A consumer's Gradle task typically also supports a `-PfigmaExcel=<path>` override, which wins over this if both are set. |
| `FIGMA_CACHE_DIR` | `downloaded_images/figma-cache` | Where `uploadFromFigma` caches downloaded Figma images, named `{fileKey}_{nodeId}_{scale}x.{format}`. |
| `APP_NAME` | `Applitools-Images` | The Applitools app name `uploadFromFigma` stamps onto every row/test it creates. |
| `APPLITOOLS_BATCH_NAME` | `Upload from Figma` (upload) / a `<user.name>-<appName>` batch (compare) | Groups everything uploaded/compared in one run under a single named batch in the Applitools dashboard. |
| `APPIUM_JS_PATH` | `./node_modules/appium/build/lib/main.js` | Appium's Node entrypoint - see `AppiumServerSupport`. |
| `APPLITOOLS_SERVER_URL` | `https://eyes.applitools.com` (compare) | For an on-prem/private Applitools instance. **`uploadFromFigma` has no built-in default for this** - it hard-fails if unset, unlike the compare path. |
| `APPLITOOLS_MATCH_LEVEL` | `STRICT` | Any `com.applitools.eyes.MatchLevel` value, case-insensitive. An invalid value hard-fails immediately naming the valid options - see `EyesConfigSupport`. Web/compare path only. |
| `APPLITOOLS_BRANCH_NAME` | `main` | Android/iOS `compareWithFigma` only. |
| `APPLITOOLS_ENVIRONMENT_NAME` | `prod` | Android/iOS `compareWithFigma` only. Dashboard metadata (`Configuration.setEnvironmentName`) - not the same thing as a row's `Baseline Env Name` in the Excel file (`setBaselineEnvName`), despite the similar name. |
| `CI_RUN_NUMBER` | unset | If set, appended to Applitools batch names as `" - #<value>"`, so batches from different CI runs are distinguishable. This library is CI-provider agnostic and never reads a provider-specific variable itself (e.g. GitHub Actions' `GITHUB_RUN_NUMBER`) - a consumer's own CI is responsible for mapping its provider's run number into this generic key. |
| `HEADLESS` | unset (headed) | Web `compareWithFigma` only - see [CompareWithFigma.md](CompareWithFigma.md). |
| `BROWSER` | `chrome` | Web `compareWithFigma` only - `chrome`/`firefox`/`edge`/`safari`. |

Still not configurable here (owned by the consumer instead, which is the right place
for them): app binary paths (APKs, `.app` bundles) live in each consumer's own
scenario provider classes, not in this library, so they're already per-consumer by
construction.

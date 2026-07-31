# figmacompare

A reusable Figma-to-Applitools visual-testing framework: uploads Figma designs as
Applitools Eyes baselines, then compares a live web/Android/iOS implementation against
those baselines and writes results back to a shared Excel file.

Extracted from the `bajajfinserv-poc` project's `core` module so any client repo can
depend on it as a published artifact instead of vendoring the source. See
`figmacompare-sample` for a worked example (scenario providers, Excel file, thin TestNG
shims around this library's plain-Java runners).

## What's in here

- `io.eot.figmacompare.excel` — the shared Excel file model/validation (`FigmaRow`,
  `FigmaExcelFile`, `FigmaValidation`, `ExcelHelper`)
- `io.eot.figmacompare.figma` — the Figma REST client (`FigmaClient`, URL parsing).
  Every request is retried with exponential backoff on `429`/`5xx`, and paced at least
  1 second apart from the previous request regardless of outcome, to reduce how often
  a run trips Figma's own per-token rate limit in the first place. Neither eliminates
  it entirely - a token already deep into an extended rate-limit window (e.g. from
  several back-to-back runs) will still see `429`s; that's Figma's own throttling, not
  a bug here.
- `io.eot.figmacompare.eyes` — Applitools Eyes configuration and batch/result helpers
  shared across web/Android/iOS (`EyesConfigSupport`, `MobileEyesSupport`,
  `BatchSupport`, `ComparisonResultRecorder`)
- `io.eot.figmacompare.appium` — Appium server lifecycle and mobile driver factories
  (`AppiumServerSupport`, `AndroidDriverFactory`, `IosDriverFactory`,
  `AndroidScenarioRegistry`, `IosScenarioRegistry`, `MobileRunSupport`)
- `io.eot.figmacompare.web.selenium` — Selenium `WebDriver` factory
- `AndroidCompareRunner` / `IosCompareRunner` / `WebCompareRunner` — the plain-Java
  orchestration for the `compareWithFigma` pipeline, one per platform. No TestNG (or any
  other test framework) dependency; a consuming repo wraps each in a thin TestNG (or
  JUnit, etc.) shim.
- `UploadFromFigma` / `Baseline` — the `uploadFromFigma` baseline-upload pipeline.
  `UploadFromFigma.main` takes `[figmaExcelPath] [forceRefresh] [platform]` (all
  optional, also settable via `-DfigmaExcel`/`-DforceRefresh`/`-Dplatform`) - `platform`
  (`Web`/`Android`/`iOS`) scopes the run to just that platform's rows instead of the
  whole file; an invalid value hard-fails immediately.

## Local dev loop

```bash
./gradlew publishToMavenLocal
```

Publishes as `io.eot:figmacompare:0.0.1-local` (the default when `-PpublishVersion` isn't
given). Consumers that list `mavenLocal()` first in their `repositories {}` (see
`figmacompare-sample`'s `build.gradle`) pick this up automatically - useful for iterating
on a change here and testing it in a consumer before cutting a real release.

## Publishing a release

Releases are on-demand, not automatic on every push - publishing to GitHub Packages only
happens when you create a GitHub Release, via `.github/workflows/publish.yml`. The
published version comes from the release's tag, so there's nothing to bump in
`build.gradle` beforehand.

```bash
gh release create v1.2.0 --repo anandbagmar/figmacompare \
  --title "v1.2.0" --notes "<what changed>"
```

This publishes `io.eot:figmacompare:1.2.0` (the tag's `v` prefix is stripped) to
[GitHub Packages](https://github.com/anandbagmar/figmacompare/packages), using the
workflow's own `GITHUB_TOKEN` - no secret to configure here. Watch it with:

```bash
gh run list --repo anandbagmar/figmacompare --workflow "Publish to GitHub Packages" --limit 1
```

**Consumers always need an authenticated token to resolve from GitHub Packages' Maven
registry**, even once this repo is public - that's a GitHub Packages limitation for Maven/
Gradle specifically (unlike its npm registry). A consumer's token needs `repo` (if this
repo is still private) and `read:packages`; see `figmacompare-sample`'s
`README_uploadFromFigma.md` for a worked example of setting that up as `FIGMACOMPARE_PAT`.

## Configuration a consumer needs to provide

- `config.properties` (or environment variables) with `FIGMA_TOKEN` and
  `APPLITOOLS_API_KEY` — see `AppConfig`.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` set, for the Android path.
- Scenario provider classes registering into `AndroidScenarioRegistry`/
  `IosScenarioRegistry` for the mobile paths (web needs no registration - any
  `App URL / Screen Name` can be navigated to directly).

### Overridable paths and Applitools settings

Everything below has a default matching the original single-repo layout, so an existing
consumer needs to change nothing. All are `AppConfig.get(key, default)` lookups (env var,
then `config.properties`) unless noted otherwise.

| Setting | Default | Notes |
|---|---|---|
| `FIGMACOMPARE_CONFIG_DIR` | `figma-visual-testing` | Where `config.properties`/`templates/` live. **Env var or `-Dfigmacompare.configDir=...` system property only** - can't live in `config.properties` itself, since it determines where that file is. |
| `FIGMA_EXCEL_FILE` | `<configDir>/figma_visual_tests.xlsx` | The shared Excel file - see `FigmaExcelFile`. |
| `APPIUM_JS_PATH` | `./node_modules/appium/build/lib/main.js` | Appium's Node entrypoint - see `AppiumServerSupport`. |
| `APPLITOOLS_SERVER_URL` | `https://eyes.applitools.com` | For an on-prem/private Applitools instance. |
| `APPLITOOLS_MATCH_LEVEL` | `STRICT` | Any `com.applitools.eyes.MatchLevel` value, case-insensitive. An invalid value hard-fails immediately naming the valid options - see `EyesConfigSupport`. |
| `APPLITOOLS_BRANCH_NAME` | `main` | Android/iOS `compareWithFigma` only. |
| `APPLITOOLS_ENVIRONMENT_NAME` | `prod` | Android/iOS `compareWithFigma` only. |

Still not configurable here (owned by the consumer instead, which is the right place for
them): `sampleApps/...` app paths live in each consumer's own scenario provider classes,
not in this library, so they're already per-consumer by construction.

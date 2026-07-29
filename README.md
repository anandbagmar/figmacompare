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
- `io.eot.figmacompare.figma` — the Figma REST client (`FigmaClient`, URL parsing)
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
- `UploadFromFigma` / `Baseline` — the `uploadFromFigma` baseline-upload pipeline

## Build and publish locally

```bash
./gradlew publishToMavenLocal
```

Consumers add `mavenLocal()` to their repositories and depend on
`io.eot:figmacompare:0.0.1`.

## Configuration a consumer needs to provide

- `config.properties` (or environment variables) with `FIGMA_TOKEN` and
  `APPLITOOLS_API_KEY` — see `AppConfig`.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` set, for the Android path.
- Scenario provider classes registering into `AndroidScenarioRegistry`/
  `IosScenarioRegistry` for the mobile paths (web needs no registration - any
  `App URL / Screen Name` can be navigated to directly).

Known gap (not yet solved here): paths like `sampleApps/`, `figma-visual-testing/`,
and `downloaded_images/` are still resolved relative to the *consumer's* working
directory (`AppConfig`, `FigmaExcelFile`), not configurable per-consumer. Fine for a
single consumer repo; would need addressing before this could serve multiple
independent consumer repos with different layouts.

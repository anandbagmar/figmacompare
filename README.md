# figmacompare

A reusable Figma-to-Applitools visual-testing framework: uploads Figma designs as
Applitools Eyes baselines, then compares a live web/Android/iOS implementation against
those baselines and writes results back to a shared Excel file.

Extracted from the `bajajfinserv-poc` project's `core` module so any client repo can
depend on it as a published artifact instead of vendoring the source. See
`figmacompare-sample` for a worked example (scenario providers, Excel file, thin TestNG
shims around this library's plain-Java runners).

## Docs

- [The shared Excel file](docs/ExcelSchema.md) - the column reference, "sticky" fields
- [uploadFromFigma / cleanFigmaExcel](docs/UploadFromFigma.md) - usage, `saveNewTests`
  behavior, rate-limiting, the manual Figma-cache workaround
- [compareWithFigma](docs/CompareWithFigma.md) - web/Android/iOS behavior, pre-flight
  validation, the `ScenarioFlow`/registry pattern for mobile
- [Configuration](docs/Configuration.md) - every setting this library reads, with
  defaults

## What's in here

- `io.eot.figmacompare.excel` — the shared Excel file model/validation (`FigmaRow`,
  `FigmaExcelFile`, `FigmaValidation`, `ExcelHelper`)
- `io.eot.figmacompare.figma` — the Figma REST client (`FigmaClient`, URL parsing) -
  see [uploadFromFigma](docs/UploadFromFigma.md) for its retry/rate-limit handling.
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
- `UploadFromFigma` / `Baseline` / `CleanExcel` — the `uploadFromFigma`
  baseline-upload pipeline and its Excel-cleanup companion - see
  [uploadFromFigma / cleanFigmaExcel](docs/UploadFromFigma.md) for usage.

## Local dev loop

```bash
./gradlew publishToMavenLocal
```

Publishes as `io.eot:figmacompare:0.0.1-local` (the default when `-PpublishVersion` isn't
given). Consumers that list `mavenLocal()` first in their `repositories {}` (see
`figmacompare-sample`'s `build.gradle`) pick this up automatically - useful for iterating
on a change here and testing it in a consumer before cutting a real release.

## Publishing a release

Releases are on-demand, not automatic on every push - triggered by creating a GitHub
Release, via `scripts/create-release.sh` (prompts for version/title/notes) or directly:

```bash
gh release create v1.2.0 --repo anandbagmar/figmacompare \
  --title "v1.2.0" --notes "<what changed>"
```

This repo is public, and consumers resolve it via **[JitPack](https://jitpack.io)**,
which builds directly from a tag on demand - **there is nothing to push anywhere**.
`.github/workflows/publish.yml` runs on the release event, but only to: gate on tests
passing, attach a built jar directly to the GitHub Release (a token-free download,
independent of JitPack), and warm JitPack's build for that tag so the first real
consumer isn't stuck waiting on a slow cold build (JitPack usually finishes in well
under the workflow's own timeout, but there's no hard guarantee). Watch it with:

```bash
gh run list --repo anandbagmar/figmacompare --workflow "Publish figmacompare release" --limit 1
```

**No token is needed to consume this.** A consumer adds JitPack as a repository and
depends on it like any other Maven coordinate:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation "com.github.anandbagmar:figmacompare:v1.2.0"   // exact tag, including the "v"
}
```

Note the groupId: JitPack always uses `com.github.<owner>` for a single-module project
(not configurable) - this intentionally differs from the `io.eot.figmacompare` Java
package namespace used throughout the actual code. That mismatch is normal for a
JitPack-distributed library (the Maven coordinate is a hosting/packaging artifact,
unrelated to the code's own namespace) - it does not indicate anything is wrong.

## Configuration a consumer needs to provide

- `config.properties` (or environment variables) with `FIGMA_TOKEN` and
  `APPLITOOLS_API_KEY` at minimum - see [Configuration](docs/Configuration.md) for
  every setting and its default.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` set, for the Android path.
- Scenario provider classes registering into `AndroidScenarioRegistry`/
  `IosScenarioRegistry` for the mobile paths (web needs no registration - any
  `App URL / Screen Name` can be navigated to directly - see
  [CompareWithFigma](docs/CompareWithFigma.md)).

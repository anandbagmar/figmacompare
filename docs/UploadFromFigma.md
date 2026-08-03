Back to main [README](../README.md)

# `uploadFromFigma` and `cleanFigmaExcel`

`UploadFromFigma.main` reads the [shared Excel file](ExcelSchema.md), downloads each
row's Figma design as an image, uploads it to Applitools Eyes as a baseline, and
writes results back into the same file, in place. Pure Java - no
Selenium/Appium/browser needed.

This is step 2 of the full workflow - see [CompareWithFigma.md](CompareWithFigma.md)
for the comparison step that follows it, and
[Configuration.md](Configuration.md) for `FIGMA_TOKEN`/`APPLITOOLS_API_KEY`/etc.

## Usage

```
UploadFromFigma [figmaExcelPath] [forceRefresh: true|false] [platform: Web|Android|iOS]
```

All positional args are optional, also settable as `-DfigmaExcel`/`-DforceRefresh`/`-Dplatform`
system properties (a consumer's Gradle task typically maps its own `-P` properties to
these - see the consuming project's own docs for its exact task/property names).

- **`figmaExcelPath`** - defaults per [Configuration.md](Configuration.md)'s
  `FIGMA_EXCEL_FILE` resolution order.
- **`forceRefresh`** - re-downloads every Figma image even if a cached copy already
  exists in `FIGMA_CACHE_DIR` (useful when the Figma design has changed). Defaults to
  `false`, reusing any cached image.
- **`platform`** - scopes the run to just `Web`/`Android`/`iOS` rows instead of the
  whole file (an invalid value hard-fails immediately, naming the valid options) -
  useful while iterating on one platform without re-touching baselines for the
  others. Defaults to unset, processing every platform's rows.

## What it does, per row/group

1. Pre-flight validates the *entire* file first (see
   [CompareWithFigma.md](CompareWithFigma.md)'s "Pre-flight validation" section) -
   nothing runs partially; every problem is reported at once.
2. Groups rows by `Scenario Name` (consecutive rows sharing a name become one group;
   a blank `Scenario Name` is a group of one).
3. For each group: downloads each row's Figma image (via `FigmaClient` - see the
   rate-limiting note below), then uploads the whole group as one Applitools test
   (one `eyes.open()`, one `check()` per step, one `close()`) - matching how the
   Applitools Figma plugin itself uploads a multi-frame scenario.
4. Writes results back: see [ExcelSchema.md](ExcelSchema.md#write-back-uploadfromfigma-stage).

One `EyesRunner` (and its background "universal core" process) and one `BatchInfo`
are shared across the whole run, so every upload groups into a single Applitools
batch instead of a new one per row.

## `saveNewTests` and dashboard status

This program always calls `saveNewTests(true)`, so a brand-new checkpoint (no prior
baseline under that exact `Baseline Env Name`) is automatically **saved as the active
baseline** - no manual dashboard approval needed. Despite that, the Applitools
dashboard's Test Results view still shows that first run as **New**/**Unresolved** -
that's expected, not a failure: there's nothing to compare a brand-new checkpoint
against, so it can't be labeled `Passed`, but it *is* saved. To confirm it actually
saved, run `compareWithFigma` against the same row afterward - it'll show
`Passed`/`Failed` (not `New`) once a real baseline exists to diff against.

Re-running with the same `Figma URL` and no `forceRefresh` reuses the cached image
(no Figma API hit), but **does** re-upload to Applitools and create a new baseline
test each run - repeated runs accumulate multiple "New" Test Results entries even
though only the latest run's image is what's actually active as the baseline.

## `cleanFigmaExcel` - resetting results

`CleanExcel.main` (`cleanFigmaExcel` in a typical consumer's Gradle wiring) resets
the write-back result columns - `App Name`, `Baseline Batch URL`, `Status`,
`Error Message`, `Comparison Batch URL`, `Validation Status` - back to blank across
every row, for a clean slate before a fresh run.

It deliberately does **not** touch `Baseline Env Name`, `Viewport`, or `Test Name` -
see [ExcelSchema.md](ExcelSchema.md)'s "sticky fields" note for why those need a
manual edit instead, if you want them to re-derive fresh.

This is a destructive, in-place overwrite with no backup - same as every other write
this library does to the Excel file.

## Notes

- **Figma's API rate-limits requests per token.** `FigmaClient` retries with
  exponential backoff on `429`/`5xx` (honoring a `Retry-After` header when Figma
  sends one), and paces every outgoing request at least 1 second apart from the
  previous one regardless of outcome, to reduce how often a run trips the limit in
  the first place. Neither eliminates it entirely - a token already deep into an
  extended rate-limit window (e.g. from several back-to-back runs) will still see
  `429`s; that's Figma's own throttling, not a bug here. Figma error responses are
  included in full in the thrown exception's message (e.g. `{"status":403,"err":"Invalid token"}`)
  to distinguish a real auth/permission problem from a mislabeled rate limit.
- **Manual workaround for a Figma image that keeps failing**: place the image
  directly in the cache instead of waiting on the Figma API - `getCachedImage` only
  hits the network if the cache file doesn't already exist.
  1. In Figma, select the frame → **Export** → PNG at the row's `Scale` (default `1`)
  2. Name it `{fileKey}_{nodeId with ":" replaced by "-"}_{scale}x.{format}` - e.g.
     for `node-id=170-61` in file `7kPt5byFnDm1hs2Bd1FlNL`, scale `1`, format `png`:
     `7kPt5byFnDm1hs2Bd1FlNL_170-61_1x.png`
  3. Move it into `FIGMA_CACHE_DIR` (default `downloaded_images/figma-cache/`)
  4. Run without `forceRefresh`, so the cache is honored

  Make sure the export matches what Figma's own API would have rendered (same
  frame, default export settings) - this image becomes the actual Applitools
  baseline, not just a placeholder.
- Since the Excel file is updated in place, close it in Excel before running - a
  file locked open by another program can't be overwritten.

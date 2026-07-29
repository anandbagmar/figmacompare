package io.eot.figmacompare;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.EyesRunner;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.images.ImageRunner;

import io.eot.figmacompare.config.AppConfig;
import io.eot.figmacompare.eyes.BatchSupport;
import io.eot.figmacompare.excel.ExcelHelper;
import io.eot.figmacompare.excel.FigmaExcelFile;
import io.eot.figmacompare.excel.FigmaRow;
import io.eot.figmacompare.excel.FigmaValidation;
import io.eot.figmacompare.figma.FigmaClient;

/**
 * Reads Figma URLs from the shared Figma Excel file, uploads each corresponding Figma
 * image to Applitools Eyes as a baseline, and writes the results back to the same file.
 *
 * Expected columns (header row, any order): Figma URL, Platform, App URL / Screen Name,
 * Scenario Name, Test Name, Baseline Env Name, Viewport, Scale, Format, Skip. Only
 * "Figma URL" is required per row; the rest are optional. "Baseline Env Name" is
 * auto-derived if left blank, or used as-is if provided. Rows with "Skip" set to
 * true/t/yes/y/skip (case-insensitive) are left untouched and not processed.
 *
 * Rows sharing the same non-blank "Scenario Name" (consecutively, in sheet order) are
 * uploaded as one multi-step Applitools test - one eyes.open(), one check() per row/step,
 * one close() - matching how the Applitools Figma plugin itself uploads a multi-frame
 * scenario. A standalone row (blank Scenario Name) is just a scenario of one step.
 *
 * Usage: UploadFromFigma [figmaExcelPath] [forceRefresh: true|false]
 * figmaExcelPath, if omitted, falls back to -DfigmaExcel, then FIGMA_EXCEL_FILE in
 * config.properties/env, then a built-in default (see FigmaExcelFile).
 */
public class UploadFromFigma {

    private static final String DEFAULT_SCALE = "1";
    private static final String DEFAULT_FORMAT = "png";

    public static void main(String[] args) {
        String pathOverride = args.length > 0 ? args[0] : System.getProperty("figmaExcel");
        String figmaExcelPath = FigmaExcelFile.resolvePath(pathOverride);
        boolean forceRefresh = args.length > 1 ? Boolean.parseBoolean(args[1]) : Boolean.getBoolean("forceRefresh");

        String figmaToken = AppConfig.get("FIGMA_TOKEN");
        String applitoolsApiKey = AppConfig.get("APPLITOOLS_API_KEY");
        String applitoolsServerUrl = AppConfig.get("APPLITOOLS_SERVER_URL");
        String appName = AppConfig.get("APP_NAME", "Applitools-Images");
        String cacheDir = AppConfig.get("FIGMA_CACHE_DIR", "downloaded_images/figma-cache");
        String batchName = AppConfig.get("APPLITOOLS_BATCH_NAME", "Upload from Figma");

        String configFilePath = AppConfig.CONFIG_DIR + "/" + AppConfig.CONFIG_FILE_NAME;
        if (null == figmaToken) {
            throw new IllegalStateException("FIGMA_TOKEN is not set. Open " + configFilePath
                    + " and fill in FIGMA_TOKEN (or set it as an environment variable).");
        }
        if (null == applitoolsApiKey) {
            throw new IllegalStateException("APPLITOOLS_API_KEY is not set. Open " + configFilePath
                    + " and fill in APPLITOOLS_API_KEY (or set it as an environment variable).");
        }
        if (null == applitoolsServerUrl) {
            throw new IllegalStateException("APPLITOOLS_SERVER_URL is not set. Open " + configFilePath
                    + " and fill in APPLITOOLS_SERVER_URL (or set it as an environment variable).");
        }

        List<FigmaRow> allRows = ExcelHelper.readRows(figmaExcelPath);
        FigmaValidation.throwIfAny(FigmaValidation.validate(allRows));

        FigmaClient figmaClient = new FigmaClient(figmaToken);
        List<FigmaRow> toProcess = FigmaExcelFile.excludeSkipped(allRows);
        List<List<FigmaRow>> groups = FigmaExcelFile.groupContiguous(toProcess);
        System.out.println("Loaded " + allRows.size() + " row(s) from " + figmaExcelPath + " ("
                + (allRows.size() - toProcess.size()) + " skipped, " + groups.size() + " test(s) to upload)");

        // One EyesRunner (and its background "universal core" process), and one BatchInfo,
        // shared across the whole run - so every upload groups into a single batch instead
        // of starting/stopping the runner and creating a new batch ID per row.
        EyesRunner runner = new ImageRunner();
        BatchInfo batch = new BatchInfo(batchName);
        try {
            for (List<FigmaRow> group : groups) {
                processGroup(runner, group, figmaClient, appName, applitoolsApiKey, applitoolsServerUrl, cacheDir,
                        forceRefresh, batch);
            }
        } finally {
            BatchSupport.closeBatch(batch);
            runner.close();
        }

        ExcelHelper.writeRows(figmaExcelPath, allRows);

        long succeeded = toProcess.stream().filter(r -> "Success".equals(r.status)).count();
        System.out.println();
        System.out.println(succeeded + " of " + toProcess.size() + " row(s) succeeded. Results written to "
                + figmaExcelPath);
    }

    private static void processGroup(EyesRunner runner, List<FigmaRow> group, FigmaClient figmaClient,
            String appName, String applitoolsApiKey, String applitoolsServerUrl, String cacheDir,
            boolean forceRefresh, BatchInfo batch) {
        FigmaRow firstRow = group.get(0);
        String scenarioName = FigmaExcelFile.scenarioNameOf(firstRow);
        boolean isScenario = null != scenarioName;
        System.out.println(isScenario
                ? "Processing scenario \"" + scenarioName + "\" (" + group.size() + " step(s))"
                : "Processing: " + firstRow.figmaUrl);
        try {
            List<Baseline.ScenarioStep> steps = new ArrayList<>();
            for (FigmaRow row : group) {
                row.appName = appName;
                String scale = isBlank(row.scale) ? DEFAULT_SCALE : row.scale;
                String format = isBlank(row.format) ? DEFAULT_FORMAT : row.format;
                if (isBlank(row.testName)) {
                    row.testName = sanitizeTestName(figmaClient.fetchNodeName(row.figmaUrl));
                }
                File imageFile = figmaClient.getCachedImage(row.figmaUrl, format, scale, cacheDir, forceRefresh);
                steps.add(new Baseline.ScenarioStep(row.testName, imageFile.getAbsolutePath()));
            }

            String scenarioTestName = isScenario ? scenarioName : firstRow.testName;
            String baselineEnvName = isBlank(firstRow.baselineEnvName)
                    ? scenarioTestName + "-baseline"
                    : firstRow.baselineEnvName;
            RectangleSize viewportSize = ExcelHelper.parseViewport(firstRow.viewport);

            BaselineUploadResult result = Baseline.uploadScenarioAndSetAsBaseline(runner, appName, scenarioTestName,
                    baselineEnvName, viewportSize, applitoolsApiKey, applitoolsServerUrl, batch, steps);

            String resolvedViewport = result.getViewportSize().getWidth() + "x"
                    + result.getViewportSize().getHeight();
            for (FigmaRow row : group) {
                row.baselineEnvName = baselineEnvName;
                row.viewport = resolvedViewport;
                if (null != result.getTestResults()) {
                    row.baselineBatchUrl = result.getTestResults().getUrl();
                    row.status = "Success";
                } else {
                    row.status = "Failed";
                    row.errorMessage = "See console/log output for details";
                }
            }
        } catch (Exception ex) {
            for (FigmaRow row : group) {
                row.status = "Failed";
                row.errorMessage = ex.getMessage();
            }
            System.out.println(ex);
            ex.printStackTrace();
        }
    }

    private static String sanitizeTestName(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9_-]+", "_");
    }

    private static boolean isBlank(String value) {
        return null == value || value.isBlank();
    }
}

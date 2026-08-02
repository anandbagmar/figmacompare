package io.eot.figmacompare.web.selenium;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import com.applitools.eyes.AccessibilityGuidelinesVersion;
import com.applitools.eyes.AccessibilityLevel;
import com.applitools.eyes.AccessibilitySettings;
import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.StdoutLogHandler;
import com.applitools.eyes.TestResultsSummary;
import com.applitools.eyes.selenium.BrowserType;
import com.applitools.eyes.selenium.Configuration;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.Target;
import com.applitools.eyes.visualgrid.services.RunnerOptions;
import com.applitools.eyes.visualgrid.services.VisualGridRunner;

import io.eot.figmacompare.Log;
import io.eot.figmacompare.config.AppConfig;
import io.eot.figmacompare.eyes.BatchSupport;
import io.eot.figmacompare.eyes.ComparisonResultRecorder;
import io.eot.figmacompare.eyes.EyesConfigSupport;
import io.eot.figmacompare.excel.ExcelHelper;
import io.eot.figmacompare.excel.FigmaExcelFile;
import io.eot.figmacompare.excel.FigmaRow;
import io.eot.figmacompare.excel.FigmaValidation;

/**
 * Plain-Java orchestration for the web path of compareWithFigma - mirrors
 * AndroidCompareRunner.java/IosCompareRunner.java in spirit, but web has no scenario
 * registry: any "App URL / Screen Name" can be navigated to directly with Selenium, so
 * there's nothing to look up before running a group (see the CompareWebWithFigmaTest
 * javadoc in the samples module for how rows/groups/steps work). No TestNG (or any other
 * test framework) dependency.
 *
 * One VisualGridRunner and one BatchInfo are shared for the whole run (creating one per
 * group would repeatedly start/stop the Ultrafast Grid's background process and hang).
 * Because of that, groups only submit their checks via closeAsync() - actual results are
 * collected once, in afterSuite(), and matched back to each group's rows by test name.
 */
public class WebCompareRunner {

    private static final String DEFAULT_APP_NAME = "Applitools-Images";
    private static final RectangleSize DEFAULT_VIEWPORT = new RectangleSize(1280, 1024);

    private static final String userName = System.getProperty("user.name");

    private static String figmaExcelPath;
    private static List<FigmaRow> allRows;
    private static List<List<FigmaRow>> webGroups;
    private static VisualGridRunner visualGridRunner;
    private static BatchInfo batchInfo;

    private WebDriver driver;

    public static void beforeSuite() {
        System.out.println("Using io.eot:figmacompare version: " + AppConfig.libraryVersion());
        visualGridRunner = new VisualGridRunner(new RunnerOptions().testConcurrency(10));
        visualGridRunner.setDontCloseBatches(true);
        batchInfo = BatchSupport.createBatch(DEFAULT_APP_NAME, userName);
    }

    /**
     * Collects the results of every group submitted via compareGroup(), writes them back
     * to the Excel file, and returns whether every group passed. Returns true (no-op) if
     * loadWebGroups() was never called or found nothing to do.
     */
    public static boolean afterSuite() {
        try {
            if (null == webGroups || webGroups.isEmpty()) {
                return true;
            }
            Map<String, List<FigmaRow>> rowsByTestName = new LinkedHashMap<>();
            for (List<FigmaRow> group : webGroups) {
                rowsByTestName.put(resolveScenarioTestName(group), group);
            }

            TestResultsSummary summary = visualGridRunner.getAllTestResults(false);
            boolean isPass = ComparisonResultRecorder.recordAndCheckPass(rowsByTestName, summary);

            ExcelHelper.writeRows(figmaExcelPath, allRows);
            long passed = allRows.stream().filter(row -> "Passed".equals(row.validationStatus)).count();
            System.out.println();
            System.out.println(passed + " of " + allRows.size() + " row(s) passed. Results written to "
                    + figmaExcelPath);

            return isPass;
        } finally {
            BatchSupport.closeBatch(batchInfo);
            visualGridRunner.close();
        }
    }

    public static List<List<FigmaRow>> loadWebGroups(String figmaExcelPathOverride) {
        figmaExcelPath = FigmaExcelFile.resolvePath(figmaExcelPathOverride);
        allRows = ExcelHelper.readRows(figmaExcelPath);
        FigmaValidation.throwIfAny(FigmaValidation.validate(allRows));

        List<FigmaRow> webRows = FigmaExcelFile.filterByPlatform(allRows, "web");
        webGroups = FigmaExcelFile.groupContiguous(webRows);
        return webGroups;
    }

    public void createDriver() {
        driver = Driver.createDriverFor("chrome");
    }

    public void quitDriver() {
        if (null != driver) {
            driver.quit();
        }
    }

    /** Runs the group's steps and submits the check asynchronously (see class comment). */
    public void compareGroup(List<FigmaRow> group) {
        FigmaRow firstRow = group.get(0);
        String appName = isBlank(firstRow.appName) ? DEFAULT_APP_NAME : firstRow.appName;
        String scenarioTestName = resolveScenarioTestName(group);
        String baselineName = isBlank(firstRow.baselineEnvName)
                ? scenarioTestName + "-baseline"
                : firstRow.baselineEnvName;
        RectangleSize viewportSize = ExcelHelper.parseViewport(firstRow.viewport);
        if (null == viewportSize) {
            viewportSize = DEFAULT_VIEWPORT;
        }

        Log.section((null != FigmaExcelFile.scenarioNameOf(firstRow) ? "Scenario: " + scenarioTestName + " ("
                + group.size() + " step(s))" : "Standalone: " + scenarioTestName));
        // Same field set/order as Baseline.configureEyes() in uploadFromFigma - compare
        // side by side when a test unexpectedly shows up as new instead of matching an
        // existing baseline.
        Log.field("App Name", appName);
        Log.field("Test Name", scenarioTestName);
        Log.field("Baseline Env Name", baselineName);
        Log.field("Viewport", viewportSize.getWidth() + "x" + viewportSize.getHeight());
        Log.field("Host OS", System.getProperty("os.name"));
        Log.field("Batch Name", null != batchInfo ? batchInfo.getName() : "(none)");

        Eyes eyesSelenium = initialiseEyes(appName, baselineName, viewportSize);
        try {
            eyesSelenium.open(driver, appName, scenarioTestName, viewportSize);
            for (FigmaRow row : group) {
                String stepName = resolveStepName(row);
                Log.line("step: " + stepName + " (url: " + row.appUrlOrScreenName + ", locator: "
                        + (isBlank(row.locator) ? "(full page)" : row.locator) + ")");
                driver.get(row.appUrlOrScreenName);
                if (isBlank(row.locator)) {
                    eyesSelenium.check(stepName, Target.window());
                } else {
                    eyesSelenium.check(stepName, Target.region(parseLocator(row.locator)));
                }
            }
            eyesSelenium.closeAsync();
        } catch (RuntimeException ex) {
            for (FigmaRow row : group) {
                row.validationStatus = "Failed";
                row.errorMessage = ex.getMessage();
            }
            Log.line("FAILED: " + ex);
            eyesSelenium.abortIfNotClosed();
            throw ex;
        }
    }

    private Eyes initialiseEyes(String appName, String baselineName, RectangleSize viewportSize) {
        Eyes eyes = new Eyes(visualGridRunner);
        Configuration config = EyesConfigSupport.baseConfiguration(batchInfo, baselineName);
        config.setHostOS(System.getProperty("os.name"));
        config.setAppName(appName);
        config.setForceFullPageScreenshot(true);
        config.setAccessibilityValidation(
                new AccessibilitySettings(AccessibilityLevel.AA, AccessibilityGuidelinesVersion.WCAG_2_1));

        // Match the Figma baseline's viewport size, so the Visual Grid renders the
        // checkpoint at the same size instead of a fixed default.
        config.addBrowser(viewportSize.getWidth(), viewportSize.getHeight(), BrowserType.CHROME);

        eyes.setConfiguration(config);
        eyes.setLogHandler(new StdoutLogHandler(true));

        // Same rationale as Baseline.configureEyes()'s diagnostic - compareWithFigma
        // deliberately sets saveNewTests=false (see EyesConfigSupport.baseConfiguration),
        // so a "New" result here will never auto-resolve to Passed the way an upload can.
        Log.field("saveNewTests", String.valueOf(config.getSaveNewTests()));
        Log.field("Match Level", String.valueOf(config.getMatchLevel()));

        return eyes;
    }

    private static String resolveScenarioTestName(List<FigmaRow> group) {
        String scenarioName = FigmaExcelFile.scenarioNameOf(group.get(0));
        return null != scenarioName ? scenarioName : resolveStepName(group.get(0));
    }

    private static String resolveStepName(FigmaRow row) {
        return isBlank(row.testName) ? row.appUrlOrScreenName : row.testName;
    }

    private static By parseLocator(String locator) {
        String trimmed = locator.trim();
        return trimmed.startsWith("/") ? By.xpath(trimmed) : By.cssSelector(trimmed);
    }

    private static boolean isBlank(String value) {
        return null == value || value.isBlank();
    }
}

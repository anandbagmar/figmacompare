package io.eot.figmacompare.appium.android;

import java.util.ArrayList;
import java.util.List;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.appium.Eyes;

import io.appium.java_client.AppiumDriver;
import io.eot.figmacompare.appium.MobileRunSupport;
import io.eot.figmacompare.eyes.ComparisonResultRecorder;
import io.eot.figmacompare.eyes.MobileEyesSupport;
import io.eot.figmacompare.excel.ExcelHelper;
import io.eot.figmacompare.excel.FigmaExcelFile;
import io.eot.figmacompare.excel.FigmaRow;
import io.eot.figmacompare.excel.FigmaValidation;

/**
 * Plain-Java orchestration for the Android path of compareWithFigma (see
 * README_FigmaVisualValidation.md): for every group of "Platform=Android" rows sharing a
 * "Scenario Name", looks up that scenario in AndroidScenarioRegistry - wherever it was
 * registered - launches the matching app, runs the registered ScenarioFlow, and records
 * results. Deliberately has no TestNG (or any other test framework) dependency, so it can
 * be driven by any test runner - see CompareAndroidWithFigmaTest in the samples module for
 * a thin TestNG wrapper around it. This class does not know or care which classes have
 * registered scenarios; the caller is responsible for that (typically via
 * AndroidScenarioRegistry.loadProviderClass(...) before calling loadAndroidGroups).
 */
public class AndroidCompareRunner {

    private static final boolean IS_FULL_RESET = true;
    private static final boolean IS_EYES_ENABLED = true;

    private static MobileRunSupport.Session session;
    private static String figmaExcelPath;
    private static List<FigmaRow> allRows;

    private AppiumDriver driver;
    private Eyes eyes;

    public static void beforeSuite() {
        session = MobileRunSupport.beforeSuite("compareAndroidWithFigma");
    }

    public static void afterSuite() {
        MobileRunSupport.afterSuite(session, figmaExcelPath, allRows);
    }

    /**
     * Reads, validates, and groups the shared Excel file's Android rows. Callers must
     * ensure every scenario provider class they care about has already been loaded (e.g.
     * via AndroidScenarioRegistry.loadProviderClass(...)) before calling this, so
     * validateScenarioTests can see the full set of registered scenario names.
     */
    public static List<List<FigmaRow>> loadAndroidGroups(String figmaExcelPathOverride) {
        figmaExcelPath = FigmaExcelFile.resolvePath(figmaExcelPathOverride);
        allRows = ExcelHelper.readRows(figmaExcelPath);

        List<FigmaRow> androidRows = FigmaExcelFile.filterByPlatform(allRows, "android");
        List<String> errors = new ArrayList<>(FigmaValidation.validate(allRows));
        errors.addAll(FigmaValidation.validateScenarioTests(androidRows,
                AndroidScenarioRegistry.registeredScenarioNames()));
        FigmaValidation.throwIfAny(errors);

        return FigmaExcelFile.groupContiguous(androidRows);
    }

    /** Launches the app registered for this group's scenario. Call once per group. */
    public void createDriverForGroup(List<FigmaRow> group) {
        String scenarioName = FigmaExcelFile.scenarioNameOf(group.get(0));
        AndroidScenarioRegistry.Registration registration = AndroidScenarioRegistry.get(scenarioName);
        driver = AndroidDriverFactory.create(session.serverUrl, registration.apkPath, IS_FULL_RESET);
    }

    public void quitDriver() {
        if (null != driver) {
            driver.quit();
        }
    }

    /**
     * Runs the group's scenario against the already-launched app (see
     * createDriverForGroup) and records the result onto every row in the group. Returns
     * whether it passed; throws on an automation/runtime error (not a visual mismatch,
     * which is recorded but does not throw).
     */
    public boolean compareGroup(List<FigmaRow> group) {
        FigmaRow firstRow = group.get(0);
        String scenarioName = FigmaExcelFile.scenarioNameOf(firstRow);
        String baselineName = isBlank(firstRow.baselineEnvName)
                ? scenarioName + "-baseline"
                : firstRow.baselineEnvName;

        // Guaranteed non-null by pre-flight validation (validateScenarioTests).
        AndroidScenarioRegistry.Registration registration = AndroidScenarioRegistry.get(scenarioName);
        // The Excel row's own App Name is authoritative, same as uploadFromFigma and the
        // web compare path - this is what keeps the two sides resolving to the same
        // Applitools baseline, rather than each side independently deciding on a value
        // that only matches by convention. registration.appName is just the fallback
        // default for rows that leave the column blank.
        String appName = isBlank(firstRow.appName) ? registration.appName : firstRow.appName;

        eyes = MobileEyesSupport.open(driver, session.batch, appName, scenarioName, baselineName,
                IS_EYES_ENABLED);
        try {
            registration.flow.run(driver, eyes, group);
            TestResults testResults = eyes.close(false);
            return ComparisonResultRecorder.recordAndCheckPass(group, testResults);
        } catch (RuntimeException ex) {
            for (FigmaRow row : group) {
                row.validationStatus = "Failed";
                row.errorMessage = ex.getMessage();
            }
            eyes.abortIfNotClosed();
            throw ex;
        }
    }

    private static boolean isBlank(String value) {
        return null == value || value.isBlank();
    }
}

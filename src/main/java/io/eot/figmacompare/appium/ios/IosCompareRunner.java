package io.eot.figmacompare.appium.ios;

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
 * Plain-Java orchestration for the iOS path of compareWithFigma - mirrors
 * AndroidCompareRunner.java exactly; see its class comment and
 * README_FigmaVisualValidation.md for the full rationale. No TestNG (or any other test
 * framework) dependency - see CompareIosWithFigmaTest in the samples module for a thin
 * TestNG wrapper around it.
 */
public class IosCompareRunner {

    private static final String IOS_UDID = "B38642DE-1521-4AF0-B13A-EC710A6807E9";
    private static final String IOS_DEVICE_NAME = "iPhone 16 Pro";
    private static final String IOS_PLATFORM_VERSION = "18.1";
    private static final boolean IS_FULL_RESET = false;
    private static final boolean IS_EYES_ENABLED = true;

    private static MobileRunSupport.Session session;
    private static String figmaExcelPath;
    private static List<FigmaRow> allRows;

    private AppiumDriver driver;
    private Eyes eyes;

    public static void beforeSuite() {
        session = MobileRunSupport.beforeSuite("compareIosWithFigma");
    }

    public static void afterSuite() {
        MobileRunSupport.afterSuite(session, figmaExcelPath, allRows);
    }

    /**
     * Reads, validates, and groups the shared Excel file's iOS rows. Callers must ensure
     * every scenario provider class they care about has already been loaded (e.g. via
     * IosScenarioRegistry.loadProviderClass(...)) before calling this.
     */
    public static List<List<FigmaRow>> loadIosGroups(String figmaExcelPathOverride) {
        figmaExcelPath = FigmaExcelFile.resolvePath(figmaExcelPathOverride);
        allRows = ExcelHelper.readRows(figmaExcelPath);

        List<FigmaRow> iosRows = FigmaExcelFile.filterByPlatform(allRows, "ios");
        List<String> errors = new ArrayList<>(FigmaValidation.validate(allRows));
        errors.addAll(FigmaValidation.validateScenarioTests(iosRows, IosScenarioRegistry.registeredScenarioNames()));
        FigmaValidation.throwIfAny(errors);

        return FigmaExcelFile.groupContiguous(iosRows);
    }

    /** Launches the app registered for this group's scenario. Call once per group. */
    public void createDriverForGroup(List<FigmaRow> group) {
        String scenarioName = FigmaExcelFile.scenarioNameOf(group.get(0));
        IosScenarioRegistry.Registration registration = IosScenarioRegistry.get(scenarioName);
        driver = IosDriverFactory.create(session.serverUrl, registration.appPath, IOS_UDID, IOS_DEVICE_NAME,
                IOS_PLATFORM_VERSION, IS_FULL_RESET);
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
        IosScenarioRegistry.Registration registration = IosScenarioRegistry.get(scenarioName);
        // The Excel row's own App Name is authoritative, same as uploadFromFigma and the
        // web/Android compare paths - this is what keeps every side resolving to the same
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

package io.eot.figmacompare.eyes;

import static io.eot.figmacompare.EyesResults.displayVisualValidationResults;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.eyes.TestResultsSummary;

import io.eot.figmacompare.excel.FigmaRow;

/**
 * Records an Applitools comparison result onto every FigmaRow in a scenario group
 * (Comparison Batch URL, Validation Status) and reports whether it passed. A standalone
 * row is just a group of one, so there is a single code path for both cases.
 */
public class ComparisonResultRecorder {

    private ComparisonResultRecorder() {
    }

    /** Native/synchronous single result (e.g. Appium), applied to every row of one group. */
    public static boolean recordAndCheckPass(List<FigmaRow> rows, TestResults testResults) {
        System.out.printf("Test: %s%n%s%n", testResults.getName(), testResults);
        displayVisualValidationResults(testResults);
        applyResult(rows, testResults);
        return isPassingStatus(testResults.getStatus());
    }

    /**
     * For a Visual Grid runner shared across many groups: call this once, after every
     * group has submitted its checks via closeAsync() - getAllTestResults() reports
     * cumulatively for every test run on that runner. Results are matched back to their
     * group's rows by test name (the scenario/test name each group's eyes.open used).
     */
    public static boolean recordAndCheckPass(Map<String, List<FigmaRow>> rowsByTestName, TestResultsSummary summary) {
        AtomicBoolean allPass = new AtomicBoolean(true);
        summary.forEach(testResultContainer -> {
            TestResults testResults = testResultContainer.getTestResults();
            System.out.printf("Test: %s%n%s%n", testResults.getName(), testResultContainer);
            displayVisualValidationResults(testResults);
            List<FigmaRow> rows = rowsByTestName.get(testResults.getName());
            if (null != rows) {
                applyResult(rows, testResults);
            }
            if (!isPassingStatus(testResults.getStatus())) {
                allPass.set(false);
            }
        });
        return allPass.get();
    }

    private static void applyResult(List<FigmaRow> rows, TestResults testResults) {
        for (FigmaRow row : rows) {
            row.comparisonBatchUrl = testResults.getUrl();
            row.validationStatus = testResults.getStatus().toString();
        }
    }

    private static boolean isPassingStatus(TestResultsStatus status) {
        return !status.equals(TestResultsStatus.Failed) && !status.equals(TestResultsStatus.Unresolved);
    }
}

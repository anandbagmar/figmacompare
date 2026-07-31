package io.eot.figmacompare.excel;

import java.util.List;
import java.util.Set;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class FigmaValidationTest {

    private static FigmaRow validWebRow(int rowNumber) {
        FigmaRow row = new FigmaRow();
        row.rowNumber = rowNumber;
        row.figmaUrl = "https://www.figma.com/design/abc/Name?node-id=1-2";
        row.platform = "Web";
        row.appUrlOrScreenName = "https://example.com";
        return row;
    }

    @Test
    public void validRowProducesNoErrors() {
        List<String> errors = FigmaValidation.validate(List.of(validWebRow(2)));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    public void flagsMissingFigmaUrl() {
        FigmaRow row = validWebRow(2);
        row.figmaUrl = null;
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Figma URL is required")), errors.toString());
    }

    @Test
    public void flagsFigmaUrlMissingNodeId() {
        FigmaRow row = validWebRow(2);
        row.figmaUrl = "https://www.figma.com/design/abc/Name";
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing a node-id")), errors.toString());
    }

    @Test
    public void flagsInvalidPlatform() {
        FigmaRow row = validWebRow(2);
        row.platform = "Windows";
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Platform must be Web, Android, or iOS")), errors.toString());
    }

    @Test
    public void flagsMissingAppUrl() {
        FigmaRow row = validWebRow(2);
        row.appUrlOrScreenName = " ";
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.stream().anyMatch(e -> e.contains("App URL / Screen Name is required")), errors.toString());
    }

    @Test
    public void requiresScenarioNameForMobilePlatforms() {
        FigmaRow row = validWebRow(2);
        row.platform = "Android";
        row.appUrlOrScreenName = "Home Screen";
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Scenario Name is required")), errors.toString());
    }

    @Test
    public void flagsInvalidViewportFormat() {
        FigmaRow row = validWebRow(2);
        row.viewport = "not-a-size";
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Viewport must be in WIDTHxHEIGHT format")), errors.toString());
    }

    @Test
    public void flagsNonContiguousScenarioRows() {
        FigmaRow row1 = validWebRow(2);
        row1.scenarioName = "flow";
        FigmaRow row2 = validWebRow(3);
        row2.scenarioName = null;
        FigmaRow row3 = validWebRow(4);
        row3.scenarioName = "flow";
        List<String> errors = FigmaValidation.validate(List.of(row1, row2, row3));
        assertTrue(errors.stream().anyMatch(e -> e.contains("not contiguous")), errors.toString());
    }

    @Test
    public void flagsConflictingBaselineEnvNameWithinScenario() {
        FigmaRow row1 = validWebRow(2);
        row1.scenarioName = "flow";
        row1.baselineEnvName = "baseline-a";
        FigmaRow row2 = validWebRow(3);
        row2.scenarioName = "flow";
        row2.baselineEnvName = "baseline-b";
        List<String> errors = FigmaValidation.validate(List.of(row1, row2));
        assertTrue(errors.stream().anyMatch(e -> e.contains("conflicting Baseline Env Name")), errors.toString());
    }

    @Test
    public void flagsDuplicateStepNamesWithinScenario() {
        FigmaRow row1 = validWebRow(2);
        row1.scenarioName = "flow";
        row1.testName = "Step";
        FigmaRow row2 = validWebRow(3);
        row2.scenarioName = "flow";
        row2.testName = "Step";
        List<String> errors = FigmaValidation.validate(List.of(row1, row2));
        assertTrue(errors.stream().anyMatch(e -> e.contains("duplicate step name")), errors.toString());
    }

    @Test
    public void skippedRowsAreExcludedFromValidation() {
        FigmaRow row = validWebRow(2);
        row.figmaUrl = null; // would normally fail - should be ignored since it's skipped
        row.skip = "true";
        List<String> errors = FigmaValidation.validate(List.of(row));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    public void validateScenarioTestsFlagsUnregisteredScenario() {
        FigmaRow row = validWebRow(2);
        row.platform = "Android";
        row.scenarioName = "unregistered-flow";
        List<String> errors = FigmaValidation.validateScenarioTests(List.of(row), Set.of("other-flow"));
        assertEquals(errors.size(), 1);
        assertTrue(errors.get(0).contains("unregistered-flow"));
    }

    @Test
    public void validateScenarioTestsPassesForRegisteredScenario() {
        FigmaRow row = validWebRow(2);
        row.platform = "Android";
        row.scenarioName = "known-flow";
        List<String> errors = FigmaValidation.validateScenarioTests(List.of(row), Set.of("known-flow"));
        assertTrue(errors.isEmpty());
    }
}

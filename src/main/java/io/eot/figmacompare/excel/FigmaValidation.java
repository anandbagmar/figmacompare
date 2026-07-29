package io.eot.figmacompare.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Pre-flight validation for the shared Figma Excel file - run before uploadFromFigma or
 * compareWithFigma do any real work (Figma/Applitools API calls, browser/app launches).
 * Collects every problem found instead of stopping at the first one, so a single run
 * surfaces the full list of things to fix. See README_FigmaVisualValidation.md.
 */
public class FigmaValidation {

    private static final Set<String> VALID_PLATFORMS = Set.of("web", "android", "ios");

    private FigmaValidation() {
    }

    public static void throwIfAny(List<String> errors) {
        if (errors.isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(errors.size()).append(" problem(s) found in the Figma Excel file:").append(
                System.lineSeparator());
        for (String error : errors) {
            message.append(" - ").append(error).append(System.lineSeparator());
        }
        throw new IllegalStateException(message.toString());
    }

    /** General checks that apply regardless of platform. */
    public static List<String> validate(List<FigmaRow> allRows) {
        List<String> errors = new ArrayList<>();
        List<FigmaRow> active = FigmaExcelFile.excludeSkipped(allRows);

        for (FigmaRow row : active) {
            String where = "row " + row.rowNumber;
            if (isBlank(row.figmaUrl)) {
                errors.add(where + ": Figma URL is required");
            } else if (!row.figmaUrl.contains("node-id=")) {
                errors.add(where + ": Figma URL is missing a node-id - " + row.figmaUrl);
            }
            if (isBlank(row.platform)) {
                errors.add(where + ": Platform is required");
            } else if (!VALID_PLATFORMS.contains(row.platform.trim().toLowerCase())) {
                errors.add(where + ": Platform must be Web, Android, or iOS - got \"" + row.platform + "\"");
            }
            if (isBlank(row.appUrlOrScreenName)) {
                errors.add(where + ": App URL / Screen Name is required");
            }
            if (!isBlank(row.platform) && isMobile(row.platform) && null == FigmaExcelFile.scenarioNameOf(row)) {
                errors.add(where + ": Scenario Name is required for Platform=Android/iOS rows - mobile screens "
                        + "may need bespoke login/navigation, so every mobile test is dispatched by Scenario "
                        + "Name, even for a single screen.");
            }
            if (!isBlank(row.viewport)) {
                try {
                    ExcelHelper.parseViewport(row.viewport);
                } catch (IllegalArgumentException ex) {
                    errors.add(where + ": " + ex.getMessage());
                }
            }
        }

        errors.addAll(validateScenarioGrouping(active));
        return errors;
    }

    /**
     * Additional mobile-only check: every distinct Scenario Name used by these rows must
     * have a registered scenario test (e.g. BajajFinservAndroidTest.SCENARIO_TESTS).
     */
    public static List<String> validateScenarioTests(List<FigmaRow> platformRows, Set<String> registeredScenarios) {
        List<String> errors = new ArrayList<>();
        Set<String> seenScenarios = new LinkedHashSet<>();
        for (FigmaRow row : platformRows) {
            String scenarioName = FigmaExcelFile.scenarioNameOf(row);
            if (null == scenarioName || !seenScenarios.add(scenarioName)) {
                continue;
            }
            if (!registeredScenarios.contains(scenarioName)) {
                errors.add("row " + row.rowNumber + ": no scenario test registered for Scenario Name \""
                        + scenarioName + "\"");
            }
        }
        return errors;
    }

    private static boolean isMobile(String platform) {
        String normalized = platform.trim().toLowerCase();
        return "android".equals(normalized) || "ios".equals(normalized);
    }

    private static List<String> validateScenarioGrouping(List<FigmaRow> rows) {
        List<String> errors = new ArrayList<>();
        List<List<FigmaRow>> chunks = FigmaExcelFile.groupContiguous(rows);

        Map<String, List<List<FigmaRow>>> chunksByScenario = new LinkedHashMap<>();
        for (List<FigmaRow> chunk : chunks) {
            String scenarioName = FigmaExcelFile.scenarioNameOf(chunk.get(0));
            if (null != scenarioName) {
                chunksByScenario.computeIfAbsent(scenarioName, key -> new ArrayList<>()).add(chunk);
            }
        }

        for (Map.Entry<String, List<List<FigmaRow>>> entry : chunksByScenario.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> ranges = new ArrayList<>();
                for (List<FigmaRow> chunk : entry.getValue()) {
                    ranges.add(rowRange(chunk));
                }
                errors.add("Scenario \"" + entry.getKey() + "\" rows are not contiguous: found at "
                        + String.join(", ", ranges) + ". All rows for the same scenario must be adjacent.");
            }
        }

        for (List<FigmaRow> chunk : chunks) {
            String scenarioName = FigmaExcelFile.scenarioNameOf(chunk.get(0));
            if (null == scenarioName || chunk.size() < 2) {
                continue;
            }
            errors.addAll(checkConsistent(scenarioName, chunk, "Baseline Env Name", row -> row.baselineEnvName));
            errors.addAll(checkConsistent(scenarioName, chunk, "App Name", row -> row.appName));
            errors.addAll(checkConsistent(scenarioName, chunk, "Viewport", row -> row.viewport));
            errors.addAll(validateUniqueStepNames(scenarioName, chunk));
        }
        return errors;
    }

    private static List<String> checkConsistent(String scenarioName, List<FigmaRow> chunk, String fieldLabel,
            Function<FigmaRow, String> getter) {
        String reference = null;
        int referenceRow = -1;
        for (FigmaRow row : chunk) {
            String value = getter.apply(row);
            if (isBlank(value)) {
                continue;
            }
            if (null == reference) {
                reference = value;
                referenceRow = row.rowNumber;
            } else if (!reference.equals(value)) {
                return List.of("Scenario \"" + scenarioName + "\" has conflicting " + fieldLabel + ": \""
                        + reference + "\" (row " + referenceRow + ") vs \"" + value + "\" (row " + row.rowNumber
                        + ")");
            }
        }
        return List.of();
    }

    private static List<String> validateUniqueStepNames(String scenarioName, List<FigmaRow> chunk) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (FigmaRow row : chunk) {
            String stepName = isBlank(row.testName) ? row.appUrlOrScreenName : row.testName;
            if (null == stepName) {
                continue;
            }
            Integer firstRow = seen.get(stepName);
            if (null != firstRow) {
                errors.add("Scenario \"" + scenarioName + "\" has duplicate step name \"" + stepName + "\" (rows "
                        + firstRow + " and " + row.rowNumber + ")");
            } else {
                seen.put(stepName, row.rowNumber);
            }
        }
        return errors;
    }

    private static String rowRange(List<FigmaRow> chunk) {
        int first = chunk.get(0).rowNumber;
        int last = chunk.get(chunk.size() - 1).rowNumber;
        return first == last ? String.valueOf(first) : first + "-" + last;
    }

    private static boolean isBlank(String value) {
        return null == value || value.isBlank();
    }
}

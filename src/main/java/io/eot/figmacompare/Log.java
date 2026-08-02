package io.eot.figmacompare;

import com.applitools.eyes.TestResults;

/**
 * Minimal structured console output for uploadFromFigma/compareWithFigma - this project
 * deliberately has no real logging framework (see AppConfig), so this just keeps the
 * handful of things these runners print consistently formatted instead of a raw mix of
 * unindented System.out.println calls. Not a general-purpose logger.
 */
public final class Log {

    private static final String RULE = "=".repeat(60);
    private static final String SUB_RULE = "-".repeat(60);

    private Log() {
    }

    public static void header(String title) {
        System.out.println();
        System.out.println(RULE);
        System.out.println(title);
        System.out.println(RULE);
    }

    public static void field(String label, String value) {
        System.out.printf("  %-18s: %s%n", label, value);
    }

    public static void section(String title) {
        System.out.println();
        System.out.println(SUB_RULE);
        System.out.println(title);
        System.out.println(SUB_RULE);
    }

    public static void line(String text) {
        System.out.println("  " + text);
    }

    /** Status here is the authoritative Passed/Unresolved/Failed/... from Applitools itself. */
    public static void testResults(TestResults results) {
        field("Status", String.valueOf(results.getStatus()));
        field("Steps", results.getSteps() + " (matches=" + results.getMatches() + ", mismatches="
                + results.getMismatches() + ", missing=" + results.getMissing() + ")");
        field("Batch URL", String.valueOf(results.getUrl()));
    }

    public static void summary(String title) {
        System.out.println();
        System.out.println(RULE);
        System.out.println(title);
        System.out.println(RULE);
    }
}

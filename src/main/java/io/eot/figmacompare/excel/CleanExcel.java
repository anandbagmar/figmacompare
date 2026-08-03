package io.eot.figmacompare.excel;

import java.util.List;

/**
 * Resets the write-back result columns in the shared Figma Excel file - App Name,
 * Baseline Batch URL, Status, Error Message, Comparison Batch URL, Validation Status -
 * back to blank, leaving every manual-input column (including Baseline Env Name,
 * Viewport, and Test Name, which are input with an auto-derive fallback rather than pure
 * results) untouched. Useful to get a clean slate of results without touching anything
 * that controls how uploadFromFigma/compareWithFigma behave.
 */
public final class CleanExcel {

    private CleanExcel() {
    }

    /**
     * Usage: CleanExcel [figmaExcelPath] (optional, also settable via -DfigmaExcel) -
     * resolved the same way as UploadFromFigma/compareWithFigma, see FigmaExcelFile.
     */
    public static void main(String[] args) {
        String pathOverride = args.length > 0 ? args[0] : System.getProperty("figmaExcel");
        clean(FigmaExcelFile.resolvePath(pathOverride));
    }

    /** Clears the result columns on every row (including skipped ones) and writes the file back in place. */
    public static void clean(String figmaExcelPath) {
        List<FigmaRow> allRows = ExcelHelper.readRows(figmaExcelPath);
        for (FigmaRow row : allRows) {
            row.appName = null;
            row.baselineBatchUrl = null;
            row.status = null;
            row.errorMessage = null;
            row.comparisonBatchUrl = null;
            row.validationStatus = null;
        }
        ExcelHelper.writeRows(figmaExcelPath, allRows);
        System.out.println("Cleared App Name/Baseline Batch URL/Status/Error Message/Comparison Batch "
                + "URL/Validation Status on " + allRows.size() + " row(s). Results written to " + figmaExcelPath);
    }
}

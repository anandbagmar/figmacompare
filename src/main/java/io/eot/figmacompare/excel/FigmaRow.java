package io.eot.figmacompare.excel;

public class FigmaRow {

    /** 1-based row number in the sheet (for validation/error messages), not a column. */
    public int rowNumber;

    public String figmaUrl;
    public String platform;
    public String appUrlOrScreenName;
    public String scenarioName;
    public String testName;
    public String baselineEnvName;
    public String viewport;
    public String scale;
    public String format;
    public String skip;

    public String appName;
    public String baselineBatchUrl;
    public String status;
    public String errorMessage;

    public String locator;
    public String comparisonBatchUrl;
    public String validationStatus;
}

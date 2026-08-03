package io.eot.figmacompare.excel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class CleanExcelTest {

    @Test
    public void clearsOnlyTheResultColumns() throws IOException {
        File tempFile = File.createTempFile("figmacompare-cleanexcel-test", ".xlsx");
        try {
            FigmaRow row = new FigmaRow();
            row.figmaUrl = "https://www.figma.com/design/abc/Name?node-id=1-2";
            row.platform = "Web";
            row.appUrlOrScreenName = "https://example.com";
            row.scenarioName = "flow";
            row.testName = "Step 1";
            row.baselineEnvName = "flow-baseline";
            row.viewport = "1280x900";
            row.scale = "1";
            row.format = "png";
            row.skip = "";
            row.locator = "[data-testid=\"x\"]";
            row.appName = "MockedE2EDemo";
            row.baselineBatchUrl = "https://eyes.applitools.com/app/batches/1/2";
            row.status = "Success";
            row.errorMessage = "some prior error";
            row.comparisonBatchUrl = "https://eyes.applitools.com/app/batches/3/4";
            row.validationStatus = "Passed";
            ExcelHelper.writeRows(tempFile.getAbsolutePath(), List.of(row));

            CleanExcel.clean(tempFile.getAbsolutePath());
            FigmaRow cleaned = ExcelHelper.readRows(tempFile.getAbsolutePath()).get(0);

            // Result columns: cleared.
            assertEquals(cleaned.appName, "");
            assertEquals(cleaned.baselineBatchUrl, "");
            assertEquals(cleaned.status, "");
            assertEquals(cleaned.errorMessage, "");
            assertEquals(cleaned.comparisonBatchUrl, "");
            assertEquals(cleaned.validationStatus, "");

            // Manual-input columns, including the auto-derive-but-sticky ones: untouched.
            assertEquals(cleaned.figmaUrl, row.figmaUrl);
            assertEquals(cleaned.platform, row.platform);
            assertEquals(cleaned.appUrlOrScreenName, row.appUrlOrScreenName);
            assertEquals(cleaned.scenarioName, row.scenarioName);
            assertEquals(cleaned.testName, row.testName);
            assertEquals(cleaned.baselineEnvName, row.baselineEnvName);
            assertEquals(cleaned.viewport, row.viewport);
            assertEquals(cleaned.scale, row.scale);
            assertEquals(cleaned.format, row.format);
            assertEquals(cleaned.locator, row.locator);
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    @Test
    public void handlesRowsThatNeverHadResultsYet() throws IOException {
        File tempFile = File.createTempFile("figmacompare-cleanexcel-test", ".xlsx");
        try {
            FigmaRow row = new FigmaRow();
            row.figmaUrl = "https://www.figma.com/design/abc/Name?node-id=1-2";
            row.platform = "Web";
            ExcelHelper.writeRows(tempFile.getAbsolutePath(), List.of(row));

            CleanExcel.clean(tempFile.getAbsolutePath());
            FigmaRow cleaned = ExcelHelper.readRows(tempFile.getAbsolutePath()).get(0);

            assertEquals(cleaned.status, "");
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }
}

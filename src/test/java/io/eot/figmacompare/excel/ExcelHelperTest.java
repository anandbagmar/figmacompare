package io.eot.figmacompare.excel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.testng.annotations.Test;

import com.applitools.eyes.RectangleSize;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

public class ExcelHelperTest {

    @Test
    public void parseViewportParsesWidthAndHeight() {
        RectangleSize size = ExcelHelper.parseViewport("1280x900");
        assertEquals(size.getWidth(), 1280);
        assertEquals(size.getHeight(), 900);
    }

    @Test
    public void parseViewportIsCaseInsensitiveAndTrims() {
        RectangleSize size = ExcelHelper.parseViewport(" 1280X900 ");
        assertEquals(size.getWidth(), 1280);
        assertEquals(size.getHeight(), 900);
    }

    @Test
    public void parseViewportReturnsNullForBlank() {
        assertNull(ExcelHelper.parseViewport(null));
        assertNull(ExcelHelper.parseViewport(""));
        assertNull(ExcelHelper.parseViewport("   "));
    }

    @Test
    public void parseViewportThrowsForInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> ExcelHelper.parseViewport("1280"));
        assertThrows(NumberFormatException.class, () -> ExcelHelper.parseViewport("abcxdef"));
    }

    @Test
    public void writeThenReadRoundTripsAllFields() throws IOException {
        File tempFile = File.createTempFile("figmacompare-excelhelper-test", ".xlsx");
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
            row.locator = "[data-testid=\"x\"]";

            ExcelHelper.writeRows(tempFile.getAbsolutePath(), List.of(row));
            List<FigmaRow> readBack = ExcelHelper.readRows(tempFile.getAbsolutePath());

            assertEquals(readBack.size(), 1);
            FigmaRow readRow = readBack.get(0);
            assertEquals(readRow.figmaUrl, row.figmaUrl);
            assertEquals(readRow.platform, row.platform);
            assertEquals(readRow.appUrlOrScreenName, row.appUrlOrScreenName);
            assertEquals(readRow.scenarioName, row.scenarioName);
            assertEquals(readRow.testName, row.testName);
            assertEquals(readRow.baselineEnvName, row.baselineEnvName);
            assertEquals(readRow.viewport, row.viewport);
            assertEquals(readRow.locator, row.locator);
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    @Test
    public void readRowsSkipsRowsWithBlankFigmaUrl() throws IOException {
        File tempFile = File.createTempFile("figmacompare-excelhelper-test", ".xlsx");
        try {
            FigmaRow withUrl = new FigmaRow();
            withUrl.figmaUrl = "https://www.figma.com/design/abc/Name?node-id=1-2";
            withUrl.platform = "Web";

            FigmaRow withoutUrl = new FigmaRow();
            withoutUrl.platform = "Web";

            ExcelHelper.writeRows(tempFile.getAbsolutePath(), List.of(withUrl, withoutUrl));
            List<FigmaRow> readBack = ExcelHelper.readRows(tempFile.getAbsolutePath());

            assertEquals(readBack.size(), 1);
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }
}

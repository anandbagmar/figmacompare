package io.eot.figmacompare.excel;

import java.util.List;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class FigmaExcelFileTest {

    private static FigmaRow row(int rowNumber, String platform, String scenarioName, String skip) {
        FigmaRow row = new FigmaRow();
        row.rowNumber = rowNumber;
        row.platform = platform;
        row.scenarioName = scenarioName;
        row.skip = skip;
        return row;
    }

    @Test
    public void excludeSkippedFiltersOutEverySkipVariant() {
        List<FigmaRow> rows = List.of(
                row(1, "Web", null, null),
                row(2, "Web", null, "true"),
                row(3, "Web", null, "SKIP"),
                row(4, "Web", null, "no"));
        List<FigmaRow> active = FigmaExcelFile.excludeSkipped(rows);
        assertEquals(active.size(), 2);
        assertEquals(active.get(0).rowNumber, 1);
        assertEquals(active.get(1).rowNumber, 4);
    }

    @Test
    public void filterByPlatformIsCaseInsensitiveAndExcludesSkipped() {
        List<FigmaRow> rows = List.of(
                row(1, "Web", null, null),
                row(2, "web", null, null),
                row(3, "Android", null, null),
                row(4, "Web", null, "true"));
        List<FigmaRow> webRows = FigmaExcelFile.filterByPlatform(rows, "Web");
        assertEquals(webRows.size(), 2);
    }

    @Test
    public void scenarioNameOfTrimsAndTreatsBlankAsNull() {
        assertNull(FigmaExcelFile.scenarioNameOf(row(1, "Web", null, null)));
        assertNull(FigmaExcelFile.scenarioNameOf(row(1, "Web", "   ", null)));
        assertEquals(FigmaExcelFile.scenarioNameOf(row(1, "Web", " flow ", null)), "flow");
    }

    @Test
    public void groupContiguousGroupsOnlyAdjacentSameNamedRows() {
        List<FigmaRow> rows = List.of(
                row(1, "Web", null, null),
                row(2, "Web", "flow", null),
                row(3, "Web", "flow", null),
                row(4, "Web", null, null),
                row(5, "Web", "flow", null));
        List<List<FigmaRow>> chunks = FigmaExcelFile.groupContiguous(rows);
        assertEquals(chunks.size(), 4);
        assertEquals(chunks.get(0).size(), 1);
        assertEquals(chunks.get(1).size(), 2);
        assertEquals(chunks.get(2).size(), 1);
        assertEquals(chunks.get(3).size(), 1);
    }

    @Test
    public void isSkippedRecognizesAllSkipValuesCaseInsensitively() {
        for (String value : new String[] { "true", "T", "yes", "Y", "skip", "SKIP" }) {
            assertTrue(FigmaExcelFile.isSkipped(row(1, "Web", null, value)), "expected skip=" + value + " to be skipped");
        }
        for (String value : new String[] { null, "", "false", "no", "n" }) {
            assertFalse(FigmaExcelFile.isSkipped(row(1, "Web", null, value)), "expected skip=" + value + " to NOT be skipped");
        }
    }
}

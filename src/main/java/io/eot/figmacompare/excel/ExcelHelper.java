package io.eot.figmacompare.excel;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.applitools.eyes.RectangleSize;

public class ExcelHelper {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private static final String COL_FIGMA_URL = "Figma URL";
    private static final String COL_PLATFORM = "Platform";
    private static final String COL_APP_URL_OR_SCREEN_NAME = "App URL / Screen Name";
    private static final String COL_SCENARIO_NAME = "Scenario Name";
    private static final String COL_TEST_NAME = "Test Name";
    private static final String COL_BASELINE_ENV_NAME = "Baseline Env Name";
    private static final String COL_VIEWPORT = "Viewport";
    private static final String COL_SCALE = "Scale";
    private static final String COL_FORMAT = "Format";
    private static final String COL_SKIP = "Skip";
    private static final String COL_APP_NAME = "App Name";
    private static final String COL_BASELINE_BATCH_URL = "Baseline Batch URL";
    private static final String COL_STATUS = "Status";
    private static final String COL_ERROR_MESSAGE = "Error Message";
    private static final String COL_LOCATOR = "Locator";
    private static final String COL_COMPARISON_BATCH_URL = "Comparison Batch URL";
    private static final String COL_VALIDATION_STATUS = "Validation Status";

    /**
     * The single, fixed schema for the unified Figma visual-testing Excel file. Always
     * written in this order, regardless of what order columns happen to be in on disk -
     * this keeps read/write simple since the file is now updated in place at every stage
     * instead of being copied between stage-specific files.
     */
    private static final List<String> ALL_COLUMNS = List.of(
            COL_FIGMA_URL, COL_PLATFORM, COL_APP_URL_OR_SCREEN_NAME, COL_SCENARIO_NAME, COL_TEST_NAME,
            COL_BASELINE_ENV_NAME, COL_VIEWPORT, COL_SCALE, COL_FORMAT, COL_SKIP,
            COL_APP_NAME, COL_BASELINE_BATCH_URL, COL_STATUS, COL_ERROR_MESSAGE,
            COL_LOCATOR, COL_COMPARISON_BATCH_URL, COL_VALIDATION_STATUS);

    private ExcelHelper() {
    }

    public static List<FigmaRow> readRows(String inputPath) {
        List<FigmaRow> rows = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(inputPath); Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerIndex = readHeaderIndex(sheet.getRow(0));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (null == row || isBlank(getCellValue(row, headerIndex.get(COL_FIGMA_URL)))) {
                    continue;
                }
                FigmaRow figmaRow = new FigmaRow();
                figmaRow.rowNumber = i + 1;
                figmaRow.figmaUrl = getCellValue(row, headerIndex.get(COL_FIGMA_URL));
                figmaRow.platform = getCellValue(row, headerIndex.get(COL_PLATFORM));
                figmaRow.appUrlOrScreenName = getCellValue(row, headerIndex.get(COL_APP_URL_OR_SCREEN_NAME));
                figmaRow.scenarioName = getCellValue(row, headerIndex.get(COL_SCENARIO_NAME));
                figmaRow.testName = getCellValue(row, headerIndex.get(COL_TEST_NAME));
                figmaRow.baselineEnvName = getCellValue(row, headerIndex.get(COL_BASELINE_ENV_NAME));
                figmaRow.viewport = getCellValue(row, headerIndex.get(COL_VIEWPORT));
                figmaRow.scale = getCellValue(row, headerIndex.get(COL_SCALE));
                figmaRow.format = getCellValue(row, headerIndex.get(COL_FORMAT));
                figmaRow.skip = getCellValue(row, headerIndex.get(COL_SKIP));
                figmaRow.appName = getCellValue(row, headerIndex.get(COL_APP_NAME));
                figmaRow.baselineBatchUrl = getCellValue(row, headerIndex.get(COL_BASELINE_BATCH_URL));
                figmaRow.status = getCellValue(row, headerIndex.get(COL_STATUS));
                figmaRow.errorMessage = getCellValue(row, headerIndex.get(COL_ERROR_MESSAGE));
                figmaRow.locator = getCellValue(row, headerIndex.get(COL_LOCATOR));
                figmaRow.comparisonBatchUrl = getCellValue(row, headerIndex.get(COL_COMPARISON_BATCH_URL));
                figmaRow.validationStatus = getCellValue(row, headerIndex.get(COL_VALIDATION_STATUS));
                rows.add(figmaRow);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to read input Excel file: " + inputPath, ex);
        }
        return rows;
    }

    /** Overwrites the same file in place - the whole point of the unified single-file flow. */
    public static void writeRows(String path, List<FigmaRow> rows) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Baselines");
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < ALL_COLUMNS.size(); col++) {
                headerRow.createCell(col).setCellValue(ALL_COLUMNS.get(col));
            }

            int rowNum = 1;
            for (FigmaRow figmaRow : rows) {
                Map<String, String> values = toMap(figmaRow);
                Row row = sheet.createRow(rowNum++);
                for (int col = 0; col < ALL_COLUMNS.size(); col++) {
                    row.createCell(col).setCellValue(values.getOrDefault(ALL_COLUMNS.get(col), ""));
                }
            }
            for (int col = 0; col < ALL_COLUMNS.size(); col++) {
                sheet.autoSizeColumn(col);
            }

            try (FileOutputStream fos = new FileOutputStream(path)) {
                workbook.write(fos);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write Excel file: " + path, ex);
        }
    }

    public static RectangleSize parseViewport(String viewport) {
        if (isBlank(viewport)) {
            return null;
        }
        String[] parts = viewport.toLowerCase().split("x");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Viewport must be in WIDTHxHEIGHT format, got: " + viewport);
        }
        return new RectangleSize(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    private static Map<String, String> toMap(FigmaRow row) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(COL_FIGMA_URL, nullToEmpty(row.figmaUrl));
        map.put(COL_PLATFORM, nullToEmpty(row.platform));
        map.put(COL_APP_URL_OR_SCREEN_NAME, nullToEmpty(row.appUrlOrScreenName));
        map.put(COL_SCENARIO_NAME, nullToEmpty(row.scenarioName));
        map.put(COL_TEST_NAME, nullToEmpty(row.testName));
        map.put(COL_BASELINE_ENV_NAME, nullToEmpty(row.baselineEnvName));
        map.put(COL_VIEWPORT, nullToEmpty(row.viewport));
        map.put(COL_SCALE, nullToEmpty(row.scale));
        map.put(COL_FORMAT, nullToEmpty(row.format));
        map.put(COL_SKIP, nullToEmpty(row.skip));
        map.put(COL_APP_NAME, nullToEmpty(row.appName));
        map.put(COL_BASELINE_BATCH_URL, nullToEmpty(row.baselineBatchUrl));
        map.put(COL_STATUS, nullToEmpty(row.status));
        map.put(COL_ERROR_MESSAGE, nullToEmpty(row.errorMessage));
        map.put(COL_LOCATOR, nullToEmpty(row.locator));
        map.put(COL_COMPARISON_BATCH_URL, nullToEmpty(row.comparisonBatchUrl));
        map.put(COL_VALIDATION_STATUS, nullToEmpty(row.validationStatus));
        return map;
    }

    private static Map<String, Integer> readHeaderIndex(Row headerRow) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            index.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
        }
        return index;
    }

    private static String getCellValue(Row row, Integer columnIndex) {
        if (null == columnIndex) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (null == cell) {
            return null;
        }
        return DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private static boolean isBlank(String value) {
        return null == value || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return null == value ? "" : value;
    }
}

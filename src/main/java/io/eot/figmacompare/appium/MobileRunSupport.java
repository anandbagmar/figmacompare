package io.eot.figmacompare.appium;

import java.util.List;

import com.applitools.eyes.BatchInfo;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.eot.figmacompare.config.AppConfig;
import io.eot.figmacompare.excel.ExcelHelper;
import io.eot.figmacompare.excel.FigmaRow;
import io.eot.figmacompare.eyes.BatchSupport;

/**
 * The suite-level setup/teardown shared by every mobile compareWithFigma runner
 * (AndroidCompareRunner, IosCompareRunner): start a local Appium server and an Applitools
 * batch before the suite, then on the way out stop the server, close the batch, write
 * results back to the Excel file, and print the pass/fail summary. Platform-specific work
 * (looking up a scenario, launching the right app, running its ScenarioFlow) stays in
 * each runner - this only covers the identical bookend logic.
 */
public class MobileRunSupport {

    private MobileRunSupport() {
    }

    public static Session beforeSuite(String defaultBatchName) {
        System.out.println("Using io.eot:figmacompare version: " + AppConfig.libraryVersion());
        AppiumDriverLocalService server = AppiumServerSupport.start(AppiumServerSupport.defaultLogFileDir());
        String serverUrl = server.getUrl().toString();
        BatchInfo batch = BatchSupport.createSuiteBatch(AppConfig.get("APPLITOOLS_BATCH_NAME", defaultBatchName));
        return new Session(server, serverUrl, batch);
    }

    public static void afterSuite(Session session, String figmaExcelPath, List<FigmaRow> allRows) {
        BatchSupport.closeBatch(session.batch);
        AppiumServerSupport.stop(session.server);
        if (null != allRows && !allRows.isEmpty()) {
            ExcelHelper.writeRows(figmaExcelPath, allRows);
            long passed = allRows.stream().filter(row -> "Passed".equals(row.validationStatus)).count();
            System.out.println();
            System.out.println(passed + " of " + allRows.size() + " row(s) passed. Results written to "
                    + figmaExcelPath);
        }
    }

    public static class Session {
        public final AppiumDriverLocalService server;
        public final String serverUrl;
        public final BatchInfo batch;

        public Session(AppiumDriverLocalService server, String serverUrl, BatchInfo batch) {
            this.server = server;
            this.serverUrl = serverUrl;
            this.batch = batch;
        }
    }
}

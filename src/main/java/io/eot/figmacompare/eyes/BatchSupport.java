package io.eot.figmacompare.eyes;

import java.io.File;

import com.applitools.eyes.BatchInfo;

public class BatchSupport {

    private BatchSupport() {
    }

    /** One batch per test method run, e.g. for Selenium tests using the Visual Grid runner. */
    public static BatchInfo createBatch(String appName, String userName) {
        BatchInfo batch = new BatchInfo(withCiRunSuffix(userName + "-" + appName));
        batch.setNotifyOnCompletion(false);
        batch.addProperty("REPOSITORY_NAME", new File(System.getProperty("user.dir")).getName());
        batch.addProperty("APP_NAME", appName);
        return batch;
    }

    /** One batch shared across the whole suite, e.g. for native Appium tests. */
    public static BatchInfo createSuiteBatch(String defaultBatchName) {
        String ciBatchName = System.getenv("APPLITOOLS_BATCH_NAME");
        BatchInfo batch = new BatchInfo(withCiRunSuffix(null == ciBatchName ? defaultBatchName : ciBatchName));
        batch.addProperty("REPOSITORY_NAME", new File(System.getProperty("user.dir")).getName());
        System.out.printf("Batch name: %s%n", batch.getName());
        System.out.printf("Batch startedAt: %s%n", batch.getStartedAt().getTime());
        System.out.printf("Batch BatchId: %s%n", batch.getId());
        return batch;
    }

    /**
     * Appends " - #<run number>" when running in GitHub Actions - GITHUB_RUN_NUMBER is
     * set automatically by the runner for every job, no workflow wiring needed - so
     * batches from different CI runs are distinguishable in the Applitools dashboard
     * (matches the run number shown in the Actions UI, e.g. "Java CI with Gradle #26").
     * No-op outside CI (e.g. local runs), where the env var isn't set.
     */
    public static String withCiRunSuffix(String batchName) {
        String runNumber = System.getenv("GITHUB_RUN_NUMBER");
        return (null == runNumber || runNumber.isBlank()) ? batchName : batchName + " - #" + runNumber;
    }

    public static void closeBatch(BatchInfo batch) {
        if (null != batch) {
            batch.setCompleted(true);
        }
    }
}

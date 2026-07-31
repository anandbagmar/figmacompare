package io.eot.figmacompare.eyes;

import java.io.File;

import com.applitools.eyes.BatchInfo;

import io.eot.figmacompare.config.AppConfig;

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
     * Appends " - #<run number>" when the consumer sets CI_RUN_NUMBER (env var or
     * config.properties - see AppConfig). This library is CI-provider agnostic, so it
     * never reads a provider-specific variable (e.g. GitHub Actions' GITHUB_RUN_NUMBER)
     * itself - the consumer's own CI workflow is responsible for mapping its provider's
     * run number into this generic key (see figmacompare-sample's gradle.yml, which
     * sets CI_RUN_NUMBER: ${{ github.run_number }}), so batches from different CI runs
     * are distinguishable in the Applitools dashboard. No-op when unset (e.g. local runs).
     */
    public static String withCiRunSuffix(String batchName) {
        String runNumber = AppConfig.get("CI_RUN_NUMBER");
        return (null == runNumber || runNumber.isBlank()) ? batchName : batchName + " - #" + runNumber;
    }

    public static void closeBatch(BatchInfo batch) {
        if (null != batch) {
            batch.setCompleted(true);
        }
    }
}

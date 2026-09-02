package io.eot.figmacompare;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.EyesRunner;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.images.ImageRunner;
import com.applitools.eyes.images.Target;

public class Baseline {

    public static void uploadImageAndSetAsBaseline(String baseLineFilePath, String baselineName, String appName,
            String testName, RectangleSize viewportSize) {
        EyesRunner runner = new ImageRunner();
        try {
            doUpload(runner, baseLineFilePath, baselineName, appName, testName, viewportSize, null, null, null);
        } finally {
            runner.close();
        }
    }

    public static BaselineUploadResult uploadImageAndSetAsBaseline(String baseLineFilePath, String baselineName,
            String appName, String testName, RectangleSize viewportSize, String apiKey, String serverUrl) {
        EyesRunner runner = new ImageRunner();
        try {
            return uploadImageAndSetAsBaseline(runner, baseLineFilePath, baselineName, appName, testName,
                    viewportSize, apiKey, serverUrl, null);
        } finally {
            runner.close();
        }
    }

    /**
     * Same as the other overload, but reuses a caller-supplied, caller-owned EyesRunner
     * instead of creating (and closing) a new one per call, and lets the caller pin an
     * explicit, shared BatchInfo. Applitools' EyesRunner starts a background "universal
     * core" process on creation - reuse one runner across many uploads (e.g. a batch
     * loop) instead of repeatedly starting/stopping it per image. Pass the *same*
     * BatchInfo instance to every call in the loop - each BatchInfo has its own batch ID,
     * so constructing a new one per call would still group uploads into separate batches
     * even if they all share the same name. Without an explicit batch, the SDK lazily
     * creates a default one on the first eyes.open() call and reuses it for every later
     * test on the same runner - so when sharing a runner across a loop, always pass a
     * real batch here, or every row ends up sharing whichever batch got auto-created for
     * the first row. The caller is responsible for calling runner.close() once, after all
     * uploads.
     */
    public static BaselineUploadResult uploadImageAndSetAsBaseline(EyesRunner runner, String baseLineFilePath,
            String baselineName, String appName, String testName, RectangleSize viewportSize, String apiKey,
            String serverUrl, BatchInfo batch) {
        requireCredentials(apiKey, serverUrl);
        return doUpload(runner, baseLineFilePath, baselineName, appName, testName, viewportSize, apiKey, serverUrl,
                batch);
    }

    /**
     * Uploads a whole ordered sequence of images as the steps of ONE Applitools test (one
     * eyes.open, one check() per step, one close) - for a Figma "scenario" of 1-n frames
     * exported together, matching how the Applitools Figma plugin itself uploads a
     * multi-frame scenario (confirmed via HAR: one open with scenarioIdOrName, one match
     * call per frame with its own step name, one close). A single-element steps list is
     * exactly equivalent to the single-image overload above - there's no separate
     * "standalone" code path, a standalone row is just a scenario of one step.
     */
    public static BaselineUploadResult uploadScenarioAndSetAsBaseline(EyesRunner runner, String appName,
            String scenarioTestName, String baselineName, RectangleSize viewportSize, String apiKey,
            String serverUrl, BatchInfo batch, List<ScenarioStep> steps) {
        return uploadScenarioAndSetAsBaseline(runner, appName, scenarioTestName, baselineName, viewportSize, apiKey,
                serverUrl, batch, steps, true);
    }

    /**
     * Same as the other overload, but lets the caller skip forcing Host OS to the
     * upload machine's own OS ({@code setHostOS = false}) - e.g. for Android/iOS rows,
     * where the upload happens on a plain Java process with no live device, so there is
     * no real device OS to report and claiming the upload machine's OS (e.g. "Mac OS X")
     * would misrepresent the dashboard for a test that will actually run on a mobile
     * device/emulator. Baseline matching still works either way since it's keyed on
     * Baseline Env Name, not Host OS.
     */
    public static BaselineUploadResult uploadScenarioAndSetAsBaseline(EyesRunner runner, String appName,
            String scenarioTestName, String baselineName, RectangleSize viewportSize, String apiKey,
            String serverUrl, BatchInfo batch, List<ScenarioStep> steps, boolean setHostOS) {
        requireCredentials(apiKey, serverUrl);
        if (null == steps || steps.isEmpty()) {
            throw new IllegalArgumentException("A scenario needs at least one step");
        }

        com.applitools.eyes.images.Eyes eyesImages = configureEyes(runner, appName, baselineName, apiKey, serverUrl,
                batch, setHostOS);
        try {
            RectangleSize resolvedViewport = viewportSize;
            if (null == resolvedViewport) {
                BufferedImage firstImage = ImageIO.read(new File(steps.get(0).imagePath));
                resolvedViewport = new RectangleSize(firstImage.getWidth(), firstImage.getHeight());
                Log.line("Viewport not provided - using first step's image size: " + resolvedViewport.getWidth()
                        + "x" + resolvedViewport.getHeight());
            }
            Log.field("Test Name", scenarioTestName);
            Log.field("Viewport", resolvedViewport.getWidth() + "x" + resolvedViewport.getHeight());
            eyesImages.open(appName, scenarioTestName, resolvedViewport);
            int stepNum = 1;
            for (ScenarioStep step : steps) {
                File imageFile = new File(step.imagePath);
                Log.line("step " + stepNum + "/" + steps.size() + ": " + step.stepName + " (image: "
                        + imageFile.getName() + ", exists=" + imageFile.exists() + ")");
                BufferedImage img = ImageIO.read(imageFile);
                eyesImages.check(step.stepName, Target.image(img));
                stepNum++;
            }
            TestResults testResults = eyesImages.close(false);
            Log.testResults(testResults);
            return new BaselineUploadResult(testResults, resolvedViewport);
        } catch (Exception ex) {
            Log.line("FAILED: " + ex);
            ex.printStackTrace();
            return new BaselineUploadResult(null, viewportSize);
        } finally {
            eyesImages.abortIfNotClosed();
        }
    }

    private static BaselineUploadResult doUpload(EyesRunner runner, String baseLineFilePath, String baselineName,
            String appName, String testName, RectangleSize viewportSize, String apiKey, String serverUrl,
            BatchInfo batch) {
        com.applitools.eyes.images.Eyes eyesImages = configureEyes(runner, appName, baselineName, apiKey, serverUrl,
                batch, true);

        try {
            File imageFile = new File(baseLineFilePath);
            Log.line("image: " + imageFile.getName() + " (exists=" + imageFile.exists() + ")");
            BufferedImage img = ImageIO.read(imageFile);
            if (null == viewportSize) {
                viewportSize = new RectangleSize(img.getWidth(), img.getHeight());
                Log.line("Viewport not provided - using image size: " + img.getWidth() + "x" + img.getHeight());
            }
            Log.field("Test Name", testName);
            Log.field("Viewport", viewportSize.getWidth() + "x" + viewportSize.getHeight());
            eyesImages.open(appName, testName, viewportSize);
            eyesImages.check(imageFile.getName(), Target.image(img));
            TestResults testResults = eyesImages.close(false);
            Log.testResults(testResults);
            return new BaselineUploadResult(testResults, viewportSize);
        } catch (Exception ex) {
            Log.line("FAILED: " + ex);
            ex.printStackTrace();
            return new BaselineUploadResult(null, viewportSize);
        } finally {
            eyesImages.abortIfNotClosed();
        }
    }

    private static com.applitools.eyes.images.Eyes configureEyes(EyesRunner runner, String appName,
            String baselineName, String apiKey, String serverUrl, BatchInfo batch, boolean setHostOS) {
        com.applitools.eyes.images.Eyes eyesImages = new com.applitools.eyes.images.Eyes(runner);
        eyesImages.setBaselineEnvName(baselineName);
        com.applitools.eyes.config.Configuration config = eyesImages.getConfiguration();
        if (setHostOS) {
            config.setHostOS(System.getProperty("os.name"));
        }
        config.setHostApp(appName);
        config.setBaselineEnvName(baselineName);
        if (null != apiKey) {
            config.setApiKey(apiKey);
        }
        if (null != serverUrl) {
            config.setServerUrl(serverUrl);
        }
        if (null != batch) {
            config.setBatch(batch);
        }
        eyesImages.setConfiguration(config);
        // Log every setting that plays into which baseline Eyes matches against -
        // App Name, Batch Name, Host OS/App, and Baseline Env Name together, all in one
        // parallel format with WebCompareRunner.initialiseEyes()'s own log block, so the
        // two are easy to diff when a scenario mysteriously shows up as a new test on
        // one side but not the other.
        Log.field("App Name", appName);
        Log.field("Baseline Env Name", baselineName);
        Log.field("Host OS", String.valueOf(config.getHostOS()));
        Log.field("Host App", String.valueOf(config.getHostApp()));
        Log.field("Batch Name", null != batch ? batch.getName() : "(none)");
        // Diagnostic: confirms saveNewTests actually round-tripped through
        // setConfiguration() and is what Eyes will use at close() - if this prints
        // "true" and a first-ever test for this baselineName still shows Unresolved
        // (not Passed) in the dashboard, the SDK call isn't the problem; check the
        // Applitools account/project's "Auto save new tests" setting instead.
        Log.field("saveNewTests", String.valueOf(eyesImages.getConfiguration().getSaveNewTests()));
        return eyesImages;
    }

    private static void requireCredentials(String apiKey, String serverUrl) {
        if (null == apiKey || apiKey.isBlank()) {
            throw new IllegalStateException("APPLITOOLS_API_KEY is required but was null/blank. "
                    + "Set it in config.properties or as an environment variable.");
        }
        if (null == serverUrl || serverUrl.isBlank()) {
            throw new IllegalStateException("APPLITOOLS_SERVER_URL is required but was null/blank. "
                    + "Set it in config.properties or as an environment variable.");
        }
    }

    public static class ScenarioStep {
        public final String stepName;
        public final String imagePath;

        public ScenarioStep(String stepName, String imagePath) {
            this.stepName = stepName;
            this.imagePath = imagePath;
        }
    }
}

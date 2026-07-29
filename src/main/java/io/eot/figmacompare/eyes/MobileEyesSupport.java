package io.eot.figmacompare.eyes;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.StdoutLogHandler;
import com.applitools.eyes.appium.Eyes;
import com.applitools.eyes.config.MobileOptions;
import com.applitools.eyes.selenium.Configuration;

import io.appium.java_client.AppiumDriver;

/**
 * The Applitools Eyes settings shared by every native-mobile compareWithFigma path -
 * AndroidCompareRunner and IosCompareRunner are otherwise identical here, so this is the
 * one place mobile Eyes configuration lives instead of each runner keeping its own copy.
 */
public class MobileEyesSupport {

    private MobileEyesSupport() {
    }

    public static Eyes open(AppiumDriver driver, BatchInfo batch, String appName, String testName,
            String baselineName, boolean isEyesEnabled) {
        return open(driver, batch, appName, testName, baselineName, isEyesEnabled, false);
    }

    public static Eyes open(AppiumDriver driver, BatchInfo batch, String appName, String testName,
            String baselineName, boolean isEyesEnabled, boolean saveNewTests) {
        Eyes eyes = new Eyes();
        eyes.setLogHandler(new StdoutLogHandler(true));

        Configuration configuration = EyesConfigSupport.baseConfiguration(batch, baselineName);
        configuration.setSaveNewTests(saveNewTests);
        configuration.setBranchName("main");
        configuration.setCaptureStatusBar(true);
        configuration.setDisableBrowserFetching(true);
        configuration.setEnablePatterns(true);
        configuration.setEnvironmentName("prod");
        configuration.setHideCaret(true);
        configuration.setIgnoreCaret(true);
        configuration.setIsDisabled(!isEyesEnabled);
        configuration.setServerUrl("https://eyes.applitools.com");
        configuration.setMobileOptions(MobileOptions.keepNavigationBar(false));
        eyes.setConfiguration(configuration);

        eyes.open(driver, appName, testName);
        return eyes;
    }
}

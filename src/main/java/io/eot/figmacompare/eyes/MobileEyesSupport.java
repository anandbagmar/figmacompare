package io.eot.figmacompare.eyes;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.StdoutLogHandler;
import com.applitools.eyes.appium.Eyes;
import com.applitools.eyes.config.MobileOptions;
import com.applitools.eyes.selenium.Configuration;

import io.appium.java_client.AppiumDriver;
import io.eot.figmacompare.config.AppConfig;

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
        Eyes eyes = new Eyes();
        eyes.setLogHandler(new StdoutLogHandler(true));

        Configuration configuration = EyesConfigSupport.baseConfiguration(batch, baselineName);
        configuration.setBranchName(AppConfig.get("APPLITOOLS_BRANCH_NAME", "main"));
        configuration.setCaptureStatusBar(true);
        configuration.setDisableBrowserFetching(true);
        configuration.setEnablePatterns(true);
        configuration.setEnvironmentName(AppConfig.get("APPLITOOLS_ENVIRONMENT_NAME", "prod"));
        configuration.setHideCaret(true);
        configuration.setIgnoreCaret(true);
        configuration.setIsDisabled(!isEyesEnabled);
        configuration.setMobileOptions(MobileOptions.keepNavigationBar(false));
        eyes.setConfiguration(configuration);

        eyes.open(driver, appName, testName);
        return eyes;
    }
}

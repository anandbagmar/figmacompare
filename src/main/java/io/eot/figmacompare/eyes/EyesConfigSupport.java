package io.eot.figmacompare.eyes;

import com.applitools.eyes.BatchInfo;
import com.applitools.eyes.MatchLevel;
import com.applitools.eyes.selenium.Configuration;
import com.applitools.eyes.selenium.StitchMode;

import io.eot.figmacompare.config.AppConfig;

/**
 * The Applitools Eyes settings shared by every compareWithFigma path - web, Android, and
 * iOS all build on this one {@link Configuration} (the same class backs both
 * com.applitools.eyes.selenium.Eyes and com.applitools.eyes.appium.Eyes) instead of each
 * runner repeating its own copy. Callers layer their platform-specific settings
 * (setAppName, setBranchName, setMobileOptions, addBrowser, etc.) on top of what this
 * returns.
 */
public class EyesConfigSupport {

    private EyesConfigSupport() {
    }

    public static Configuration baseConfiguration(BatchInfo batch, String baselineEnvName) {
        Configuration configuration = new Configuration();
        configuration.setApiKey(AppConfig.requireApplitoolsApiKey());
        configuration.setBatch(batch);
        configuration.setBaselineEnvName(baselineEnvName);
        configuration.setMatchLevel(MatchLevel.STRICT);
        configuration.setSaveNewTests(false);
        configuration.setIgnoreDisplacements(true);
        configuration.setStitchMode(StitchMode.CSS);
        configuration.addProperty("username", System.getProperty("user.name"));
        return configuration;
    }
}

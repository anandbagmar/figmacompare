package io.eot.figmacompare.eyes;

import java.util.Arrays;

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

    private static final String DEFAULT_SERVER_URL = "https://eyes.applitools.com";
    private static final MatchLevel DEFAULT_MATCH_LEVEL = MatchLevel.STRICT;

    private EyesConfigSupport() {
    }

    public static Configuration baseConfiguration(BatchInfo batch, String baselineEnvName) {
        Configuration configuration = new Configuration();
        configuration.setApiKey(AppConfig.requireApplitoolsApiKey());
        configuration.setServerUrl(AppConfig.get("APPLITOOLS_SERVER_URL", DEFAULT_SERVER_URL));
        configuration.setBatch(batch);
        configuration.setBaselineEnvName(baselineEnvName);
        configuration.setMatchLevel(resolveMatchLevel());
        configuration.setIgnoreDisplacements(true);
        configuration.setStitchMode(StitchMode.CSS);
        configuration.addProperty("username", System.getProperty("user.name"));
        return configuration;
    }

    private static MatchLevel resolveMatchLevel() {
        String value = AppConfig.get("APPLITOOLS_MATCH_LEVEL");
        if (null == value) {
            return DEFAULT_MATCH_LEVEL;
        }
        try {
            return MatchLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid APPLITOOLS_MATCH_LEVEL: '" + value + "' - must be one of "
                    + Arrays.toString(MatchLevel.values()), ex);
        }
    }
}

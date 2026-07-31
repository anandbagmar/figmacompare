package io.eot.figmacompare.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {

    /**
     * Where config.properties (and its templates/ subdirectory) live, relative to the
     * consuming project's working directory. Overridable via the figmacompare.configDir
     * system property or FIGMACOMPARE_CONFIG_DIR environment variable (checked in that
     * order) for consumers with a different repo layout - this can't itself be set via
     * config.properties, since it determines where that file is.
     */
    public static final String CONFIG_DIR = resolveConfigDir();
    public static final String TEMPLATES_DIR = CONFIG_DIR + File.separator + "templates";
    public static final String CONFIG_FILE_NAME = "config.properties";

    private static final Properties PROPERTIES = load();

    private AppConfig() {
    }

    private static String resolveConfigDir() {
        String fromSystemProperty = System.getProperty("figmacompare.configDir");
        if (null != fromSystemProperty && !fromSystemProperty.isBlank()) {
            return fromSystemProperty;
        }
        String fromEnv = System.getenv("FIGMACOMPARE_CONFIG_DIR");
        if (null != fromEnv && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return "figma-visual-testing";
    }

    private static Properties load() {
        Properties properties = new Properties();
        File configFile = new File(CONFIG_DIR, CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            System.out.println(configFile.getPath() + " not found. Copy "
                    + new File(TEMPLATES_DIR, CONFIG_FILE_NAME + ".example").getPath() + " to "
                    + configFile.getPath() + " and fill in your tokens. Falling back to environment variables only.");
            return properties;
        }
        try (FileInputStream in = new FileInputStream(configFile)) {
            properties.load(in);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to load " + configFile.getPath(), ex);
        }
        return properties;
    }

    public static String get(String key) {
        String envValue = System.getenv(key);
        if (null != envValue && !envValue.isBlank()) {
            return envValue;
        }
        String propValue = PROPERTIES.getProperty(key);
        return (null != propValue && !propValue.isBlank()) ? propValue : null;
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return null != value ? value : defaultValue;
    }

    /**
     * The Applitools API key, from either the APPLITOOLS_API_KEY environment variable or
     * config.properties (env var wins - see get(String)). Hard-fails with a clear message
     * if neither is set, rather than letting Eyes fail later with a less specific error.
     */
    public static String requireApplitoolsApiKey() {
        String apiKey = get("APPLITOOLS_API_KEY");
        if (null == apiKey) {
            throw new IllegalStateException("APPLITOOLS_API_KEY is not set. Export it as an environment "
                    + "variable, or add APPLITOOLS_API_KEY=... to "
                    + new File(CONFIG_DIR, CONFIG_FILE_NAME).getPath() + " - see README_uploadFromFigma.md § 1.2.");
        }
        return apiKey;
    }

    /**
     * The io.eot:figmacompare version actually on the classpath, read from the jar's own
     * manifest (Implementation-Version - see build.gradle's jar.manifest block) rather
     * than trusted from what a consumer's build.gradle merely asked for, so it reflects
     * what's really loaded (catches a stale mavenLocal() cache, wrong -PfigmacompareVersion,
     * etc). Only populated when running from the built jar - returns "unknown
     * (not running from a packaged jar - e.g. an IDE/test run of figmacompare itself)"
     * otherwise, since a manifest doesn't exist for raw .class files.
     */
    public static String libraryVersion() {
        String version = AppConfig.class.getPackage().getImplementationVersion();
        return null != version ? version : "unknown (not running from a packaged jar)";
    }
}

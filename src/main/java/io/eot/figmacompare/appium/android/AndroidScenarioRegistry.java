package io.eot.figmacompare.appium.android;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.eot.figmacompare.appium.ScenarioFlow;

/**
 * Central lookup of every Android "Scenario Name" -> the app (APK) and ScenarioFlow that
 * implement it, regardless of which class registered it or which module that class lives
 * in. A consumer's own runner looks a scenario up purely by name - this registry doesn't
 * know or care where it was registered from, so a scenario provider can live in this
 * project's samples module, or in a completely separate consuming project/repo.
 */
public class AndroidScenarioRegistry {

    private static final Map<String, Registration> SCENARIOS = new LinkedHashMap<>();

    private AndroidScenarioRegistry() {
    }

    public static void register(String scenarioName, String apkPath, String appName, ScenarioFlow flow) {
        if (SCENARIOS.containsKey(scenarioName)) {
            throw new IllegalStateException("Scenario \"" + scenarioName + "\" is already registered - "
                    + "check for a duplicate registration across test classes.");
        }
        SCENARIOS.put(scenarioName, new Registration(apkPath, appName, flow));
    }

    public static Registration get(String scenarioName) {
        return SCENARIOS.get(scenarioName);
    }

    public static Set<String> registeredScenarioNames() {
        return SCENARIOS.keySet();
    }

    /**
     * Loads (and so triggers the static initializer / registrations of) a provider class
     * by name. core deliberately does NOT maintain a list of provider classes to load -
     * it can't know about classes that live in a consumer's own module. Each consumer
     * owns its own bootstrap that calls this once per provider class it wants active
     * before a test run looks scenarios up - see CompareAndroidWithFigmaTest in the
     * samples module for the pattern.
     */
    public static void loadProviderClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Could not load Android scenario provider class: " + className, ex);
        }
    }

    public static class Registration {
        public final String apkPath;
        public final String appName;
        public final ScenarioFlow flow;

        public Registration(String apkPath, String appName, ScenarioFlow flow) {
            this.apkPath = apkPath;
            this.appName = appName;
            this.flow = flow;
        }
    }
}

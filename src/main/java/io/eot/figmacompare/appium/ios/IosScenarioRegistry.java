package io.eot.figmacompare.appium.ios;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.eot.figmacompare.appium.ScenarioFlow;

/**
 * Central lookup of every iOS "Scenario Name" -> the app (.app bundle) and ScenarioFlow
 * that implement it, regardless of which class registered it or which module that class
 * lives in. Mirrors AndroidScenarioRegistry - see its class comment for the full
 * rationale.
 */
public class IosScenarioRegistry {

    private static final Map<String, Registration> SCENARIOS = new LinkedHashMap<>();

    private IosScenarioRegistry() {
    }

    public static void register(String scenarioName, String appPath, String appName, ScenarioFlow flow) {
        if (SCENARIOS.containsKey(scenarioName)) {
            throw new IllegalStateException("Scenario \"" + scenarioName + "\" is already registered - "
                    + "check for a duplicate registration across test classes.");
        }
        SCENARIOS.put(scenarioName, new Registration(appPath, appName, flow));
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
     * see AndroidScenarioRegistry.loadProviderClass() for the full rationale. Each
     * consumer owns its own bootstrap that calls this once per provider class it wants
     * active before a test run looks scenarios up.
     */
    public static void loadProviderClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Could not load iOS scenario provider class: " + className, ex);
        }
    }

    public static class Registration {
        public final String appPath;
        public final String appName;
        public final ScenarioFlow flow;

        public Registration(String appPath, String appName, ScenarioFlow flow) {
            this.appPath = appPath;
            this.appName = appName;
            this.flow = flow;
        }
    }
}

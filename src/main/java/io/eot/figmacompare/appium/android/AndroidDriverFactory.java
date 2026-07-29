package io.eot.figmacompare.appium.android;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

public class AndroidDriverFactory {

    private AndroidDriverFactory() {
    }

    public static AndroidDriver create(String appiumServerUrl, String apkPath, boolean fullReset) {
        requireAndroidSdk();

        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setDeviceName("Android");
        options.setPrintPageSourceOnFindFailure(true);
        options.setAutoGrantPermissions(true);
        options.setFullReset(fullReset);
        options.setApp(new File(apkPath).getAbsolutePath());
        // Accept any foreground activity as proof the app launched, instead of requiring
        // the manifest-declared launcher activity by name. Some apps transition off their
        // splash/launcher activity faster than Appium's own polling samples it, which
        // otherwise fails session creation with "<LauncherActivity> never started" even
        // though the app did in fact launch.
        options.setAppWaitActivity("*");

        System.out.println("UiAutomator2Options:");
        for (String capabilityName : options.getCapabilityNames()) {
            System.out.println("\t" + capabilityName + ": " + options.getCapability(capabilityName));
        }

        try {
            AndroidDriver driver = new AndroidDriver(new URL(appiumServerUrl), options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1L));
            return driver;
        } catch (MalformedURLException ex) {
            throw new RuntimeException(
                    "Error creating Appium driver for Android device with capabilities: " + options, ex);
        }
    }

    private static void requireAndroidSdk() {
        String androidHome = System.getenv("ANDROID_HOME");
        String androidSdkRoot = System.getenv("ANDROID_SDK_ROOT");
        boolean isBlank = (null == androidHome || androidHome.isBlank())
                && (null == androidSdkRoot || androidSdkRoot.isBlank());
        if (isBlank) {
            throw new IllegalStateException("Neither ANDROID_HOME nor ANDROID_SDK_ROOT is set. Export one of "
                    + "them to your local Android SDK path (e.g. export ANDROID_HOME=~/Library/Android/sdk) "
                    + "before running Android tests - the Appium uiautomator2 driver needs it to find adb.");
        }
    }
}

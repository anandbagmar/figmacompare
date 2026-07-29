package io.eot.figmacompare.appium.ios;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

public class IosDriverFactory {

    private IosDriverFactory() {
    }

    public static IOSDriver create(String appiumServerUrl, String appPath, String udid, String deviceName,
            String platformVersion, boolean fullReset) {
        XCUITestOptions options = new XCUITestOptions();
        options.setPlatformName("iOS");
        options.setAutomationName("XCUITest");
        options.setPlatformVersion(platformVersion);
        options.setDeviceName(deviceName);
        options.setUdid(udid);
        options.setFullReset(fullReset);
        options.setShowXcodeLog(false);
        options.setCapability("appium:showIOSLog", false);
        options.setPrintPageSourceOnFindFailure(true);
        options.setAutoAcceptAlerts(true);
        options.setApp(new File(appPath).getAbsolutePath());

        System.out.println("XCUITestOptions:");
        for (String capabilityName : options.getCapabilityNames()) {
            System.out.println("\t" + capabilityName + ": " + options.getCapability(capabilityName));
        }

        try {
            IOSDriver driver = new IOSDriver(new URL(appiumServerUrl), options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1L));
            return driver;
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Error creating Appium driver for iOS device with capabilities: " + options,
                    ex);
        }
    }
}

package io.eot.figmacompare.appium;

import java.io.File;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import io.eot.figmacompare.config.AppConfig;

public class AppiumServerSupport {

    private static final String DEFAULT_APPIUM_JS_PATH = "./node_modules/appium/build/lib/main.js";

    private AppiumServerSupport() {
    }

    public static String defaultLogFileDir() {
        String logDir = System.getenv("LOG_DIR");
        return null == logDir ? "appium-server.log" : logDir + "/appium_logs.txt";
    }

    public static AppiumDriverLocalService start(String logFileDir) {
        System.out.println("Start local Appium server");
        AppiumServiceBuilder serviceBuilder = new AppiumServiceBuilder();
        // Use any port, in case the default 4723 is already taken (maybe by another
        // Appium server)
        serviceBuilder.usingAnyFreePort();
        serviceBuilder.withAppiumJS(new File(AppConfig.get("APPIUM_JS_PATH", DEFAULT_APPIUM_JS_PATH)));
        serviceBuilder.withLogFile(new File(logFileDir));
        serviceBuilder.withArgument(GeneralServerFlag.ALLOW_INSECURE, "adb_shell");
        serviceBuilder.withArgument(GeneralServerFlag.RELAXED_SECURITY);

        AppiumDriverLocalService service = AppiumDriverLocalService.buildService(serviceBuilder);
        service.start();
        System.out.printf("Appium server started on url: '%s'%n", service.getUrl());
        return service;
    }

    public static void stop(AppiumDriverLocalService service) {
        if (null != service) {
            service.stop();
            System.out.printf("Is Appium server running? %s%n", service.isRunning());
        }
    }
}

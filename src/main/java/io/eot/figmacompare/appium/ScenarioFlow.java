package io.eot.figmacompare.appium;

import java.util.List;

import com.applitools.eyes.appium.Eyes;

import io.appium.java_client.AppiumDriver;
import io.eot.figmacompare.excel.FigmaRow;

/**
 * A fully bespoke, self-contained mobile test procedure for one Scenario Name - reaching
 * even a single mobile screen can require login/navigation specific to that screen, so
 * (unlike web) there's no generic "just navigate there" runner for mobile. Implementations
 * do whatever driver interactions are needed and call eyes.checkWindow(...) themselves,
 * once per step they want recorded - they own the whole sequence, not just one screen.
 */
@FunctionalInterface
public interface ScenarioFlow {

    /**
     * @param driver the already-launched Appium driver for this test
     * @param eyes   already open (eyes.open was already called); call checkWindow(...) for
     *               each step, do not call eyes.close() - the caller does that once, after
     *               this method returns
     * @param rows   every FigmaRow belonging to this scenario, in sheet order
     */
    void run(AppiumDriver driver, Eyes eyes, List<FigmaRow> rows);
}

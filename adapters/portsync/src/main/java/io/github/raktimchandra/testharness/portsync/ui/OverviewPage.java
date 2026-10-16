package io.github.raktimchandra.testharness.portsync.ui;

import io.github.raktimchandra.testharness.ui.page.BasePage;
import io.github.raktimchandra.testharness.ui.wait.Waits;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** The terminal overview: the KPI band, quay, yard blocks, active alerts. */
public final class OverviewPage extends BasePage<OverviewPage> {

    OverviewPage(WebDriver driver, Waits waits, String baseUrl) {
        super(driver, waits, baseUrl);
    }

    @Override
    protected By readyLocator() {
        return By.cssSelector("section.kpis[aria-label='Key figures'] .kpi-value");
    }

    @Override
    protected String path() {
        return "/";
    }

    /** The headline figure above a KPI label, e.g. "Open alerts" → "2". */
    public String kpi(String label) {
        return text(By.xpath("//section[@aria-label='Key figures']//div[contains(@class,'kpi')]"
                + "[.//div[@class='kpi-label' and normalize-space(.)='" + label + "']]//div[@class='kpi-value']"));
    }

    public String heading() {
        return text(By.tagName("h1"));
    }
}

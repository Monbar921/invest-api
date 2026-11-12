package ru.invest.api.feature.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.account.service.config.PlanProperties;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ошибка в настройке тарифов не даёт приложению стартовать (`@Validated`). ApplicationContextRunner, без Testcontainers.
 */
class PlanPropertiesTest {
    private static final String PREFIX = "ru.invest.api.plans.";
    private static final String FREE = PREFIX + "plans.free.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void validPlansStart() {
        runner.withPropertyValues(valid())
                .withPropertyValues(PREFIX + "plans.pro-1.title=Про", PREFIX + "plans.pro-1.paid=true",
                        PREFIX + "plans.pro-1.limits.ALERTS=0", PREFIX + "plans.pro-1.limits.PORTFOLIO_POSITIONS=10",
                        PREFIX + "plans.pro-1.features=CALENDAR_ICS")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final PlanProperties properties = context.getBean(PlanProperties.class);
                    assertThat(properties.getZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
                    assertThat(properties.getPlans()).containsOnlyKeys("free", "pro-1");
                    assertThat(properties.getPlans().get("free").getFeatures()).isEmpty();
                    assertThat(properties.getPlans().get("free").isPaid()).isFalse();
                    assertThat(properties.getPlans().get("pro-1").getFeatures()).containsExactly(PlanFeature.CALENDAR_ICS);
                    assertThat(properties.getPlans().get("pro-1").getLimits()).containsEntry(PlanLimit.ALERTS, 0);
                });
    }

    @Test
    void unknownDefaultPlanFailsStartup() {
        assertFails(PREFIX + "default-plan=premium");
        assertFails(PREFIX + "default-plan=");
    }

    @Test
    void noPlansFailStartup() {
        runner.withPropertyValues(PREFIX + "default-plan=free").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingOrNegativeLimitFailsStartup() {
        runner.withPropertyValues(PREFIX + "default-plan=free", FREE + "title=Бесплатный", FREE + "limits.ALERTS=50")
                .run(context -> assertThat(context).hasFailed());
        assertFails(FREE + "limits.ALERTS=-1");
    }

    @Test
    void badPlanIdOrTitleFailsStartup() {
        runner.withPropertyValues(valid())
                .withPropertyValues(PREFIX + "plans.Pro.title=Про", PREFIX + "plans.Pro.limits.ALERTS=1",
                        PREFIX + "plans.Pro.limits.PORTFOLIO_POSITIONS=1")
                .run(context -> assertThat(context).hasFailed());
        assertFails(FREE + "title= ");
    }

    @Test
    void unknownLimitOrFeatureFailsStartup() {
        assertFails(FREE + "limits.SAVED_FILTERS=1");
        assertFails(FREE + "features=AI_SUMMARY");
    }

    private void assertFails(final String override) {
        runner.withPropertyValues(valid())
                .withPropertyValues(override)
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] valid() {
        return new String[]{
            PREFIX + "default-plan=free",
            FREE + "title=Бесплатный",
            FREE + "limits.ALERTS=50",
            FREE + "limits.PORTFOLIO_POSITIONS=200"
        };
    }

    @Configuration
    @EnableConfigurationProperties(PlanProperties.class)
    static class PropertiesConfiguration {
    }
}

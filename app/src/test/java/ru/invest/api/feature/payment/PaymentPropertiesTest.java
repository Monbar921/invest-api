package ru.invest.api.feature.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.account.service.model.PeriodTexts;
import ru.invest.api.payment.service.config.PaymentProperties;

import java.time.Duration;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Настройка оплаты при старте (ApplicationContextRunner, без Testcontainers) и срок тарифа по-русски.
 */
class PaymentPropertiesTest {
    private static final String PREFIX = "ru.invest.api.payments.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void disabledPaymentsStartWithoutSettings() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            final PaymentProperties properties = context.getBean(PaymentProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getProvider()).isEqualTo("tbank");
            assertThat(properties.getRenewal().getChargeBefore()).isEqualTo(Duration.ofDays(1));
            assertThat(properties.getRenewal().getMaxAttempts()).isEqualTo(3);
        });
    }

    @Test
    void enabledPaymentsStartWithAllSettings() {
        runner.withPropertyValues(enabled()).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void enabledPaymentsWithoutUrlsOrKeysFailStartup() {
        runner.withPropertyValues(enabled()).withPropertyValues(PREFIX + "offer-url=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(enabled()).withPropertyValues(PREFIX + "notification-url= ")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(enabled()).withPropertyValues(PREFIX + "tbank.password=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(enabled()).withPropertyValues(PREFIX + "tbank.terminal-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownProviderFailsStartup() {
        runner.withPropertyValues(PREFIX + "provider=yookassa").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void periodIsWrittenInRussian() {
        assertThat(PeriodTexts.format(Period.ofMonths(1))).isEqualTo("1 месяц");
        assertThat(PeriodTexts.format(Period.ofMonths(3))).isEqualTo("3 месяца");
        assertThat(PeriodTexts.format(Period.ofMonths(6))).isEqualTo("6 месяцев");
        assertThat(PeriodTexts.format(Period.ofYears(1))).isEqualTo("1 год");
        assertThat(PeriodTexts.format(Period.of(2, 1, 0))).isEqualTo("2 года 1 месяц");
        assertThat(PeriodTexts.format(Period.ofDays(11))).isEqualTo("11 дней");
        assertThat(PeriodTexts.format(Period.ofDays(21))).isEqualTo("21 день");
        assertThat(PeriodTexts.format(Period.ofYears(5))).isEqualTo("5 лет");
        assertThat(PeriodTexts.format(null)).isNull();
    }

    private static String[] enabled() {
        return new String[]{
                PREFIX + "enabled=true",
                PREFIX + "notification-url=https://invest.test/api/payments/notifications/tbank",
                PREFIX + "success-url=https://invest.test/plan?payment={paymentId}",
                PREFIX + "fail-url=https://invest.test/plan?payment={paymentId}",
                PREFIX + "offer-url=https://invest.test/offer",
                PREFIX + "privacy-url=https://invest.test/privacy",
                PREFIX + "tbank.terminal-key=TestDEMO",
                PREFIX + "tbank.password=secret"};
    }

    @Configuration
    @EnableConfigurationProperties(PaymentProperties.class)
    static class PropertiesConfiguration {
    }
}

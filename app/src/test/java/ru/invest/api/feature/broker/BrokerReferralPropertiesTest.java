package ru.invest.api.feature.broker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import ru.invest.api.broker.service.config.BrokerReferralProperties;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ошибка в настройке партнёра не даёт приложению стартовать (`@Validated`). ApplicationContextRunner, без Testcontainers.
 */
class BrokerReferralPropertiesTest {
    private static final String PARTNER = "ru.invest.api.broker-referral.partners[0].";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void validPartnerStarts() {
        runner.withPropertyValues(valid())
                .withPropertyValues("ru.invest.api.broker-referral.zone=UTC")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    final BrokerReferralProperties properties = context.getBean(BrokerReferralProperties.class);
                    assertThat(properties.getZone()).isEqualTo(ZoneId.of("UTC"));
                    assertThat(properties.getPartners()).singleElement()
                            .satisfies(partner -> {
                                assertThat(partner.getId()).isEqualTo("t-bank-2");
                                assertThat(partner.isEnabled()).isTrue();
                                assertThat(partner.getErid()).isNull();
                            });
                });
    }

    @Test
    void noPartnersStartsWithMoscowZone() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            final BrokerReferralProperties properties = context.getBean(BrokerReferralProperties.class);
            assertThat(properties.getPartners()).isEmpty();
            assertThat(properties.getZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
        });
    }

    @Test
    void badIdFailsStartup() {
        assertFails(PARTNER + "id=T-Bank");
        assertFails(PARTNER + "id=t_bank");
        assertFails(PARTNER + "id=" + "a".repeat(65));
        assertFails(PARTNER + "id=");
    }

    @Test
    void nonHttpsTemplateFailsStartup() {
        assertFails(PARTNER + "url-template=http://broker.test/{ticker}");
        assertFails(PARTNER + "url-template=https://broker.test/a b");
        assertFails(PARTNER + "url-template=");
    }

    @Test
    void blankAdvertiserOrNameFailsStartup() {
        assertFails(PARTNER + "advertiser= ");
        assertFails(PARTNER + "name=");
    }

    @Test
    void badZoneFailsStartup() {
        assertFails("ru.invest.api.broker-referral.zone=Mars/Olympus");
    }

    private void assertFails(final String override) {
        runner.withPropertyValues(valid())
                .withPropertyValues(override)
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] valid() {
        return new String[] {
            PARTNER + "id=t-bank-2",
            PARTNER + "name=Т-Банк",
            PARTNER + "url-template=https://tbank-online.com/baf/XXXX",
            PARTNER + "advertiser=АО \"ТБанк\", ИНН 7710140679",
        };
    }

    @Configuration
    @EnableConfigurationProperties(BrokerReferralProperties.class)
    static class PropertiesConfiguration {
    }
}

package ru.invest.api.feature.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.telegram.bot.status.TelegramAvailability;
import ru.invest.api.telegram.bot.status.TelegramAvailabilityChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Когда запускаются long polling и проверка связи. TelegramBotRunner стоит на том же условии, что и
 * TelegramAvailabilityChecker; сам runner здесь не поднимается - он начал бы long polling.
 */
class TelegramConditionsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TelegramClient.class, () -> mock(TelegramClient.class))
            .withBean(TelegramAvailability.class)
            .withUserConfiguration(TelegramAvailabilityChecker.class);

    @Test
    void enabledWithoutPollingSettingStartsBot() {
        runner.withPropertyValues("ru.invest.api.telegram.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TelegramAvailabilityChecker.class));
    }

    @Test
    void pollingFalseKeepsBotIdle() {
        runner.withPropertyValues("ru.invest.api.telegram.enabled=true", "ru.invest.api.telegram.polling=false")
                .run(context -> assertThat(context).doesNotHaveBean(TelegramAvailabilityChecker.class));
    }

    @Test
    void disabledBotDoesNotStart() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TelegramAvailabilityChecker.class));
    }
}

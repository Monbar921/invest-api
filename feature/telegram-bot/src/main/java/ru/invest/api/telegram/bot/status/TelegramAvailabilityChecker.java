package ru.invest.api.telegram.bot.status;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Проверка связи с Telegram (getMe) для статуса в UI: блокировка, не запущен прокси, неверный токен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// polling не задан - true: бот запускается при одном enabled
@ConditionalOnExpression("${ru.invest.api.telegram.enabled:false} and ${ru.invest.api.telegram.polling:true}")
public class TelegramAvailabilityChecker {
    private final TelegramClient telegramClient;
    private final TelegramAvailability availability;

    @Scheduled(initialDelay = 0, fixedDelayString = "${ru.invest.api.telegram.availability-check-interval:PT5M}")
    public void check() {
        try {
            telegramClient.execute(GetMe.builder().build());
            availability.success();
        } catch (final TelegramApiException e) {
            // без стека: при блокировке это повторяется при каждой проверке
            log.warn("Telegram is unavailable: {}", e.getMessage());
            availability.failure();
        }
    }
}

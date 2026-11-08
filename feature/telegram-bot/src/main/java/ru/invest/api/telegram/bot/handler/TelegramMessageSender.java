package ru.invest.api.telegram.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.telegram.bot.status.TelegramAvailability;

/**
 * Отправка текста в чат. Результат обновляет статус связи с Telegram.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = "enabled", havingValue = "true")
public class TelegramMessageSender {
    private final TelegramClient telegramClient;
    private final TelegramAvailability availability;

    /**
     * Простой текст, без разметки: ничего не нужно экранировать.
     *
     * @return true - отправлено
     */
    public boolean send(final Long chatId, final String text) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
            availability.success();
            return true;
        } catch (final TelegramApiException e) {
            log.warn("Telegram message was not sent: {}", e.getMessage());
            availability.failure();
            return false;
        }
    }

    /**
     * Ответ на нажатие кнопки: без него у кнопки в клиенте крутится индикатор.
     *
     * @param text всплывающая подсказка; null - без подсказки
     */
    public void answerCallback(final String callbackQueryId, final String text) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .build());
            availability.success();
        } catch (final TelegramApiException e) {
            log.warn("Telegram callback was not answered: {}", e.getMessage());
            availability.failure();
        }
    }
}

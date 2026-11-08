package ru.invest.api.telegram.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.telegram.bot.config.TelegramProperties;

import java.util.List;

/**
 * Long polling: webhook не подходит - у ПК нет публичного HTTPS-адреса. Запускается после старта контекста и
 * останавливается до закрытия пула БД. Должен работать в одном экземпляре приложения: Telegram отдаёт обновления
 * только одному getUpdates.
 * <p>
 * Пока Telegram недоступен, библиотека сама повторяет getUpdates с растущей паузой; запуск приложения это не ломает.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = {"enabled", "polling"}, havingValue = "true")
public class TelegramBotRunner implements SmartLifecycle {
    private final TelegramProperties properties;
    private final OkHttpClient telegramOkHttpClient;
    private final TelegramClient telegramClient;
    private final TelegramUpdateHandler updateHandler;

    private TelegramBotsLongPollingApplication application;

    @Override
    public synchronized void start() {
        try {
            application = new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> telegramOkHttpClient);
            application.registerBot(properties.getBotToken(), updateHandler);
            if (!application.isRunning()) {
                application.start();
            }
            log.info("Telegram bot @{} long polling started", properties.getBotUsername());
        } catch (final TelegramApiException e) {
            log.error("Telegram bot was not started: {}", e.getMessage());
            return;
        }
        registerCommands();
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public synchronized void stop() {
        if (application == null) {
            return;
        }
        try {
            application.close();
        } catch (final Exception e) {
            log.warn("Telegram bot stop failed: {}", e.getMessage());
        }
        application = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return application != null;
    }

    // меню команд в клиенте Telegram; при недоступном Telegram просто не обновится
    private void registerCommands() {
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .commands(List.of(
                            new BotCommand("start", "подключение по ссылке из приложения"),
                            new BotCommand("stop", "отключить уведомления"),
                            new BotCommand("help", "подсказка")))
                    .build());
        } catch (final TelegramApiException e) {
            log.warn("Telegram bot commands were not registered: {}", e.getMessage());
        }
    }
}

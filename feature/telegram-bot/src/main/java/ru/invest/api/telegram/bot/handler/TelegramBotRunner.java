package ru.invest.api.telegram.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.telegram.bot.config.TelegramProperties;

import java.time.Duration;
import java.util.List;

/**
 * Long polling: webhook не подходит - у ПК нет публичного HTTPS-адреса. Должен работать в одном экземпляре
 * приложения: Telegram отдаёт обновления только одному getUpdates.
 * <p>
 * Регистрация бота (deleteWebhook) - сетевой запрос, поэтому идёт в фоновом потоке: недоступный Telegram не держит
 * старт приложения, а неудачная попытка повторяется с растущей паузой, пока не получится. После регистрации
 * getUpdates при сбоях повторяет уже сама библиотека.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// polling не задан - true: бот запускается при одном enabled
@ConditionalOnExpression("${ru.invest.api.telegram.enabled:false} and ${ru.invest.api.telegram.polling:true}")
public class TelegramBotRunner implements SmartLifecycle {
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(10);

    private final TelegramProperties properties;
    private final OkHttpClient telegramOkHttpClient;
    private final TelegramClient telegramClient;
    private final TelegramUpdateHandler updateHandler;

    private Thread starter;
    private TelegramBotsLongPollingApplication application;
    private volatile boolean running;

    @Override
    public synchronized void start() {
        running = true;
        starter = Thread.ofVirtual().name("telegram-bot-starter").start(this::registerUntilSuccess);
    }

    @Override
    public void stop() {
        final Thread thread;
        synchronized (this) {
            running = false;
            thread = starter;
            starter = null;
        }
        if (thread != null) {
            thread.interrupt();
        }
        closeApplication();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void registerUntilSuccess() {
        Duration delay = FIRST_RETRY_DELAY;
        while (running) {
            if (tryRegister()) {
                registerCommands();
                return;
            }
            log.warn("Telegram bot registration will be retried in {}", delay);
            try {
                Thread.sleep(delay);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            final Duration doubled = delay.multipliedBy(2);
            delay = doubled.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : doubled;
        }
    }

    private synchronized boolean tryRegister() {
        if (!running) {
            return true;
        }
        try {
            application = new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> telegramOkHttpClient);
            application.registerBot(properties.getBotToken(), updateHandler);
            if (!application.isRunning()) {
                application.start();
            }
            log.info("Telegram bot @{} long polling started", properties.getBotUsername());
            return true;
        } catch (final TelegramApiException e) {
            log.warn("Telegram bot was not started: {}", e.getMessage());
            closeApplication();
            return false;
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private synchronized void closeApplication() {
        if (application == null) {
            return;
        }
        try {
            // close() объявляет Exception: останавливает сессии и пулы потоков библиотеки
            application.close();
        } catch (final Exception e) {
            log.warn("Telegram bot stop failed: {}", e.getMessage());
        }
        application = null;
    }

    // меню команд в клиенте Telegram; при недоступном Telegram просто не обновится
    private void registerCommands() {
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .commands(List.of(
                            new BotCommand("start", "подключение по ссылке из приложения"),
                            new BotCommand("list", "мои уведомления"),
                            new BotCommand("stop", "отключить Telegram"),
                            new BotCommand("help", "подсказка")))
                    .build());
        } catch (final TelegramApiException e) {
            log.warn("Telegram bot commands were not registered: {}", e.getMessage());
        }
    }
}

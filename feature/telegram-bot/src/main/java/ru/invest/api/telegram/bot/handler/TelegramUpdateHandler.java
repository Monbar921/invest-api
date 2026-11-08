package ru.invest.api.telegram.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.invest.api.telegram.bot.model.LinkResult;
import ru.invest.api.telegram.bot.usecase.TelegramLinkUseCase;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Команды бота. Обрабатываются только личные чаты: группы и каналы игнорируются.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = "enabled", havingValue = "true")
public class TelegramUpdateHandler implements LongPollingSingleThreadUpdateConsumer {
    static final String LINKED = """
            Готово: Telegram подключён. Сюда будут приходить уведомления по облигациям.
            Отключить - /stop или кнопкой в приложении.""";
    static final String INVALID_TOKEN = """
            Ссылка недействительна: она одноразовая и действует 10 минут.
            Получите новую в приложении: «Подключить Telegram».""";
    static final String START_WITHOUT_TOKEN = """
            Это бот уведомлений по облигациям.
            Чтобы подключить его, нажмите «Подключить Telegram» в приложении и откройте ссылку
            или отправьте сюда код подключения из приложения.""";
    static final String UNLINKED = "Telegram отключён, уведомления больше не придут. Подключить снова - из приложения.";
    static final String NOT_LINKED = "Этот чат не подключён к приложению.";
    static final String HELP = """
            /start - подключение (по ссылке или коду из приложения)
            /stop - отключить уведомления
            /help - эта подсказка""";

    // токен ссылки привязки: 32 байта в base64url. Telegram Desktop иногда теряет параметр start у ссылки - тогда
    // пользователь отправляет код из приложения обычным сообщением
    private static final Pattern LINK_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final TelegramLinkUseCase linkUseCase;
    private final TelegramMessageSender sender;

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void consume(final Update update) {
        if (!update.hasMessage()) {
            return;
        }
        final Message message = update.getMessage();
        if (!message.isUserMessage() || message.getText() == null) {
            return;
        }
        try {
            handle(message);
        } catch (final RuntimeException e) {
            // ошибка одного сообщения не должна останавливать обработку остальных
            log.error("Telegram update handling failed", e);
        }
    }

    private void handle(final Message message) {
        final Long chatId = message.getChatId();
        final String[] parts = message.getText().strip().split("\\s+", 2);
        // в группах команда приходит как /start@bot_name; в личном чате суффикс тоже допустим
        final String command = parts[0].toLowerCase(Locale.ROOT).replaceFirst("@.*$", "");
        final String argument = parts.length > 1 ? parts[1].strip() : "";
        // сам токен в лог не пишем
        log.info("Telegram message: command={}, argument={}", command.startsWith("/") ? command : "<text>",
                argument.isEmpty() ? "none" : "present");

        if (parts.length == 1 && LINK_TOKEN.matcher(parts[0]).matches()) {
            sender.send(chatId, link(message, parts[0]));
            return;
        }
        switch (command) {
            case "/start" -> sender.send(chatId, argument.isEmpty() ? START_WITHOUT_TOKEN : link(message, argument));
            case "/stop" -> sender.send(chatId, linkUseCase.unlinkByChat(chatId) ? UNLINKED : NOT_LINKED);
            default -> sender.send(chatId, HELP);
        }
    }

    private String link(final Message message, final String token) {
        final String username = message.getFrom() == null ? null : message.getFrom().getUserName();
        return linkUseCase.link(token, message.getChatId(), username) == LinkResult.LINKED ? LINKED : INVALID_TOKEN;
    }
}

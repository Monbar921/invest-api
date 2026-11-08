package ru.invest.api.telegram.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.alert.service.sender.DeliveryResult;
import ru.invest.api.alert.service.sender.NotificationSender;
import ru.invest.api.alert.service.sender.OutgoingNotification;
import ru.invest.api.common.entity.TelegramLink;
import ru.invest.api.common.repository.TelegramLinkRepository;
import ru.invest.api.telegram.bot.status.TelegramAvailability;
import ru.invest.api.telegram.bot.usecase.TelegramLinkUseCase;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Доставка уведомлений в Telegram. У уведомления от правила - кнопка «Отключить это уведомление».
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = "enabled", havingValue = "true")
public class TelegramNotificationSender implements NotificationSender {
    static final String DISABLE_ALERT_PREFIX = "alert-off:";
    static final String DISABLE_ALERT_BUTTON = "Отключить это уведомление";
    private static final int FORBIDDEN = 403;
    private static final int BAD_REQUEST = 400;
    private static final int TOO_MANY_REQUESTS = 429;

    private final TelegramClient telegramClient;
    private final TelegramLinkRepository linkRepository;
    private final TelegramLinkUseCase linkUseCase;
    private final TelegramAvailability availability;

    @Override
    public DeliveryResult send(final OutgoingNotification notification) {
        final Optional<TelegramLink> link = linkRepository.findById(notification.userId());
        if (link.isEmpty()) {
            return DeliveryResult.failed("Telegram не подключён");
        }
        final SendMessage.SendMessageBuilder<?, ?> message = SendMessage.builder()
                .chatId(link.get().getChatId())
                .text(notification.text());
        if (notification.alertId() != null) {
            message.replyMarkup(new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(DISABLE_ALERT_BUTTON)
                    .callbackData(DISABLE_ALERT_PREFIX + notification.alertId())
                    .build()))));
        }
        try {
            telegramClient.execute(message.build());
            availability.success();
            return DeliveryResult.sent();
        } catch (final TelegramApiRequestException e) {
            return requestFailed(notification, e);
        } catch (final TelegramApiException e) {
            // сеть, прокси, блокировка: повторим позже
            availability.failure();
            return DeliveryResult.retry(e.getMessage(), null);
        }
    }

    private DeliveryResult requestFailed(final OutgoingNotification notification, final TelegramApiRequestException e) {
        final Integer code = e.getErrorCode();
        if (code == null) {
            availability.failure();
            return DeliveryResult.retry(e.getMessage(), null);
        }
        // Telegram ответил - связь есть
        availability.success();
        if (code == FORBIDDEN) {
            // пользователь заблокировал бота: чат больше не наш
            linkUseCase.unlinkByUser(notification.userId());
            log.info("Telegram chat unlinked: bot is blocked by user {}", notification.userId());
            return DeliveryResult.failed("Бот заблокирован в Telegram - Telegram отключён");
        }
        if (code == TOO_MANY_REQUESTS) {
            final Integer retryAfter = e.getParameters() == null ? null : e.getParameters().getRetryAfter();
            return DeliveryResult.retry(e.getMessage(), retryAfter == null ? null : Duration.ofSeconds(retryAfter));
        }
        if (code == BAD_REQUEST) {
            return DeliveryResult.failed(e.getMessage());
        }
        return DeliveryResult.retry(e.getMessage(), null);
    }
}

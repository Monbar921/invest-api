package ru.invest.api.telegram.bot.handler;

import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.common.model.alert.AlertType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Тексты бота, которые собираются из данных.
 */
final class TelegramTexts {
    private TelegramTexts() {
    }

    /**
     * /list: правила по облигациям, выключенные - с пометкой.
     */
    static String alerts(final List<AlertModel> alerts) {
        if (alerts.isEmpty()) {
            return TelegramUpdateHandler.NO_ALERTS;
        }
        return "Ваши уведомления:\n" + alerts.stream()
                .map(alert -> "• %s - %s%s".formatted(bond(alert), rule(alert), alert.enabled() ? "" : " (выключено)"))
                .collect(Collectors.joining("\n"))
                + "\n\nИзменить - колокольчиком у облигации в приложении.";
    }

    static String rule(final AlertModel alert) {
        return "%s за %d дн.".formatted(label(alert.type()), alert.days());
    }

    private static String label(final AlertType type) {
        return switch (type) {
            case COUPON_BEFORE -> "купон";
            case AMORTIZATION_BEFORE -> "частичное погашение";
            case OFFER_BEFORE -> "оферта";
            case MATURITY_BEFORE -> "погашение";
        };
    }

    private static String bond(final AlertModel alert) {
        return alert.bondName() == null ? alert.ticker() : "%s (%s)".formatted(alert.bondName(), alert.ticker());
    }
}

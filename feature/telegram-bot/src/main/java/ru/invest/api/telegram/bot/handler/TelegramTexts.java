package ru.invest.api.telegram.bot.handler;

import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.common.model.alert.AlertDirection;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
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
        final String threshold = alert.threshold() == null ? "" : number(alert.threshold());
        final String level = alert.direction() == AlertDirection.UP ? "\u2265 " : "\u2264 ";
        final String change = switch (alert.direction() == null ? AlertDirection.ANY : alert.direction()) {
            case UP -> "+";
            case DOWN -> "\u2212";
            case ANY -> "\u00b1";
        };
        return switch (alert.type()) {
            case COUPON_BEFORE -> "купон за %d дн.".formatted(alert.days());
            case AMORTIZATION_BEFORE -> "частичное погашение за %d дн.".formatted(alert.days());
            case OFFER_BEFORE -> "оферта за %d дн.".formatted(alert.days());
            case MATURITY_BEFORE -> "погашение за %d дн.".formatted(alert.days());
            case PRICE_LEVEL -> "цена %s%s%% от номинала".formatted(level, threshold);
            case PRICE_CHANGE -> "цена %s%s%% от %s".formatted(change, threshold,
                    alert.baseValue() == null ? "цены при создании" : number(alert.baseValue()) + "%");
            case PRICE_DAILY_CHANGE -> "цена за день %s%s%%".formatted(change, threshold);
            case YTM_LEVEL -> "доходность к погашению %s%s%%".formatted(level, threshold);
            case YTO_LEVEL -> "доходность к оферте %s%s%%".formatted(level, threshold);
            case FLOATER_COUPON -> "объявленный купон";
            case BOND_NEWS -> "новости";
            case ISSUER_RATING -> "рейтинги эмитента";
            case KEY_RATE -> "решения по ключевой ставке";
        };
    }

    private static String number(final BigDecimal value) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"))).format(value);
    }

    private static String bond(final AlertModel alert) {
        if (alert.ticker() == null) {
            return "Банк России";
        }
        return alert.bondName() == null ? alert.ticker() : "%s (%s)".formatted(alert.bondName(), alert.ticker());
    }
}

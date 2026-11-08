package ru.invest.api.alert.service.usecase.impl;

import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.model.event.BondEventModel;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Тексты уведомлений. Простой текст: Telegram получает его без разметки, экранировать ничего не нужно.
 */
final class AlertTexts {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final int TEN = 10;
    private static final int HUNDRED = 100;
    private static final int FEW_FROM = 2;
    private static final int FEW_TO = 4;
    private static final int TEENS_FROM = 11;
    private static final int TEENS_TO = 14;

    private AlertTexts() {
    }

    /**
     * «Купон через 3 дня, 14.11.2026» + облигация + выплата или подсказка.
     */
    static String event(final AlertType type, final BondEventModel event, final LocalDate today,
                        final String bondName, final String ticker) {
        final String title = switch (type) {
            case COUPON_BEFORE -> "Купон";
            case AMORTIZATION_BEFORE -> "Частичное погашение номинала";
            case OFFER_BEFORE -> "Оферта";
            case MATURITY_BEFORE -> "Погашение";
        };
        final String details = switch (type) {
            case OFFER_BEFORE -> "По оферте облигацию можно предъявить к выкупу - проверьте условия и сроки подачи заявки.";
            case COUPON_BEFORE -> event.getAmount() == null
                    ? "Размер купона ещё не объявлен."
                    : "Выплата: " + money(event.getAmount()) + " на облигацию.";
            default -> event.getAmount() == null ? null : "Выплата: " + money(event.getAmount()) + " на облигацию.";
        };
        // \n, а не %n: текст уходит в Telegram и в БД, перевод строки от ОС тут не нужен
        final String head = "%s %s, %s\n%s".formatted(title, when(today, event.getDate()),
                event.getDate().format(DATE), bond(bondName, ticker));
        return details == null ? head : head + "\n" + details;
    }

    static String bondGone(final String ticker) {
        return "Облигации %s больше нет в списке торгуемых - уведомления по ней удалены.".formatted(ticker);
    }

    static String bond(final String bondName, final String ticker) {
        return bondName == null || bondName.equals(ticker) ? ticker : "%s (%s)".formatted(bondName, ticker);
    }

    /** «сегодня», «завтра», «через 3 дня», «через 5 дней» */
    static String when(final LocalDate today, final LocalDate date) {
        final long days = ChronoUnit.DAYS.between(today, date);
        if (days <= 0) {
            return "сегодня";
        }
        if (days == 1) {
            return "завтра";
        }
        return "через %d %s".formatted(days, plural(days, "день", "дня", "дней"));
    }

    static String plural(final long n, final String one, final String few, final String many) {
        final long lastTwo = n % HUNDRED;
        final long last = n % TEN;
        if (lastTwo >= TEENS_FROM && lastTwo <= TEENS_TO) {
            return many;
        }
        if (last == 1) {
            return one;
        }
        return last >= FEW_FROM && last <= FEW_TO ? few : many;
    }

    /** «15,21 ₽», «12,5 USD» */
    static String money(final MoneyModel money) {
        final DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(RU));
        final BigDecimal quantity = money.getQuantity();
        final String currency = money.getCurrency() == null ? "" : money.getCurrency().toUpperCase(RU);
        final String sign = "RUB".equals(currency) || "RUR".equals(currency) ? "₽" : currency;
        return (format.format(quantity) + " " + sign).strip();
    }
}

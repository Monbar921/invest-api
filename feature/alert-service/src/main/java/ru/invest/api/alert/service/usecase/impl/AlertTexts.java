package ru.invest.api.alert.service.usecase.impl;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.model.event.BondEventModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            default -> throw new IllegalArgumentException("Not a date alert: " + type);
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

    /**
     * Порог уровня: «Цена опустилась до 95% от номинала: сейчас 94,6%», «Доходность к погашению выросла до 20%: …».
     */
    static String level(final AlertType type, final AlertDirection direction, final BigDecimal threshold,
                        final BigDecimal value, final BondModel bond) {
        final boolean up = direction == AlertDirection.UP;
        final String head = switch (type) {
            case PRICE_LEVEL -> "Цена %s до %s%% от номинала: сейчас %s%%".formatted(
                    up ? "выросла" : "опустилась", number(threshold), number(value));
            case YTM_LEVEL -> "Доходность к погашению %s до %s%%: сейчас %s%% годовых".formatted(
                    up ? "выросла" : "снизилась", number(threshold), number(value));
            case YTO_LEVEL -> "Доходность к оферте %s до %s%%: сейчас %s%% годовых".formatted(
                    up ? "выросла" : "снизилась", number(threshold), number(value));
            default -> throw new IllegalArgumentException("Not a level alert: " + type);
        };
        return head + "\n" + bond(bond.getName(), bond.getTicker()) + price(bond, type != AlertType.PRICE_LEVEL);
    }

    /** «Цена изменилась на −5,2% с 08.11.2026: сейчас 94,6% от номинала (было 99,8%)» */
    static String change(final BigDecimal change, final BigDecimal value, final BigDecimal base,
                         final LocalDate since, final BondModel bond) {
        return "Цена изменилась на %s%% с %s: сейчас %s%% от номинала (было %s%%)\n%s%s".formatted(signed(change),
                since.format(DATE), number(value), number(base), bond(bond.getName(), bond.getTicker()),
                price(bond, false));
    }

    /** «Цена за день −3,1%: сейчас 94,6% от номинала (закрытие прошлого дня 97,6%)» */
    static String daily(final BigDecimal change, final BigDecimal value, final BigDecimal previousClose,
                        final BondModel bond) {
        return "Цена за день %s%%: сейчас %s%% от номинала (закрытие прошлого дня %s%%)\n%s%s".formatted(
                signed(change), number(value), number(previousClose), bond(bond.getName(), bond.getTicker()),
                price(bond, false));
    }

    /** Строка с ценой: у доходности - и % от номинала; у цены - только деньги, % уже в заголовке */
    private static String price(final BondModel bond, final boolean withPercent) {
        final PriceModel price = bond.getPrice();
        if (price == null) {
            return "";
        }
        final String percent = withPercent && price.getPercentagePrice() != null
                ? number(price.getPercentagePrice()) + "% от номинала"
                : null;
        final String money = price.getCurrent() != null && price.getCurrent().getQuantity() != null
                ? money(price.getCurrent())
                : null;
        if (percent == null && money == null) {
            return "";
        }
        return "\nЦена: " + (percent == null ? money : money == null ? percent : percent + ", " + money);
    }

    /** «1,25», «95» */
    static String number(final BigDecimal value) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(RU)).format(value);
    }

    /** «+1,25», «−3,1» (минус - типографский, как в UI) */
    static String signed(final BigDecimal value) {
        final BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        final String abs = number(rounded.abs());
        return rounded.signum() > 0 ? "+" + abs : rounded.signum() < 0 ? "−" + abs : abs;
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

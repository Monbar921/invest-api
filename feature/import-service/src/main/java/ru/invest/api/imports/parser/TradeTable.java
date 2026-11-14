package ru.invest.api.imports.parser;

import ru.invest.api.imports.model.ImportKind;
import ru.invest.api.imports.model.Money;
import ru.invest.api.imports.model.TradeCandidate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Таблица сделок из отчёта: столбцы находятся по заголовкам, строки превращаются в {@link TradeCandidate}. Общая для
 * форматов - различаются только названия столбцов и то, что считать «не сделкой».
 */
final class TradeTable {
    private static final int KEY_HASH_LENGTH = 40;
    private static final int MAX_TRADE_ID_LENGTH = 100;

    private final Columns columns;
    private final String keyPrefix;
    private final String broker;
    private final String commissionCurrency;
    private final boolean amountIncludesAci;
    private final boolean strict;
    private final Map<String, Integer> occurrences = new HashMap<>();

    /**
     * @param keyPrefix          источник в ключе импорта: csv, sber
     * @param broker             имя брокера у сделок, если в строке его нет
     * @param commissionCurrency валюта комиссии; null - валюта из столбца валюты или валюта номинала
     * @param amountIncludesAci  сумма сделки - с НКД (отчёт Альфа-Банка): рубли за облигации - сумма без НКД
     * @param strict             строка без даты или операции - ошибка (свой шаблон); иначе - не сделка (итоги,
     *                           подзаголовки, РЕПО в отчётах брокеров)
     */
    TradeTable(final Columns columns, final String keyPrefix, final String broker, final String commissionCurrency,
               final boolean amountIncludesAci, final boolean strict) {
        this.columns = columns;
        this.keyPrefix = keyPrefix;
        this.broker = broker;
        this.commissionCurrency = commissionCurrency;
        this.amountIncludesAci = amountIncludesAci;
        this.strict = strict;
    }

    /**
     * @return сделка (возможно, с ошибкой); null - строка не сделка
     */
    TradeCandidate read(final int lineNo, final List<String> cells) {
        final String dateText = cell(cells, columns.date());
        final LocalDate date = ReportValues.date(dateText);
        if (date == null) {
            return strict
                    ? TradeCandidate.invalid(lineNo, "Дата не разобрана: «%s»".formatted(Objects.toString(dateText, "")))
                    : null;
        }
        final String kindText = cell(cells, columns.kind());
        final ImportKind kind = columns.kind() < 0 ? kindBySign(cells) : ReportValues.kind(kindText);
        if (kind == null) {
            return strict
                    ? TradeCandidate.invalid(lineNo, "Операция - «покупка» или «продажа», а не «%s»"
                    .formatted(Objects.toString(kindText, "")))
                    : null;
        }
        final String code = cell(cells, columns.isin());
        final String isin = ReportValues.isIsin(code) ? code.toUpperCase(Locale.ROOT) : null;
        // код из столбца ISIN - ещё и тикер: тикеры ОФЗ (SU26238RMFS4) по формату не отличить от ISIN
        final String ticker = columns.ticker() >= 0 ? cell(cells, columns.ticker()) : code;
        if (isin == null && ticker == null) {
            return TradeCandidate.invalid(lineNo, "Нет ISIN или тикера облигации");
        }
        final BigDecimal quantity;
        final BigDecimal price;
        final BigDecimal aci;
        final BigDecimal commission;
        final BigDecimal amount;
        try {
            quantity = abs(ReportValues.number(cell(cells, columns.quantity())));
            price = ReportValues.number(cell(cells, columns.price()));
            aci = abs(ReportValues.number(cell(cells, columns.aci())));
            commission = commission(cells);
            amount = abs(ReportValues.number(cell(cells, columns.amountRub())));
        } catch (final NumberFormatException e) {
            return TradeCandidate.invalid(lineNo, "Число не разобрано в строке %d".formatted(lineNo));
        }
        if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 0) {
            return TradeCandidate.invalid(lineNo, "Количество - целое число больше 0");
        }
        if (price == null) {
            return TradeCandidate.invalid(lineNo, "Нет цены в % номинала");
        }
        final String currency = lower(cell(cells, columns.currency()));
        final String feeCurrency = Optional.ofNullable(lower(cell(cells, columns.commissionCurrency())))
                .orElse(commissionCurrency == null ? currency : commissionCurrency);
        final String tradeId = cell(cells, columns.tradeId());
        final String rowBroker = Optional.ofNullable(cell(cells, columns.broker())).orElse(broker);
        final String key = key(tradeId, rowBroker, date, kind, isin, ticker, quantity, price, aci, commission);
        return new TradeCandidate(lineNo, kind, key, date, null, isin, ticker, null, cell(cells, columns.name()),
                quantity.intValueExact(), price.abs(), null,
                aci == null ? null : new Money(aci, currency),
                commission == null ? null : new Money(commission, feeCurrency),
                amountRub(amount, aci, currency), rowBroker, null, null);
    }

    /**
     * Рубли за облигации без НКД: у своего шаблона - столбец «Сумма в руб»; у отчёта, где сумма сделки - в валюте
     * расчётов и с НКД, - сумма без НКД, если расчёты в рублях, иначе null.
     */
    private BigDecimal amountRub(final BigDecimal amount, final BigDecimal aci, final String currency) {
        if (amount == null || amount.signum() == 0) {
            return null;
        }
        if (amountIncludesAci && !"rub".equals(currency) && !"rur".equals(currency)) {
            return null;
        }
        final BigDecimal clean = amountIncludesAci && aci != null ? amount.subtract(aci) : amount;
        return clean.signum() > 0 ? clean : null;
    }

    /**
     * Ключ: номер сделки, если он есть (он уникален у брокера); иначе - хеш значений строки и номер повтора таких же
     * строк в файле (две одинаковые сделки за день - две разные).
     */
    private String key(final String tradeId, final String rowBroker, final LocalDate date, final ImportKind kind,
                       final String isin, final String ticker, final BigDecimal quantity, final BigDecimal price,
                       final BigDecimal aci, final BigDecimal commission) {
        if (tradeId != null) {
            return keyPrefix + ":" + header(rowBroker) + ":" + truncate(tradeId);
        }
        final String values = String.join("|", date.toString(), kind.name(), Objects.toString(isin, ""),
                Objects.toString(ticker, ""), plain(quantity), plain(price), plain(aci), plain(commission));
        final int occurrence = occurrences.merge(values, 1, Integer::sum);
        return keyPrefix + ":" + ReportValues.sha256(values).substring(0, KEY_HASH_LENGTH) + "#" + occurrence;
    }

    /**
     * Направление по знаку количества - когда столбца операции нет (отчёт Альфа-Банка: «-» - выбытие актива).
     *
     * @return null - количества нет или оно не число (строка не сделка)
     */
    private ImportKind kindBySign(final List<String> cells) {
        try {
            final BigDecimal quantity = ReportValues.number(cell(cells, columns.quantity()));
            if (quantity == null || quantity.signum() == 0) {
                return null;
            }
            return quantity.signum() > 0 ? ImportKind.BUY : ImportKind.SELL;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal commission(final List<String> cells) {
        BigDecimal sum = null;
        for (final int index : columns.commissions()) {
            final BigDecimal value = ReportValues.number(cell(cells, index));
            if (value != null) {
                sum = (sum == null ? BigDecimal.ZERO : sum).add(value.abs());
            }
        }
        return sum;
    }

    private static String cell(final List<String> cells, final int index) {
        return index < 0 || index >= cells.size() ? null : ReportValues.trimmed(cells.get(index));
    }

    private static BigDecimal abs(final BigDecimal value) {
        return value == null ? null : value.abs();
    }

    private static String plain(final BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String lower(final String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String header(final String value) {
        return value == null ? "" : ReportValues.header(value);
    }

    private static String truncate(final String value) {
        return value.length() > MAX_TRADE_ID_LENGTH ? value.substring(0, MAX_TRADE_ID_LENGTH) : value;
    }

    /**
     * Номера столбцов; -1 - столбца нет.
     *
     * @param isin        ISIN или код бумаги (не ISIN - считается тикером, если столбца тикера нет)
     * @param commissions все столбцы комиссий - складываются
     * @param currency    валюта НКД и комиссии
     * @param amountRub   сумма сделки в рублях (у Альфа-Банка - в валюте расчётов, с НКД)
     */
    record Columns(int date, int kind, int isin, int ticker, int name, int quantity, int price, int aci,
                   List<Integer> commissions, int amountRub, int tradeId, int broker, int currency,
                   int commissionCurrency) {
        /** Нашлись дата, операция, бумага, количество и цена */
        boolean complete() {
            return date >= 0 && kind >= 0 && (isin >= 0 || ticker >= 0) && quantity >= 0 && price >= 0;
        }

        /** То же, но операцию даёт знак количества - столбец операции не нужен */
        boolean completeWithSignedQuantity() {
            return date >= 0 && (isin >= 0 || ticker >= 0) && quantity >= 0 && price >= 0;
        }

        /** Каких обязательных столбцов нет - для сообщения пользователю */
        List<String> missing() {
            final List<String> missing = new ArrayList<>();
            if (date < 0) {
                missing.add("дата");
            }
            if (kind < 0) {
                missing.add("операция");
            }
            if (isin < 0 && ticker < 0) {
                missing.add("ISIN или тикер");
            }
            if (quantity < 0) {
                missing.add("количество");
            }
            if (price < 0) {
                missing.add("цена");
            }
            return missing;
        }

        /**
         * Столбцы по заголовкам: для каждого поля - первый заголовок, который подходит; комиссии - все подходящие.
         * Заголовки приводятся {@link ReportValues#header}.
         */
        static Columns of(final List<String> headers, final Matchers matchers) {
            final List<String> normalized = headers.stream().map(ReportValues::header).toList();
            final List<Integer> commissions = new ArrayList<>();
            for (int i = 0; i < normalized.size(); i++) {
                if (matchers.commission().test(normalized.get(i))) {
                    commissions.add(i);
                }
            }
            return new Columns(find(normalized, matchers.date()), find(normalized, matchers.kind()),
                    find(normalized, matchers.isin()), find(normalized, matchers.ticker()),
                    find(normalized, matchers.name()), find(normalized, matchers.quantity()),
                    find(normalized, matchers.price()), find(normalized, matchers.aci()), commissions,
                    find(normalized, matchers.amountRub()), find(normalized, matchers.tradeId()),
                    find(normalized, matchers.broker()), find(normalized, matchers.currency()),
                    find(normalized, matchers.commissionCurrency()));
        }

        private static int find(final List<String> headers, final Predicate<String> matcher) {
            for (int i = 0; i < headers.size(); i++) {
                if (matcher.test(headers.get(i))) {
                    return i;
                }
            }
            return -1;
        }
    }

    /** Какие заголовки (после {@link ReportValues#header}) у какого поля */
    record Matchers(Predicate<String> date, Predicate<String> kind, Predicate<String> isin, Predicate<String> ticker,
                    Predicate<String> name, Predicate<String> quantity, Predicate<String> price,
                    Predicate<String> aci, Predicate<String> commission, Predicate<String> amountRub,
                    Predicate<String> tradeId, Predicate<String> broker, Predicate<String> currency,
                    Predicate<String> commissionCurrency) {
    }
}

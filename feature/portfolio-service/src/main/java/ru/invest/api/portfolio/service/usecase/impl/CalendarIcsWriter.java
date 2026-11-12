package ru.invest.api.portfolio.service.usecase.impl;

import org.springframework.stereotype.Component;
import ru.invest.api.common.model.event.BondEventType;
import ru.invest.api.portfolio.service.model.CalendarEventModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Календарь выплат в формате iCalendar (RFC 5545): событие на весь день на каждую выплату. UID события постоянный
 * (облигация, тип, дата) - при обновлении подписки календарь меняет событие, а не дублирует его.
 */
@Component
public class CalendarIcsWriter {
    private static final String CRLF = "\r\n";
    // строка не длиннее 75 октетов без перевода строки, продолжение начинается с пробела
    private static final int MAX_LINE_OCTETS = 75;
    private static final int MIN_FRACTION_DIGITS = 2;
    private static final int MAX_FRACTION_DIGITS = 6;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Map<BondEventType, String> LABELS = Map.of(
            BondEventType.COUPON, "Купон",
            BondEventType.AMORTIZATION, "Амортизация",
            BondEventType.OFFER, "Оферта",
            BondEventType.MATURITY, "Погашение");
    private static final Map<String, String> SYMBOLS = Map.of("rub", "₽", "rur", "₽", "usd", "$", "eur", "€",
            "cny", "¥");

    /**
     * @param events события календаря по дате
     * @param now    время выгрузки, UTC
     */
    public String write(final List<CalendarEventModel> events, final LocalDateTime now) {
        final StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//invest-api//Bond payments//RU");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:" + text("Выплаты по облигациям"));
        // как часто календарю перечитывать подписку: размеры купонов флоатеров объявляются по ходу
        line(ics, "REFRESH-INTERVAL;VALUE=DURATION:PT12H");
        line(ics, "X-PUBLISHED-TTL:PT12H");
        final String stamp = TIMESTAMP.format(now);
        events.forEach(event -> event(ics, event, stamp));
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private void event(final StringBuilder ics, final CalendarEventModel event, final String stamp) {
        final LocalDate date = event.date();
        final String name = event.bondName() == null ? event.ticker() : event.bondName();
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + text("%s-%s-%s@invest-api".formatted(event.ticker(), event.type(), DATE.format(date))
                .toLowerCase(Locale.ROOT)));
        line(ics, "DTSTAMP:" + stamp);
        line(ics, "DTSTART;VALUE=DATE:" + DATE.format(date));
        line(ics, "DTEND;VALUE=DATE:" + DATE.format(date.plusDays(1)));
        line(ics, "SUMMARY:" + text("%s: %s - %s".formatted(LABELS.get(event.type()), name, summaryAmount(event))));
        line(ics, "DESCRIPTION:" + text(event.ticker() + "\n" + description(event)));
        line(ics, "TRANSP:TRANSPARENT");
        line(ics, "END:VEVENT");
    }

    private static String summaryAmount(final CalendarEventModel event) {
        if (event.amount() != null) {
            return money(event.amount(), event.currency());
        }
        if (event.amountUnknown()) {
            return "размер не объявлен";
        }
        return event.type() == BondEventType.OFFER ? "без выплаты" : "в амортизации";
    }

    private static String description(final CalendarEventModel event) {
        if (event.amount() != null) {
            return "%d шт. × %s = %s".formatted(event.quantity(), money(event.amountPerBond(), event.currency()),
                    money(event.amount(), event.currency()));
        }
        if (event.amountUnknown()) {
            return "%d шт.; размер выплаты ещё не объявлен".formatted(event.quantity());
        }
        if (event.type() == BondEventType.OFFER) {
            return "%d шт.; своей выплаты у оферты нет: выкуп - по заявке держателя".formatted(event.quantity());
        }
        return "%d шт.; остаток номинала - в амортизации этого дня".formatted(event.quantity());
    }

    /** 1 234,50 ₽; у выплаты на облигацию - до 6 знаков после запятой, как в графике купонов */
    static String money(final BigDecimal amount, final String currency) {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        final DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setMinimumFractionDigits(MIN_FRACTION_DIGITS);
        format.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        final String code = currency == null ? "" : currency.toLowerCase(Locale.ROOT);
        final String symbol = SYMBOLS.getOrDefault(code, code.toUpperCase(Locale.ROOT));
        return (format.format(amount) + " " + symbol).strip();
    }

    /** Экранирование значения TEXT: обратная косая, точка с запятой, запятая и перевод строки */
    static String text(final String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /**
     * Строка с переносом по 75 октетов UTF-8; символ не разрывается.
     */
    static void line(final StringBuilder ics, final String content) {
        int octets = 0;
        int index = 0;
        while (index < content.length()) {
            final int codePoint = content.codePointAt(index);
            final int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_LINE_OCTETS) {
                ics.append(CRLF).append(' ');
                // пробел продолжения - тоже октет строки
                octets = 1;
            }
            ics.appendCodePoint(codePoint);
            octets += size;
            index += Character.charCount(codePoint);
        }
        ics.append(CRLF);
    }
}

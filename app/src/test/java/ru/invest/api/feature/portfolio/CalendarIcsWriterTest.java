package ru.invest.api.feature.portfolio;

import org.junit.jupiter.api.Test;
import ru.invest.api.common.model.event.BondEventType;
import ru.invest.api.portfolio.service.model.CalendarEventModel;
import ru.invest.api.portfolio.service.usecase.impl.CalendarIcsWriter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * Формат iCalendar без Spring и БД: экранирование, перенос длинных строк, постоянный UID, события без суммы.
 */
public class CalendarIcsWriterTest {
    private static final LocalDate DATE = LocalDate.of(2026, 12, 14);
    private static final int MAX_LINE_OCTETS = 75;

    private final CalendarIcsWriter writer = new CalendarIcsWriter();

    @Test
    void eventIsWholeDayWithStableUidAndEscapedText() {
        final String ics = writer.write(List.of(new CalendarEventModel(DATE, BondEventType.COUPON, "SU26238RMFS4",
                "ОФЗ 26238; выпуск, \"тест\"", "rub", 1250, new BigDecimal("35.15"), new BigDecimal("43937.50"),
                new BigDecimal("43937.50"), false, null)), LocalDateTime.of(2026, 11, 12, 9, 30));

        final String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded, containsString("UID:su26238rmfs4-coupon-20261214@invest-api\r\n"));
        assertThat(unfolded, containsString("DTSTAMP:20261112T093000Z\r\n"));
        assertThat(unfolded, containsString("DTSTART;VALUE=DATE:20261214\r\nDTEND;VALUE=DATE:20261215\r\n"));
        assertThat(unfolded, containsString("SUMMARY:Купон: ОФЗ 26238\\; выпуск\\, \"тест\" - 43 937\\,50 ₽\r\n"));
        assertThat(unfolded, containsString("DESCRIPTION:SU26238RMFS4\\n1250 шт. × 35\\,15 ₽ = 43 937\\,50 ₽\r\n"));
        assertThat(ics, endsWith("END:VCALENDAR\r\n"));
    }

    @Test
    void longLinesAreFoldedWithoutBreakingCharacters() {
        final String name = "Очень длинное название облигации с кириллицей, чтобы строка превысила 75 октетов";
        final String ics = writer.write(List.of(new CalendarEventModel(DATE, BondEventType.COUPON, "T", name, "usd",
                1, null, null, null, true, null)), LocalDateTime.of(2026, 11, 12, 0, 0));

        final List<Integer> lengths = Arrays.stream(ics.split("\r\n"))
                .map(line -> line.getBytes(StandardCharsets.UTF_8).length)
                .toList();
        assertThat(lengths, everyItem(lessThanOrEqualTo(MAX_LINE_OCTETS)));
        // после склейки строк продолжения текст цел
        assertThat(ics.replace("\r\n ", ""), containsString(name.replace(",", "\\,") + " - размер не объявлен"));
    }

    @Test
    void eventsWithoutPaymentAreExplained() {
        final String ics = writer.write(List.of(
                new CalendarEventModel(DATE, BondEventType.OFFER, "T", "Бумага", "rub", 2, null, null, null, false,
                        null),
                new CalendarEventModel(DATE, BondEventType.MATURITY, "T", "Бумага", "rub", 2, null, null, null, false,
                        null)), LocalDateTime.of(2026, 11, 12, 0, 0)).replace("\r\n ", "");

        assertThat(ics, containsString("SUMMARY:Оферта: Бумага - без выплаты"));
        assertThat(ics, containsString("SUMMARY:Погашение: Бумага - в амортизации"));
    }
}

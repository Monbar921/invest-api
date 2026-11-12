package ru.invest.api.feature.portfolio;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.cb.rf.supplier.model.CurrencyElementDto;
import ru.invest.api.dto.bond.event.BondEventDto;
import ru.invest.api.dto.bond.event.BondEventTypeDto;
import ru.invest.api.dto.portfolio.CalendarLinkDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarEventDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarMonthDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarTotalDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Календарь выплат портфеля и личная ссылка на него (.ics). Даты событий - от сегодняшнего дня (UTC, как
 * DateTimeMapper.getNow): календарь показывает только будущие выплаты.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = "truncate table portfolio_position, portfolio_broker, "
        + "portfolio_calendar_token, amortization, currency_rate restart identity")
public class PortfolioCalendarTest extends AbstractInvestApplicationTest {
    private static final String PORTFOLIO_URL = "http://localhost:8099/internal/rest/portfolio";
    private static final String FEED_URL = "http://localhost:8099/internal/rest/calendar/";
    private static final String BONDS_URL = "http://localhost:8099/internal/rest/bonds/";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    // bonds-for-controller.sql: ОФЗ, номинал 1000 ₽, coupon id 1, своего графика купонов нет
    private static final String OFZ = "SU26238RMFS4";
    // номинал 1000 $, coupon id 5
    private static final String USD_BOND = "RU000A105A95";
    // флоатер, coupon id 3
    private static final String FLOATER = "RU000A10EHC1";
    // номинал 950 ₽, bond id 2, купонов нет
    private static final String AMORTIZED = "RU000A1080Y2";
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CbRfClient cbRfClient;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    void setUp() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
        // доллар сегодня - 90 ₽
        final CurrencyElementDto usd = new CurrencyElementDto();
        usd.setCharCode("USD");
        usd.setNominal(BigDecimal.ONE);
        usd.setRate(new BigDecimal("90"));
        final CurrencyDto rates = new CurrencyDto();
        rates.setCurrencies(List.of(usd));
        when(cbRfClient.getCurrencyRates(any())).thenReturn(rates);
    }

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", PORTFOLIO_URL + "/calendar", null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("POST", PORTFOLIO_URL + "/calendar/link", null).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void paymentsAreMultipliedByQuantityAndTotaled() {
        // две покупки ОФЗ - 5 облигаций
        position(ALICE, OFZ, 2);
        position(ALICE, OFZ, 3);
        position(ALICE, USD_BOND, 1);
        position(ALICE, FLOATER, 4);
        position(BOB, OFZ, 100);
        coupon(1, OFZ, "35.15", "rub", today.plusDays(10));
        coupon(1, OFZ, "35.15", "rub", today.plusDays(190));
        // за пределами периода
        coupon(1, OFZ, "35.15", "rub", today.plusDays(370));
        coupon(5, USD_BOND, "31", "usd", today.plusDays(20));
        // прошлый купон флоатера - не в календаре; следующий ещё не объявлен
        coupon(3, FLOATER, "60", "rub", today.minusDays(60));
        coupon(3, FLOATER, "0", "rub", today.plusDays(30));

        final PortfolioCalendarDto calendar = calendar(ALICE, today.minusDays(30), today.plusDays(200));

        // прошлое не показывается: начало периода - сегодня
        assertThat(calendar.getFrom(), equalTo(today));
        assertThat(calendar.getEvents().stream().map(PortfolioCalendarEventDto::getTicker).toList(),
                contains(OFZ, USD_BOND, FLOATER, OFZ));
        final PortfolioCalendarEventDto ofz = calendar.getEvents().getFirst();
        assertThat(ofz.getType(), equalTo(BondEventTypeDto.COUPON));
        assertThat(ofz.getQuantity(), equalTo(5L));
        assertThat(ofz.getAmountPerBond(), comparesEqualTo(new BigDecimal("35.15")));
        assertThat(ofz.getAmount(), comparesEqualTo(new BigDecimal("175.75")));
        assertThat(ofz.getAmountInRub(), comparesEqualTo(new BigDecimal("175.75")));
        assertThat(ofz.isAmountUnknown(), equalTo(false));
        final PortfolioCalendarEventDto usd = calendar.getEvents().get(1);
        assertThat(usd.getCurrency(), equalTo("usd"));
        assertThat(usd.getAmount(), comparesEqualTo(new BigDecimal("31")));
        assertThat(usd.getAmountInRub(), comparesEqualTo(new BigDecimal("2790")));
        // флоатер без объявленного купона: суммы нет, итог помечен неполным, а не занижен молча
        final PortfolioCalendarEventDto floater = calendar.getEvents().get(2);
        assertThat(floater.getAmount(), nullValue());
        assertThat(floater.isAmountUnknown(), equalTo(true));
        assertThat(floater.getCurrency(), equalTo("rub"));

        final PortfolioCalendarTotalDto rub = total(calendar.getTotals(), "rub");
        assertThat(rub.getAmount(), comparesEqualTo(new BigDecimal("351.50")));
        assertThat(rub.isIncomplete(), equalTo(true));
        final PortfolioCalendarTotalDto usdTotal = total(calendar.getTotals(), "usd");
        assertThat(usdTotal.getAmountInRub(), comparesEqualTo(new BigDecimal("2790")));
        assertThat(usdTotal.isIncomplete(), equalTo(false));
        assertThat(calendar.getAmountInRub(), comparesEqualTo(new BigDecimal("3141.50")));
        assertThat(calendar.isIncomplete(), equalTo(true));
        assertThat(calendar.getUnknownTickers(), empty());

        // месяцы: по возрастанию, в сумме - итог периода; неполный - только месяц с флоатером
        final List<String> months = calendar.getMonths().stream().map(PortfolioCalendarMonthDto::getMonth).toList();
        assertThat(months, equalTo(months.stream().sorted().distinct().toList()));
        assertThat(calendar.getMonths().stream().map(PortfolioCalendarMonthDto::getAmountInRub)
                .reduce(BigDecimal.ZERO, BigDecimal::add), comparesEqualTo(calendar.getAmountInRub()));
        final String floaterMonth = today.plusDays(30).toString().substring(0, 7);
        calendar.getMonths().forEach(month -> assertThat(month.getMonth(), month.isIncomplete(),
                equalTo(month.getMonth().equals(floaterMonth))));
    }

    /**
     * Суммы календаря - выплаты вкладки «События» карточки × количество.
     */
    @Test
    void amountsMatchBondEventsTimesQuantity() {
        position(ALICE, OFZ, 7);
        coupon(1, OFZ, "35.15", "rub", today.plusDays(10));
        coupon(1, OFZ, "36.40", "rub", today.plusDays(190));

        final PortfolioCalendarDto calendar = calendar(ALICE, null, null);
        final List<BondEventDto> cardEvents = cardEvents(OFZ).stream()
                .filter(event -> !event.getDate().isBefore(today))
                .filter(event -> !event.getDate().isAfter(calendar.getTo()))
                .toList();

        assertThat(calendar.getTo(), equalTo(today.plusYears(1)));
        assertThat(calendar.getEvents(), hasSize(cardEvents.size()));
        for (int i = 0; i < cardEvents.size(); i++) {
            final BondEventDto card = cardEvents.get(i);
            final PortfolioCalendarEventDto event = calendar.getEvents().get(i);
            assertThat(event.getDate(), equalTo(card.getDate()));
            assertThat(event.getAmount(), comparesEqualTo(card.getAmount().getQuantity().multiply(BigDecimal.valueOf(7))));
        }
        // ставка пересмотрена - прошлый размер виден и в календаре
        assertThat(calendar.getEvents().getLast().getPreviousAmountPerBond(), comparesEqualTo(new BigDecimal("35.15")));
    }

    @Test
    void amortizationOfferAndMaturity() {
        position(ALICE, AMORTIZED, 10);
        final LocalDate maturity = today.plusDays(50);
        jdbcTemplate.update("update bond set maturity_date = ?, offer_date = ? where ticker = ?",
                maturity.atStartOfDay(), today.plusDays(40).atStartOfDay(), AMORTIZED);
        amortization(2, AMORTIZED, "50", today.plusDays(20));
        // последняя часть номинала - в амортизации дня погашения
        amortization(2, AMORTIZED, "900", maturity);

        final PortfolioCalendarDto calendar = calendar(ALICE, null, null);

        assertThat(calendar.getEvents().stream().map(PortfolioCalendarEventDto::getType).toList(), contains(
                BondEventTypeDto.AMORTIZATION, BondEventTypeDto.OFFER, BondEventTypeDto.AMORTIZATION,
                BondEventTypeDto.MATURITY));
        assertThat(calendar.getEvents().getFirst().getAmount(), comparesEqualTo(new BigDecimal("500")));
        // у оферты выплаты нет, у погашения остаток уже в амортизации - итог не становится неполным
        final PortfolioCalendarEventDto offer = calendar.getEvents().get(1);
        assertThat(offer.getAmount(), nullValue());
        assertThat(offer.isAmountUnknown(), equalTo(false));
        assertThat(calendar.getEvents().getLast().getAmount(), nullValue());
        assertThat(calendar.getEvents().getLast().isAmountUnknown(), equalTo(false));
        assertThat(total(calendar.getTotals(), "rub").getAmount(), comparesEqualTo(new BigDecimal("9500")));
        assertThat(calendar.isIncomplete(), equalTo(false));
    }

    @Test
    void maturityWithoutAmortizationPaysNominal() {
        position(ALICE, AMORTIZED, 3);
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?",
                today.plusDays(15).atStartOfDay(), AMORTIZED);

        final PortfolioCalendarEventDto maturity = calendar(ALICE, null, null).getEvents().getFirst();

        assertThat(maturity.getType(), equalTo(BondEventTypeDto.MATURITY));
        assertThat(maturity.getAmount(), comparesEqualTo(new BigDecimal("2850")));
    }

    @Test
    void bondMissingFromSystemMakesTotalIncomplete() {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, 'GONE', 1, 100, ?, now())", ALICE, today.minusDays(5));

        final PortfolioCalendarDto calendar = calendar(ALICE, null, null);

        assertThat(calendar.getEvents(), empty());
        assertThat(calendar.getUnknownTickers(), contains("GONE"));
        assertThat(calendar.isIncomplete(), equalTo(true));
    }

    @Test
    void emptyPortfolioHasEmptyCalendar() {
        final PortfolioCalendarDto calendar = calendar(ALICE, null, null);

        assertThat(calendar.getEvents(), empty());
        assertThat(calendar.getMonths(), empty());
        assertThat(calendar.getAmountInRub(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(calendar.isIncomplete(), equalTo(false));
    }

    @Test
    void periodEndBeforeStartIsRejected() {
        final String url = PORTFOLIO_URL + "/calendar?from=%s&to=%s".formatted(today.plusDays(10), today.plusDays(5));
        assertThat(send("GET", url, ALICE).statusCode(), equalTo(UNPROCESSABLE));
        // from в прошлом заменяется сегодняшним днём - to вчера тоже раньше
        final String past = PORTFOLIO_URL + "/calendar?to=%s".formatted(today.minusDays(1));
        assertThat(send("GET", past, ALICE).statusCode(), equalTo(UNPROCESSABLE));
    }

    @Test
    void icsLinkLifecycle() {
        position(ALICE, OFZ, 5);
        position(ALICE, FLOATER, 1);
        coupon(1, OFZ, "35.15", "rub", today.plusDays(10));
        coupon(3, FLOATER, "0", "rub", today.plusDays(30));
        // дальше года - .ics отдаёт все будущие выплаты
        coupon(1, OFZ, "35.15", "rub", today.plusYears(3));

        assertThat(link("GET", ALICE).isEnabled(), equalTo(false));
        final CalendarLinkDto link = link("POST", ALICE);
        assertThat(link.isEnabled(), equalTo(true));
        assertThat(link.getToken(), notNullValue());
        assertThat(link.getCreatedAt(), notNullValue());
        // токен показывается один раз: в БД - только хеш
        final CalendarLinkDto status = link("GET", ALICE);
        assertThat(status.isEnabled(), equalTo(true));
        assertThat(status.getToken(), nullValue());
        assertThat(jdbcTemplate.queryForObject("select token_hash from portfolio_calendar_token where user_id = ?",
                String.class, ALICE), not(equalTo(link.getToken())));

        // без X-User-Id: календарь ходит по ссылке без сессии
        final HttpResponse<String> ics = send("GET", FEED_URL + link.getToken() + ".ics", null);
        assertThat(ics.body(), ics.statusCode(), equalTo(OK));
        assertThat(ics.headers().firstValue("Content-Type").orElse(""), startsWith("text/calendar"));
        assertThat(ics.body(), startsWith("BEGIN:VCALENDAR\r\n"));
        assertThat(ics.body(), containsString("DTSTART;VALUE=DATE:" + today.plusDays(10).toString().replace("-", "")));
        assertThat(ics.body(), containsString("SUMMARY:Купон: ОФЗ 26238 - 175\\,75 ₽"));
        // длинные строки .ics переносятся - текст проверяется после склейки
        assertThat(ics.body().replace("\r\n ", ""), containsString("размер не объявлен"));
        assertThat(ics.body(), containsString("DTSTART;VALUE=DATE:" + today.plusYears(3).toString().replace("-", "")));
        // три купона и погашения обеих облигаций (2036 и 2041)
        assertThat(ics.body().split("BEGIN:VEVENT", -1).length - 1, equalTo(5));
        assertThat(ics.body(), containsString("DTSTART;VALUE=DATE:20410515"));

        // новая ссылка отменяет прежнюю
        final CalendarLinkDto renewed = link("POST", ALICE);
        assertThat(send("GET", FEED_URL + link.getToken() + ".ics", null).statusCode(), equalTo(NOT_FOUND));
        assertThat(send("GET", FEED_URL + renewed.getToken() + ".ics", null).statusCode(), equalTo(OK));

        // ссылка Боба показывает портфель Боба
        final CalendarLinkDto bob = link("POST", BOB);
        assertThat(send("GET", FEED_URL + bob.getToken() + ".ics", null).body(), not(containsString("VEVENT")));

        assertThat(send("DELETE", PORTFOLIO_URL + "/calendar/link", ALICE).statusCode(), equalTo(NO_CONTENT));
        assertThat(send("GET", FEED_URL + renewed.getToken() + ".ics", null).statusCode(), equalTo(NOT_FOUND));
        assertThat(link("GET", ALICE).isEnabled(), equalTo(false));
        // повторное отключение - тоже 204
        assertThat(send("DELETE", PORTFOLIO_URL + "/calendar/link", ALICE).statusCode(), equalTo(NO_CONTENT));
        assertThat(send("GET", FEED_URL + "unknown-token.ics", null).statusCode(), equalTo(NOT_FOUND));
    }

    private void position(final String userId, final String ticker, final int quantity) {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, ?, ?, 100, ?, now())", userId, ticker, quantity, today.minusDays(5));
    }

    private void coupon(final long couponId, final String ticker, final String amount, final String currency,
                        final LocalDate date) {
        jdbcTemplate.update("insert into coupon_data (coupon_id, ticker, uid, price, currency, fix_date, payment_date,"
                        + " created_at, created_by) select ?, ?, uid, ?, ?, ?, ?, now(), 'TEST' from bond where ticker = ?",
                couponId, ticker, new BigDecimal(amount), currency, date.minusDays(1).atStartOfDay(),
                date.atStartOfDay(), ticker);
    }

    private void amortization(final long bondId, final String ticker, final String amount, final LocalDate date) {
        jdbcTemplate.update("insert into amortization (bond_id, uid, ticker, price, currency, payment_date, created_at,"
                        + " created_by) select ?, uid, ?, ?, 'rub', ?, now(), 'TEST' from bond where id = ?",
                bondId, ticker, new BigDecimal(amount), date.atStartOfDay(), bondId);
    }

    private PortfolioCalendarDto calendar(final String userId, final LocalDate from, final LocalDate to) {
        final StringBuilder url = new StringBuilder(PORTFOLIO_URL + "/calendar?");
        Optional.ofNullable(from).ifPresent(date -> url.append("from=").append(date).append('&'));
        Optional.ofNullable(to).ifPresent(date -> url.append("to=").append(date));
        final HttpResponse<String> response = send("GET", url.toString(), userId);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PortfolioCalendarDto.class);
    }

    private List<BondEventDto> cardEvents(final String ticker) {
        final HttpResponse<String> response = send("GET", BONDS_URL + ticker + "/events", null);
        assertThat(response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private CalendarLinkDto link(final String method, final String userId) {
        final HttpResponse<String> response = send(method, PORTFOLIO_URL + "/calendar/link", userId);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), CalendarLinkDto.class);
    }

    private static PortfolioCalendarTotalDto total(final List<PortfolioCalendarTotalDto> totals, final String currency) {
        return totals.stream().filter(total -> Objects.equals(total.getCurrency(), currency)).findFirst().orElseThrow();
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String url, final String userId) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}

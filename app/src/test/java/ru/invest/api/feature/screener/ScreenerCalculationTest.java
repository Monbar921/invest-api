package ru.invest.api.feature.screener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.rating.CreditRatingModel;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.common.model.rating.RatingOutlook;
import ru.invest.api.common.model.rating.RatingScale;
import ru.invest.api.common.model.rating.RatingScope;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.news.supplier.model.OfzYieldCurve;
import ru.invest.api.news.supplier.usecase.impl.CreditRatingParser;
import ru.invest.api.news.supplier.usecase.impl.CreditRatingParser.Action;
import ru.invest.api.tinkoff.supplier.service.YieldMetrics;
import ru.invest.api.tinkoff.supplier.service.impl.CouponCalculationServiceImpl;
import ru.invest.api.ui.service.export.BondCsvWriter;
import ru.invest.api.ui.service.export.BondXlsxWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Расчёты скринера без Spring и БД: дюрация, кривая ОФЗ, разбор рейтинговых релизов, общая шкала, выгрузка.
 */
public class ScreenerCalculationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);
    private static final BigDecimal PRECISION = new BigDecimal("0.0001");
    private static final String RUB = "rub";

    private CouponCalculationServiceImpl couponCalculationService;

    @BeforeEach
    public void setUp() {
        final DateTimeMapper dateTimeMapper = mock(DateTimeMapper.class);
        when(dateTimeMapper.getNow()).thenReturn(NOW);
        couponCalculationService = new CouponCalculationServiceImpl(mock(CurrencyPriceUseCase.class), dateTimeMapper);
    }

    @Test
    public void durationOfParBondTest() {
        // учебный пример: 3 года, купон 10% раз в год, цена = номинал => доходность 10%,
        // D = (100/1,1 × 1 + 100/1,21 × 2 + 1100/1,331 × 3) / 1000 = 2,7355 года, модифицированная = D / 1,1 = 2,4869
        final YieldMetrics metrics = couponCalculationService.calculateMaturityMetrics(
                coupon(payment(365, "100"), payment(730, "100"), payment(1095, "100")), bond("1000", 1095));

        assertThat(metrics.yield(), closeTo(new BigDecimal("10.0000"), PRECISION));
        assertThat(metrics.durationMacaulay(), closeTo(new BigDecimal("2.7355"), PRECISION));
        assertThat(metrics.durationModified(), closeTo(new BigDecimal("2.4869"), PRECISION));
    }

    @Test
    public void durationOfZeroCouponBondTest() {
        // одна выплата - дюрация равна сроку: 2 года; 826,45 = 1000 / (1 + r)^2 => r = 10%
        final YieldMetrics metrics = couponCalculationService.calculateMaturityMetrics(coupon(), bond("826.4463", 730));

        assertThat(metrics.yield(), closeTo(new BigDecimal("10.0000"), PRECISION));
        assertThat(metrics.durationMacaulay(), closeTo(new BigDecimal("2.0000"), PRECISION));
        assertThat(metrics.durationModified(), closeTo(new BigDecimal("1.8182"), PRECISION));
    }

    @Test
    public void durationWithoutYieldTest() {
        // доходность не посчитать (нет НКД) - дюрации тоже нет
        final BondModel bond = bond("1000", 730);
        bond.getPrice().setAci(null);

        final YieldMetrics metrics = couponCalculationService.calculateMaturityMetrics(coupon(payment(365, "100")), bond);

        assertThat(metrics.yield(), nullValue());
        assertThat(metrics.durationMacaulay(), nullValue());
        assertThat(metrics.durationModified(), nullValue());
    }

    @Test
    public void ofzCurveMatchesMoexPublishedYieldsTest() {
        // параметры КБД Мосбиржи за 13.11.2026 и опубликованные ею же доходности на стандартные сроки
        final OfzYieldCurve curve = moexCurve();

        assertThat(curve.yieldAt(0.25).orElseThrow(), closeTo(new BigDecimal("13.3351"), PRECISION));
        assertThat(curve.yieldAt(1).orElseThrow(), closeTo(new BigDecimal("14.0063"), PRECISION));
        assertThat(curve.yieldAt(5).orElseThrow(), closeTo(new BigDecimal("16.2379"), PRECISION));
        assertThat(curve.yieldAt(10).orElseThrow(), closeTo(new BigDecimal("16.7429"), PRECISION));
        assertThat(curve.yieldAt(20).orElseThrow(), closeTo(new BigDecimal("16.8524"), PRECISION));
        // срок вне кривой - точки нет
        assertThat(curve.yieldAt(0).isPresent(), equalTo(false));
        assertThat(curve.yieldAt(31).isPresent(), equalTo(false));
    }

    @Test
    public void ratingScaleTest() {
        assertThat(RatingScale.score("AAA").orElseThrow(), equalTo(20));
        assertThat(RatingScale.score("AA-").orElseThrow(), equalTo(17));
        assertThat(RatingScale.score("BBB-").orElseThrow(), equalTo(11));
        assertThat(RatingScale.score("B-").orElseThrow(), equalTo(5));
        assertThat(RatingScale.score("C").orElseThrow(), equalTo(2));
        // выборочный дефолт у АКРА - RD, у НКР - SD: одна ступень
        assertThat(RatingScale.score("RD").orElseThrow(), equalTo(1));
        assertThat(RatingScale.score("SD").orElseThrow(), equalTo(1));
        assertThat(RatingScale.score("D").orElseThrow(), equalTo(0));
        assertThat(RatingScale.score("XYZ").isPresent(), equalTo(false));
        assertThat(RatingScale.grades().getFirst(), equalTo("AAA"));
        assertThat(RatingScale.grades().getLast(), equalTo("D"));
    }

    @Test
    public void acraIssuerReleasesTest() {
        // понижение: рейтинг - после «до уровня», прогноз из заголовка
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA,
                        "АКРА ПОНИЗИЛО КРЕДИТНЫЙ РЕЙТИНГ ООО «ФЛИТ» ДО УРОВНЯ BBB+(RU), ИЗМЕНИВ ПРОГНОЗ НА «НЕГАТИВНЫЙ»"),
                contains(new Action(RatingScope.ISSUER, null, null, rating("BBB+(RU)", "BBB+", 13), RatingOutlook.NEGATIVE)));
        // «под наблюдением» важнее прогноза
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA,
                        "АКРА ПРИСВОИЛО СТАТУС «ПОД НАБЛЮДЕНИЕМ» КРЕДИТНОМУ РЕЙТИНГУ ООО «АФ» B-(RU), ПРОГНОЗ «НЕГАТИВНЫЙ»"),
                contains(new Action(RatingScope.ISSUER, null, null, rating("B-(RU)", "B-", 5), RatingOutlook.UNDER_REVIEW)));
        // рейтинг эмитента и его облигаций в одном релизе; «AА+» с русской «А»
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA, "АКРА ПОДТВЕРДИЛО КРЕДИТНЫЙ РЕЙТИНГ ПАО «ТГК-1» НА УРОВНЕ AА+(RU), "
                        + "ПРОГНОЗ «СТАБИЛЬНЫЙ», И ЕГО ОБЛИГАЦИЙ (RU000A105NB4) — НА УРОВНЕ AА+(RU)"),
                contains(new Action(RatingScope.ISSUER, null, null, rating("AA+(RU)", "AA+", 19), RatingOutlook.STABLE),
                        new Action(RatingScope.ISSUE, "RU000A105NB4", null, rating("AA+(RU)", "AA+", 19), null)));
        // международная шкала - не сравнима с национальной
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA,
                "АКРА ПОДТВЕРДИЛО КРЕДИТНЫЙ РЕЙТИНГ АО «СОГАЗ» ПО МЕЖДУНАРОДНОЙ ШКАЛЕ НА УРОВНЕ А-, ПРОГНОЗ «СТАБИЛЬНЫЙ»"), empty());
        // отзыв рейтинга эмитента
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA,
                        "АКРА ОТОЗВАЛО КРЕДИТНЫЙ РЕЙТИНГ ООО «ЦЕНТР-РЕЗЕРВ» ПО ИНИЦИАТИВЕ РЕЙТИНГУЕМОГО ЛИЦА"),
                contains(new Action(RatingScope.ISSUER, null, null, null, null)));
    }

    @Test
    public void acraIssueReleasesTest() {
        // рейтинг выпуска по ISIN; «ААА» русскими буквами
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA, "АКРА ПРИСВОИЛО ВЫПУСКУ ОБЛИГАЦИЙ ООО «ИКС 5 ФИНАНС» СЕРИИ 003Р-24 "
                        + "(RU000A10GEH2) КРЕДИТНЫЙ РЕЙТИНГ ААА(RU)"),
                contains(new Action(RatingScope.ISSUE, "RU000A10GEH2", null, rating("AAA(RU)", "AAA", 20), null)));
        // ожидаемый рейтинг - ещё не рейтинг
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA, "АКРА ПРИСВОИЛО ВЫПУСКУ ОБЛИГАЦИЙ ООО «ГАЗПРОМ КАПИТАЛ» "
                + "СЕРИИ БО-003Р-31 ОЖИДАЕМЫЙ КРЕДИТНЫЙ РЕЙТИНГ еAAA(RU)"), empty());
        // отзыв рейтинга выпуска после погашения
        assertThat(CreditRatingParser.parse(RatingAgency.ACRA, "АКРА ОТОЗВАЛО КРЕДИТНЫЙ РЕЙТИНГ ВЫПУСКА ОБЛИГАЦИЙ (RU000A102CR0) "
                        + "МОСКОВСКОЙ ОБЛАСТИ В СВЯЗИ С ПОГАШЕНИЕМ ВЫПУСКА"),
                contains(new Action(RatingScope.ISSUE, "RU000A102CR0", null, null, null)));
    }

    @Test
    public void nkrReleasesTest() {
        // «с AA.ru до AA-.ru» - новый рейтинг после «до»
        assertThat(CreditRatingParser.parse(RatingAgency.NKR,
                        "НКР снизило кредитный рейтинг АО «Аэропорт Кольцово» с AA.ru до AA-.ru, прогноз — стабильный"),
                contains(new Action(RatingScope.ISSUER, null, null, rating("AA-.ru", "AA-", 17), RatingOutlook.STABLE)));
        assertThat(CreditRatingParser.parse(RatingAgency.NKR, "НКР снизило кредитный рейтинг ООО «Идель Нефтемаш» с B+.ru до CC.ru, "
                        + "прогноз — «рейтинг на пересмотре с возможностью понижения»"),
                contains(new Action(RatingScope.ISSUER, null, null, rating("CC.ru", "CC", 3), RatingOutlook.UNDER_REVIEW)));
        // выпуски без ISIN - по номерам серий; латинская P в номере серии
        assertThat(CreditRatingParser.parse(RatingAgency.NKR, "НКР присвоило двум выпускам биржевых облигаций ООО «Борец Капитал» "
                        + "серий 001P-06 и 001Р-08 кредитные рейтинги A-.ru"),
                contains(new Action(RatingScope.ISSUE, null, "001P-06", rating("A-.ru", "A-", 14), null),
                        new Action(RatingScope.ISSUE, null, "001P-08", rating("A-.ru", "A-", 14), null)));
        assertThat(CreditRatingParser.parse(RatingAgency.NKR,
                        "НКР отозвало кредитный рейтинг ООО «Патриот Групп» и прогноз по нему без подтверждения"),
                contains(new Action(RatingScope.ISSUER, null, null, null, null)));
        assertThat(CreditRatingParser.parse(RatingAgency.NKR, "НКР присвоило выпуску биржевых зелёных облигаций АО «Эталон-Финанс» "
                + "серии 002P-06 ожидаемый кредитный рейтинг eA-.ru"), empty());
        // суффикс другого агентства - не рейтинг НКР
        assertThat(CreditRatingParser.parse(RatingAgency.NKR, "НКР присвоило ООО «Ромашка» кредитный рейтинг A(RU)"), empty());
    }

    @Test
    public void csvExportTest() {
        final String csv = new String(BondCsvWriter.write(List.of(exportBond())), StandardCharsets.UTF_8);

        // BOM - чтобы Excel понял UTF-8
        assertThat(csv, startsWith("﻿Тикер;ISIN;Название;"));
        // десятичная запятая, дата по-русски, пустое поле - значения нет
        assertThat(csv, containsString("\r\nSU26238RMFS4;RU000A1038V6;\"ОФЗ; 26238\";government;rub;61,5;615;"));
        assertThat(csv, containsString(";15.05.2041;;нет;AA-;AA-(RU), АКРА;1,23;"));
    }

    @Test
    public void xlsxExportTest() throws Exception {
        final Map<String, String> entries = unzip(BondXlsxWriter.write(List.of(exportBond())));

        assertThat(entries, hasKey("[Content_Types].xml"));
        assertThat(entries, hasKey("xl/workbook.xml"));
        final String sheet = entries.get("xl/worksheets/sheet1.xml");
        // каждый файл - корректный XML
        entries.values().forEach(ScreenerCalculationTest::parseXml);
        assertThat(sheet, containsString("<t xml:space=\"preserve\">ОФЗ; 26238</t>"));
        assertThat(sheet, containsString("<v>61.5</v>"));
        // дата - число дней Excel с 30.12.1899 и стиль даты
        assertThat(sheet, containsString("s=\"2\"><v>" + ChronoUnit.DAYS.between(
                LocalDate.of(1899, 12, 30), LocalDate.of(2041, 5, 15)) + "</v>"));
        assertThat(sheet, containsString("<autoFilter ref=\"A1:W2\"/>"));
        assertThat(entries.get("xl/workbook.xml"), containsString("$A$1:$W$2"));
        assertThat(sheet, not(containsString("<v>null</v>")));
    }

    private static BondModel exportBond() {
        return new BondModel()
                .setTicker("SU26238RMFS4")
                .setIsin("RU000A1038V6")
                .setName("ОФЗ; 26238")
                .setSector("government")
                .setCurrency(RUB)
                .setMaturityDate(LocalDateTime.of(2041, 5, 15, 0, 0))
                .setPrice(new PriceModel().setPercentagePrice(new BigDecimal("61.50")).setCurrentInRub(new BigDecimal("615")))
                .setCoupon(new CouponModel().setQuantityPerYear(2).setIsFixed(true))
                .setCreditRating(new CreditRatingModel().setAgency(RatingAgency.ACRA).setRating("AA-(RU)").setGrade("AA-")
                        .setScore(17).setActionDate(LocalDate.of(2026, 10, 1)))
                .setSpreadToOfz(new BigDecimal("1.23"));
    }

    private static Map<String, String> unzip(final byte[] zip) throws Exception {
        final Map<String, String> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static void parseXml(final String xml) {
        try {
            final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertThat(document.getDocumentElement(), not(nullValue()));
        } catch (final Exception e) {
            throw new AssertionError("Not XML: " + xml, e);
        }
    }

    static OfzYieldCurve moexCurve() {
        return new OfzYieldCurve(LocalDate.of(2026, 11, 13), 1579.580772, -355.786825, 155.676185, 2.0,
                new double[]{12.606815, -8.545117, -13.221452, -5.479985, 10.435647, 8.708645, -3.194020, 0, 0});
    }

    private static CreditRatingParser.Rating rating(final String rating, final String grade, final int score) {
        return new CreditRatingParser.Rating(rating, grade, score);
    }

    private static CouponModel coupon(final CouponDataModel... payments) {
        return new CouponModel()
                .setQuantityPerYear(1)
                .setIsFixed(true)
                .setCouponData(List.of(payments));
    }

    private static CouponDataModel payment(final int daysFromNow, final String amount) {
        return new CouponDataModel()
                .setPaymentDate(NOW.plusDays(daysFromNow))
                .setPrice(rub(amount));
    }

    private static BondModel bond(final String price, final int daysToMaturity) {
        return new BondModel()
                .setMaturityDate(NOW.plusDays(daysToMaturity))
                .setPrice(new PriceModel()
                        .setNominal(rub("1000"))
                        .setCurrent(rub(price))
                        .setAci(rub("0")));
    }

    private static MoneyModel rub(final String amount) {
        return new MoneyModel().setQuantity(new BigDecimal(amount)).setCurrency(RUB);
    }
}

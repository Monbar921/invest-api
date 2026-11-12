package ru.invest.api.portfolio.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.PortfolioCalendarToken;
import ru.invest.api.common.entity.PortfolioPosition;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.event.BondEventType;
import ru.invest.api.common.repository.PortfolioCalendarTokenRepository;
import ru.invest.api.common.repository.PortfolioPositionRepository;
import ru.invest.api.common.usecase.BondEventUseCase;
import ru.invest.api.portfolio.service.model.CalendarEventModel;
import ru.invest.api.portfolio.service.model.CalendarLinkModel;
import ru.invest.api.portfolio.service.model.CalendarMonthModel;
import ru.invest.api.portfolio.service.model.CalendarTotalModel;
import ru.invest.api.portfolio.service.model.PortfolioCalendarModel;
import ru.invest.api.portfolio.service.usecase.PortfolioCalendarUseCase;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioCalendarUseCaseImpl implements PortfolioCalendarUseCase {
    private static final int DEFAULT_PERIOD_YEARS = 1;
    // в .ics - все будущие выплаты: длинные ОФЗ гасятся через 15-20 лет
    private static final int ICS_HORIZON_YEARS = 50;
    private static final int TOKEN_BYTES = 32;
    private static final int MONEY_SCALE = 2;
    private static final String LINK_NOT_FOUND = "Calendar link not found";

    private final PortfolioPositionRepository positionRepository;
    private final PortfolioCalendarTokenRepository tokenRepository;
    private final BondUseCase bondUseCase;
    private final BondEventUseCase bondEventUseCase;
    private final PriceConversionService priceConversionService;
    private final DateTimeMapper dateTimeMapper;
    private final CalendarIcsWriter icsWriter;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public PortfolioCalendarModel getCalendar(final String userId, final LocalDate from, final LocalDate to) {
        final LocalDate today = today();
        final LocalDate start = from == null || from.isBefore(today) ? today : from;
        final LocalDate end = to == null ? start.plusYears(DEFAULT_PERIOD_YEARS) : to;
        if (end.isBefore(start)) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.PORTFOLIO_INVALID,
                    "to must not be before from (from is today if not set or in the past)");
        }
        return calendar(userId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarLinkModel getLink(final String userId) {
        return new CalendarLinkModel(null,
                tokenRepository.findById(userId).map(PortfolioCalendarToken::getCreatedAt).orElse(null));
    }

    @Override
    @Transactional
    public CalendarLinkModel createLink(final String userId) {
        final byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        // одна ссылка на пользователя: запись по user_id перезаписывается, прежний токен перестаёт находиться
        final PortfolioCalendarToken link = tokenRepository.findById(userId)
                .orElseGet(() -> new PortfolioCalendarToken().setUserId(userId));
        tokenRepository.save(link.setTokenHash(hash(token)).setCreatedAt(createdAt));
        return new CalendarLinkModel(token, createdAt);
    }

    @Override
    @Transactional
    public void deleteLink(final String userId) {
        tokenRepository.findById(userId).ifPresent(tokenRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public String getIcs(final String token) {
        final PortfolioCalendarToken link = Optional.ofNullable(token)
                .filter(value -> !value.isBlank())
                .flatMap(value -> tokenRepository.findByTokenHash(hash(value)))
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND, LINK_NOT_FOUND));
        final LocalDate today = today();
        return icsWriter.write(calendar(link.getUserId(), today, today.plusYears(ICS_HORIZON_YEARS)).events(),
                dateTimeMapper.getNow());
    }

    private PortfolioCalendarModel calendar(final String userId, final LocalDate from, final LocalDate to) {
        // покупки - не позже сегодня, события - с сегодня: на дату любой выплаты в портфеле все покупки облигации
        final Map<String, Long> quantities = new LinkedHashMap<>();
        positionRepository.findByUserIdOrderByTickerAscBuyDateAscIdAsc(userId)
                .forEach(position -> quantities.merge(position.getTicker(), quantity(position), Long::sum));

        final List<CalendarEventModel> events = new ArrayList<>();
        final List<String> unknownTickers = new ArrayList<>();
        final RubRates rates = new RubRates();
        // сначала - какие облигации есть: исключение «облигации нет» внутри транзакции пометило бы её на откат
        final Set<String> known = quantities.isEmpty()
                ? Set.of()
                : bondUseCase.getByTickers(quantities.keySet()).keySet();
        quantities.forEach((ticker, quantity) -> {
            if (known.contains(ticker)) {
                events.addAll(events(bondUseCase.getWithSchedule(ticker), quantity, from, to, rates));
            } else {
                unknownTickers.add(ticker);
            }
        });
        events.sort(Comparator.comparing(CalendarEventModel::date)
                .thenComparing(CalendarEventModel::type)
                .thenComparing(CalendarEventModel::ticker));

        final Map<String, List<CalendarEventModel>> byMonth = events.stream()
                .collect(Collectors.groupingBy(event -> YearMonth.from(event.date()).toString(), TreeMap::new,
                        Collectors.toList()));
        final List<CalendarMonthModel> months = byMonth.entrySet().stream()
                .map(entry -> {
                    final List<CalendarTotalModel> totals = totals(entry.getValue(), rates);
                    return new CalendarMonthModel(entry.getKey(), totals, sumInRub(totals), incomplete(totals));
                })
                .toList();
        final List<CalendarTotalModel> totals = totals(events, rates);
        return new PortfolioCalendarModel(from, to, events, months, totals, sumInRub(totals),
                incomplete(totals) || !unknownTickers.isEmpty(), unknownTickers);
    }

    /**
     * События облигации в периоде - тот же BondEventUseCase, что у вкладки «События» карточки, - умноженные на
     * количество.
     */
    private List<CalendarEventModel> events(final BondModel bond, final long quantity, final LocalDate from,
                                            final LocalDate to, final RubRates rates) {
        final List<BondEventModel> bondEvents = bondEventUseCase.getEvents(bond, from).stream()
                .filter(event -> !event.getDate().isAfter(to))
                .toList();
        final BigDecimal count = BigDecimal.valueOf(quantity);
        return bondEvents.stream()
                .map(event -> {
                    final BigDecimal perBond = quantity(event.getAmount());
                    final String currency = Optional.ofNullable(event.getAmount())
                            .map(MoneyModel::getCurrency)
                            .orElseGet(() -> nominalCurrency(bond));
                    final BigDecimal amount = perBond == null
                            ? null
                            : perBond.multiply(count).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    return new CalendarEventModel(event.getDate(), event.getType(), bond.getTicker(), bond.getName(),
                            normalize(currency), quantity, perBond, amount, rates.toRub(amount, currency),
                            perBond == null && expectsPayment(event, bondEvents),
                            quantity(event.getPreviousAmount()));
                })
                .toList();
    }

    /**
     * Будет ли у события выплата. У оферты своей выплаты нет (выкуп - по заявке держателя); у погашения без суммы,
     * если в тот же день есть амортизация, остаток номинала уже в ней. В остальных случаях выплата есть, и если
     * размера нет - он не объявлен.
     */
    private static boolean expectsPayment(final BondEventModel event, final List<BondEventModel> bondEvents) {
        return switch (event.getType()) {
            case OFFER -> false;
            case MATURITY -> bondEvents.stream().noneMatch(other -> other.getType() == BondEventType.AMORTIZATION
                    && other.getDate().equals(event.getDate()));
            case COUPON, AMORTIZATION -> true;
        };
    }

    /**
     * Итоги по валютам, в порядке первой выплаты. Курс - тот же, что у событий: сумма в рублях - сумма рублей событий.
     */
    private static List<CalendarTotalModel> totals(final List<CalendarEventModel> events, final RubRates rates) {
        final Map<String, List<CalendarEventModel>> byCurrency = events.stream()
                .filter(event -> event.amount() != null || event.amountUnknown())
                .collect(Collectors.groupingBy(event -> Optional.ofNullable(event.currency()).orElse(""),
                        LinkedHashMap::new, Collectors.toList()));
        return byCurrency.entrySet().stream()
                .map(entry -> {
                    final String currency = entry.getKey().isEmpty() ? null : entry.getKey();
                    final BigDecimal amount = entry.getValue().stream()
                            .map(CalendarEventModel::amount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    final boolean incomplete = entry.getValue().stream().anyMatch(CalendarEventModel::amountUnknown);
                    return new CalendarTotalModel(currency, amount, rates.toRub(amount, currency), incomplete);
                })
                .toList();
    }

    private static BigDecimal sumInRub(final List<CalendarTotalModel> totals) {
        if (totals.stream().anyMatch(total -> total.amountInRub() == null)) {
            return null;
        }
        return totals.stream().map(CalendarTotalModel::amountInRub).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean incomplete(final List<CalendarTotalModel> totals) {
        return totals.stream().anyMatch(CalendarTotalModel::incomplete);
    }

    private static long quantity(final PortfolioPosition position) {
        return position.getQuantity();
    }

    private static BigDecimal quantity(final MoneyModel money) {
        return money == null ? null : money.getQuantity();
    }

    private static String nominalCurrency(final BondModel bond) {
        return Optional.ofNullable(bond.getPrice())
                .map(PriceModel::getNominal)
                .map(MoneyModel::getCurrency)
                .orElse(bond.getCurrency());
    }

    private static String normalize(final String currency) {
        return currency == null ? null : currency.toLowerCase(Locale.ROOT);
    }

    private LocalDate today() {
        return dateTimeMapper.getNow().toLocalDate();
    }

    private static String hash(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Курсы к рублю на сегодня на один запрос календаря: каждая валюта запрашивается один раз, в том числе неудачно.
     */
    private final class RubRates {
        private final Map<String, Optional<BigDecimal>> rates = new HashMap<>();

        BigDecimal toRub(final BigDecimal amount, final String currency) {
            if (amount == null || currency == null) {
                return null;
            }
            return rates.computeIfAbsent(currency.toUpperCase(Locale.ROOT),
                            code -> Optional.ofNullable(priceConversionService.getRubRate(code)))
                    .map(rate -> amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .orElse(null);
        }
    }
}

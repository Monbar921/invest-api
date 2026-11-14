package ru.invest.api.imports.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.PortfolioImportItem;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.PortfolioPositionRepository;
import ru.invest.api.common.repository.PortfolioSaleRepository;
import ru.invest.api.imports.model.ImportItemStatus;
import ru.invest.api.imports.model.ImportKind;
import ru.invest.api.imports.model.Money;
import ru.invest.api.imports.model.TradeCandidate;
import ru.invest.api.portfolio.service.usecase.impl.PortfolioIncomeCalculator;
import ru.invest.api.portfolio.service.usecase.impl.RubRates;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сделки источника → строки импорта со статусом: облигация в системе (по uid T-Invest, ISIN или тикеру), цена в %
 * номинала на дату сделки, НКД и комиссия в валюте номинала, проверки портфеля (количество, цена, дата, погашение),
 * повтор уже импортированной сделки и похожая сделка, введённая вручную. Вызывается в транзакции.
 */
@Component
@RequiredArgsConstructor
class ImportItemResolver {
    static final int MAX_QUANTITY = 1_000_000;
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(300);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int PRICE_SCALE = 4;
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 10;
    private static final int MAX_MESSAGE = 500;
    private static final int MAX_NAME = 255;

    private final TinkoffBondCacheRepositoryUseCase bondCache;
    private final BondUseCase bondUseCase;
    private final PortfolioIncomeCalculator incomeCalculator;
    private final PriceConversionService priceConversionService;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioSaleRepository saleRepository;
    private final DateTimeMapper dateTimeMapper;

    /** Облигации системы для поиска по uid, ISIN и тикеру - на один импорт */
    BondIndex bondIndex() {
        final Collection<BondModel> bonds = bondCache.getBonds().values();
        final Map<String, BondModel> byUid = new HashMap<>();
        final Map<String, BondModel> byIsin = new HashMap<>();
        final Map<String, BondModel> byTicker = new HashMap<>();
        bonds.stream().filter(Objects::nonNull).forEach(bond -> {
            Optional.ofNullable(bond.getUid()).ifPresent(uid -> byUid.put(uid, bond));
            Optional.ofNullable(bond.getIsin()).ifPresent(isin -> byIsin.put(upper(isin), bond));
            Optional.ofNullable(bond.getTicker()).ifPresent(ticker -> byTicker.put(upper(ticker), bond));
        });
        return new BondIndex(byUid, byIsin, byTicker);
    }

    /**
     * @return строки импорта (без importId) в порядке сделок
     */
    List<PortfolioImportItem> resolve(final String userId, final List<TradeCandidate> candidates,
                                      final BondIndex index) {
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final Set<String> buyKeys = positionRepository.findImportKeys(userId);
        final Set<String> saleKeys = saleRepository.findImportKeys(userId);
        final Set<String> manualBuys = positionRepository.findByUserIdOrderByTickerAscBuyDateAscIdAsc(userId).stream()
                .filter(lot -> lot.getImportKey() == null)
                .map(lot -> similar(lot.getTicker(), lot.getBuyDate(), lot.getQuantity()))
                .collect(Collectors.toSet());
        final Set<String> manualSales = saleRepository.findByUserIdOrderByTickerAscSellDateAscIdAsc(userId).stream()
                .filter(sale -> sale.getImportKey() == null)
                .map(sale -> similar(sale.getTicker(), sale.getSellDate(), sale.getQuantity()))
                .collect(Collectors.toSet());
        final Set<String> seenKeys = new HashSet<>();
        final Map<String, BondModel> schedules = new HashMap<>();
        final RubRates rates = new RubRates(priceConversionService);
        final List<PortfolioImportItem> items = new ArrayList<>();
        for (final TradeCandidate candidate : candidates) {
            final PortfolioImportItem item = new PortfolioImportItem()
                    .setLineNo(candidate.lineNo())
                    .setKind(candidate.kind() == null ? null : candidate.kind().name())
                    .setImportKey(candidate.importKey())
                    .setTradeDate(candidate.date())
                    .setIsin(candidate.isin())
                    .setFigi(candidate.figi())
                    .setTicker(candidate.ticker())
                    .setName(truncate(candidate.name(), MAX_NAME))
                    .setQuantity(candidate.quantity())
                    .setBroker(truncate(candidate.broker(), 100));
            items.add(item);
            if (candidate.error() != null) {
                status(item, ImportItemStatus.INVALID, candidate.error());
                continue;
            }
            final BondModel found = index.find(candidate.instrumentUid(), candidate.isin(), candidate.ticker());
            if (found == null) {
                status(item, ImportItemStatus.UNKNOWN_BOND, "Облигации нет в системе - сделка не импортируется");
                continue;
            }
            final BondModel bond = schedules.computeIfAbsent(found.getTicker(), bondUseCase::getWithSchedule);
            item.setTicker(bond.getTicker()).setIsin(bond.getIsin()).setName(truncate(bond.getName(), MAX_NAME));
            final List<String> notes = new ArrayList<>();
            fillAmounts(item, candidate, bond, rates, notes);
            final String invalid = validate(item, candidate, bond, today);
            if (invalid != null) {
                status(item, ImportItemStatus.INVALID, invalid);
                continue;
            }
            final boolean buy = candidate.kind() == ImportKind.BUY;
            if ((buy ? buyKeys : saleKeys).contains(candidate.importKey()) || !seenKeys.add(candidate.importKey())) {
                status(item, ImportItemStatus.DUPLICATE, "Уже импортирована");
            } else if (candidate.reviewNote() != null) {
                notes.addFirst(candidate.reviewNote());
                status(item, ImportItemStatus.NEEDS_REVIEW, String.join(". ", notes));
            } else if ((buy ? manualBuys : manualSales)
                    .contains(similar(bond.getTicker(), candidate.date(), candidate.quantity()))) {
                notes.addFirst("Похожая %s уже введена вручную: та же облигация, дата и количество"
                        .formatted(buy ? "покупка" : "продажа"));
                status(item, ImportItemStatus.POSSIBLE_DUPLICATE, String.join(". ", notes));
            } else {
                status(item, ImportItemStatus.NEW, notes.isEmpty() ? null : String.join(". ", notes));
            }
        }
        return items;
    }

    /** Цена в % номинала, рубли за валютную облигацию, НКД и комиссия в валюте номинала */
    private void fillAmounts(final PortfolioImportItem item, final TradeCandidate candidate, final BondModel bond,
                             final RubRates rates, final List<String> notes) {
        final String currency = PortfolioIncomeCalculator.nominalCurrency(bond);
        final LocalDate date = candidate.date();
        if (candidate.pricePercent() != null) {
            item.setPrice(candidate.pricePercent().setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        } else if (candidate.price() != null && !candidate.price().isZero()) {
            final BigDecimal nominal = incomeCalculator.nominalAt(bond, date);
            final Money price = candidate.price();
            if (nominal != null && nominal.signum() > 0 && sameCurrency(price.currency(), currency)) {
                item.setPrice(percent(price.amount(), nominal));
            } else if (nominal != null && nominal.signum() > 0) {
                // замещающие облигации торгуются в рублях, номинал - в валюте: пересчёт по курсу ЦБ на дату
                final BigDecimal priceRub = rates.toRubExact(price.amount(), price.currency(), date);
                final BigDecimal nominalRub = rates.toRubExact(nominal, currency, date);
                if (priceRub != null && nominalRub != null && nominalRub.signum() > 0) {
                    item.setPrice(percent(priceRub, nominalRub));
                    if (candidate.kind() == ImportKind.BUY && isRub(price.currency()) && candidate.quantity() != null) {
                        item.setAmountRub(price.amount().multiply(BigDecimal.valueOf(candidate.quantity()))
                                .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    }
                }
            }
        }
        item.setAci(toNominal(candidate.aci(), currency, date, rates, notes, "НКД"));
        item.setCommission(toNominal(candidate.commission(), currency, date, rates, notes, "комиссия"));
    }

    /** Сообщение об ошибке; null - сделку можно создать */
    private static String validate(final PortfolioImportItem item, final TradeCandidate candidate, final BondModel bond,
                                   final LocalDate today) {
        if (item.getPrice() == null) {
            return "Цену не перевести в % номинала: нет номинала облигации или курса ЦБ на дату сделки";
        }
        if (candidate.quantity() == null || candidate.quantity() < 1 || candidate.quantity() > MAX_QUANTITY) {
            return "Количество - от 1 до %d".formatted(MAX_QUANTITY);
        }
        if (item.getPrice().compareTo(MIN_PRICE) < 0 || item.getPrice().compareTo(MAX_PRICE) > 0) {
            return "Цена %s %% номинала - вне пределов от 0,01 до 300 %%".formatted(item.getPrice().toPlainString());
        }
        if (candidate.date().isAfter(today)) {
            return "Дата сделки в будущем";
        }
        final LocalDate maturity = Optional.ofNullable(bond.getMaturityDate()).map(LocalDateTime::toLocalDate)
                .orElse(null);
        if (maturity != null && candidate.kind() == ImportKind.BUY && candidate.date().isAfter(maturity)) {
            return "Покупка после даты погашения";
        }
        if (maturity != null && candidate.kind() == ImportKind.SELL && !candidate.date().isBefore(maturity)) {
            return "Продажа в дату погашения и позже не нужна: погашение портфель учитывает сам";
        }
        return null;
    }

    private static BigDecimal toNominal(final Money money, final String currency, final LocalDate date,
                                        final RubRates rates, final List<String> notes, final String what) {
        if (money == null || money.amount() == null) {
            return null;
        }
        if (money.currency() == null || sameCurrency(money.currency(), currency)) {
            return money.amount().abs().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        final BigDecimal rub = rates.toRubExact(money.amount().abs(), money.currency(), date);
        final BigDecimal rate = rates.toRubExact(BigDecimal.ONE, currency, date);
        if (rub == null || rate == null || rate.signum() <= 0) {
            notes.add("%s не пересчитана в валюту номинала: нет курса ЦБ".formatted(capitalize(what)));
            return null;
        }
        return rub.divide(rate, RATE_SCALE, RoundingMode.HALF_UP).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(final BigDecimal amount, final BigDecimal nominal) {
        return amount.abs().multiply(HUNDRED).divide(nominal, PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static void status(final PortfolioImportItem item, final ImportItemStatus status, final String message) {
        item.setStatus(status.name()).setMessage(truncate(message, MAX_MESSAGE));
    }

    private static String similar(final String ticker, final LocalDate date, final Integer quantity) {
        return upper(ticker) + "|" + date + "|" + quantity;
    }

    private static boolean sameCurrency(final String first, final String second) {
        return first != null && second != null && normalize(first).equals(normalize(second));
    }

    private static boolean isRub(final String currency) {
        return currency != null && "rub".equals(normalize(currency));
    }

    private static String normalize(final String currency) {
        final String lower = currency.toLowerCase(Locale.ROOT);
        return "rur".equals(lower) ? "rub" : lower;
    }

    private static String upper(final String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String capitalize(final String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String truncate(final String value, final int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Облигации системы по uid T-Invest, ISIN и тикеру */
    record BondIndex(Map<String, BondModel> byUid, Map<String, BondModel> byIsin, Map<String, BondModel> byTicker) {
        BondModel find(final String uid, final String isin, final String ticker) {
            if (uid != null && byUid.containsKey(uid)) {
                return byUid.get(uid);
            }
            if (isin != null && byIsin.containsKey(upper(isin))) {
                return byIsin.get(upper(isin));
            }
            return ticker == null ? null : byTicker.get(upper(ticker));
        }
    }
}

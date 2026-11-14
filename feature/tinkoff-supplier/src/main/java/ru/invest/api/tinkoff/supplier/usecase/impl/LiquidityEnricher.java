package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.screener.LiquidityModel;
import ru.invest.api.common.repository.PriceCandleRepository;
import ru.invest.api.common.repository.PriceCandleRepository.CandleVolume;
import ru.invest.api.common.usecase.BondScreenerEnricher;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ликвидность по дневным свечам за 30 дней: средний дневной объём и оборот. Сумма делится на число торговых дней
 * биржи в окне (дни, когда есть свеча хоть у одной облигации), а не на дни со сделками по этой облигации: иначе
 * бумага с одной сделкой за месяц выглядела бы такой же ликвидной, как торгуемая каждый день.
 */
@Service
@RequiredArgsConstructor
public class LiquidityEnricher implements BondScreenerEnricher {
    static final int PERIOD_DAYS = 30;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private final PriceCandleRepository priceCandleRepository;
    private final PriceConversionService priceConversionService;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public void enrich(final Collection<BondModel> bonds, final boolean detailed) {
        final Set<String> uids = bonds.stream()
                .filter(Objects::nonNull)
                .map(BondModel::getUid)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (uids.isEmpty()) {
            return;
        }

        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final LocalDate from = today.minusDays(PERIOD_DAYS - 1);
        final long tradingDays = priceCandleRepository.countTradingDays(from, today);
        // свечей за окно нет ни у кого - свечи не загружаются, объём неизвестен
        if (tradingDays == 0) {
            return;
        }

        final Set<String> withCandles = priceCandleRepository.findUidsWithCandles(uids);
        final Map<String, CandleVolume> volumes = priceCandleRepository.sumVolumes(uids, from, today).stream()
                .collect(Collectors.toMap(CandleVolume::getUid, Function.identity()));
        final Map<String, Optional<BigDecimal>> rubRates = new HashMap<>();

        bonds.stream()
                .filter(Objects::nonNull)
                .filter(bond -> withCandles.contains(bond.getUid()))
                .forEach(bond -> bond.setLiquidity(liquidity(bond, volumes.get(bond.getUid()), tradingDays, rubRates)));
    }

    /**
     * Нет свечей за окно у облигации, у которой свечи вообще есть, - сделок не было, объём 0.
     */
    private LiquidityModel liquidity(final BondModel bond, final CandleVolume volume, final long tradingDays,
                                     final Map<String, Optional<BigDecimal>> rubRates) {
        final BigDecimal days = BigDecimal.valueOf(tradingDays);
        final BigDecimal pieces = volume == null || volume.getVolume() == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(volume.getVolume());
        final BigDecimal volumeByPrice = volume == null || volume.getVolumeByPrice() == null
                ? BigDecimal.ZERO : volume.getVolumeByPrice();

        final LiquidityModel liquidity = new LiquidityModel()
                .setAvgDailyVolume(pieces.divide(days, SCALE, RoundingMode.HALF_UP))
                .setTradingDays((int) tradingDays)
                .setPeriodDays(PERIOD_DAYS);

        // цена свечи - % номинала: оборот = объём × цена / 100 × номинал (текущий - номинал на даты свечей не храним)
        final MoneyModel nominal = Optional.ofNullable(bond.getPrice()).map(PriceModel::getNominal).orElse(null);
        if (nominal == null || nominal.getQuantity() == null || StringUtils.isBlank(nominal.getCurrency())) {
            return liquidity;
        }
        final BigDecimal turnover = volumeByPrice.multiply(nominal.getQuantity())
                .divide(PERCENT.multiply(days), SCALE, RoundingMode.HALF_UP);
        liquidity.setAvgDailyTurnover(new MoneyModel().setQuantity(turnover).setCurrency(nominal.getCurrency()));
        rubRates.computeIfAbsent(nominal.getCurrency().toUpperCase(),
                        currency -> Optional.ofNullable(priceConversionService.getRubRate(currency)))
                .ifPresent(rate -> liquidity.setAvgDailyTurnoverRub(turnover.multiply(rate).setScale(SCALE, RoundingMode.HALF_UP)));
        return liquidity;
    }
}

package ru.invest.api.alert.service.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.alert.service.usecase.PriceAlertCheckUseCase;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.PriceCandle;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertState;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.PriceCandleRepository;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Пороги по цене и доходности. Данные - из кэша облигаций (его сбрасывает синхронизация цен перед событием
 * PricesSyncedEvent), закрытие прошлого дня - из дневных свечей.
 * <p>
 * Уровень срабатывает при пересечении и взводится снова, когда значение вернётся за порог с запасом (гистерезис):
 * 0,2 п.п. для цены, 0,1 п.п. для доходности. Изменение от цены при создании - один раз, затем правило выключается.
 * Изменение за день - не чаще раза в день.
 */
@Slf4j
@Service
public class PriceAlertCheckUseCaseImpl implements PriceAlertCheckUseCase {
    static final BigDecimal PRICE_HYSTERESIS = new BigDecimal("0.2");
    static final BigDecimal YIELD_HYSTERESIS = new BigDecimal("0.1");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int CHANGE_SCALE = 6;
    private static final Set<AlertType> PRICE_TYPES = Arrays.stream(AlertType.values())
            .filter(AlertType::isPriceRule)
            .collect(Collectors.toSet());

    private final BondAlertRepository alertRepository;
    private final PriceCandleRepository candleRepository;
    private final TinkoffBondCacheRepositoryUseCase bondCache;
    private final NotificationQueue notificationQueue;
    private final TransactionTemplate transaction;

    public PriceAlertCheckUseCaseImpl(final BondAlertRepository alertRepository,
                                      final PriceCandleRepository candleRepository,
                                      final TinkoffBondCacheRepositoryUseCase bondCache,
                                      final NotificationQueue notificationQueue,
                                      final PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.candleRepository = candleRepository;
        this.bondCache = bondCache;
        this.notificationQueue = notificationQueue;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int checkAll(final LocalDate today) {
        final Map<String, List<Long>> byTicker = alertRepository.findByEnabledTrueAndTypeIn(PRICE_TYPES)
                .stream()
                .collect(Collectors.groupingBy(BondAlert::getTicker,
                        Collectors.mapping(BondAlert::getId, Collectors.toList())));
        if (byTicker.isEmpty()) {
            return 0;
        }
        final Map<String, BondModel> bonds = bondsByTicker();
        final Map<String, PriceCandle> previousCloses = previousCloses(byTicker.keySet(), bonds, today);

        int queued = 0;
        for (final Map.Entry<String, List<Long>> entry : byTicker.entrySet()) {
            final BondModel bond = bonds.get(entry.getKey());
            if (bond == null) {
                // облигации нет - правила удалит утренняя проверка по датам
                continue;
            }
            try {
                queued += Optional.ofNullable(transaction.execute(status -> {
                    int count = 0;
                    for (final BondAlert alert : alertRepository.findAllById(entry.getValue())) {
                        if (alert.isEnabled()) {
                            count += check(alert, bond, previousCloses.get(bond.getUid()), today);
                        }
                    }
                    return count;
                })).orElse(0);
            } catch (final RuntimeException e) {
                log.error("Price alert check failed for {}", entry.getKey(), e);
            }
        }
        log.info("Price alert check: {} bonds, {} notifications queued", byTicker.size(), queued);
        return queued;
    }

    @Override
    public int check(final Long alertId, final LocalDate today) {
        return Optional.ofNullable(transaction.execute(status -> alertRepository.findById(alertId)
                .filter(BondAlert::isEnabled)
                .filter(alert -> alert.getType().isPriceRule())
                .map(alert -> {
                    final Map<String, BondModel> bonds = bondsByTicker();
                    final BondModel bond = bonds.get(alert.getTicker());
                    if (bond == null) {
                        return 0;
                    }
                    return check(alert, bond, previousCloses(Set.of(alert.getTicker()), bonds, today)
                            .get(bond.getUid()), today);
                })
                .orElse(0))).orElse(0);
    }

    private int check(final BondAlert alert, final BondModel bond, final PriceCandle previousClose,
                      final LocalDate today) {
        final BigDecimal value = value(alert.getType(), bond);
        if (value == null) {
            return 0;
        }
        return switch (alert.getType()) {
            case PRICE_LEVEL, YTM_LEVEL, YTO_LEVEL -> checkLevel(alert, bond, value);
            case PRICE_CHANGE -> checkChange(alert, bond, value);
            case PRICE_DAILY_CHANGE -> checkDaily(alert, bond, value, previousClose);
            default -> 0;
        };
    }

    private int checkLevel(final BondAlert alert, final BondModel bond, final BigDecimal value) {
        final BigDecimal threshold = alert.getThreshold();
        final boolean up = alert.getDirection() == AlertDirection.UP;
        final boolean reached = up ? value.compareTo(threshold) >= 0 : value.compareTo(threshold) <= 0;
        if (alert.getState() != AlertState.TRIGGERED) {
            if (!reached) {
                return 0;
            }
            alert.setState(AlertState.TRIGGERED);
            return fire(alert, triggerKey(alert), AlertTexts.level(alert.getType(), alert.getDirection(), threshold,
                    value, bond));
        }
        final BigDecimal margin = alert.getType() == AlertType.PRICE_LEVEL ? PRICE_HYSTERESIS : YIELD_HYSTERESIS;
        final boolean back = up
                ? value.compareTo(threshold.subtract(margin)) < 0
                : value.compareTo(threshold.add(margin)) > 0;
        if (back) {
            alert.setState(AlertState.ARMED);
        }
        return 0;
    }

    private int checkChange(final BondAlert alert, final BondModel bond, final BigDecimal value) {
        final BigDecimal base = alert.getBaseValue();
        if (base == null || base.signum() == 0) {
            alert.setBaseValue(value);
            return 0;
        }
        final BigDecimal change = change(value, base);
        if (!hits(change, alert.getDirection(), alert.getThreshold())) {
            return 0;
        }
        // изменение от цены при создании - одно уведомление; включение заново отсчитает от новой цены
        alert.setEnabled(false);
        return fire(alert, triggerKey(alert), AlertTexts.change(change, value, base,
                alert.getCreatedAt().toLocalDate(), bond));
    }

    /**
     * Изменение от закрытия прошлого торгового дня. Ключ - дата этого закрытия, а не «сегодня»: в выходные цена и
     * закрытие не меняются, и правило не должно повторять то же уведомление каждый день.
     */
    private int checkDaily(final BondAlert alert, final BondModel bond, final BigDecimal value,
                           final PriceCandle previousClose) {
        if (previousClose == null || previousClose.getClose() == null || previousClose.getClose().signum() == 0) {
            return 0;
        }
        final BigDecimal close = previousClose.getClose();
        final BigDecimal change = change(value, close);
        if (!hits(change, alert.getDirection(), alert.getThreshold())) {
            return 0;
        }
        return fire(alert, "%s:%d:%s".formatted(alert.getType(), alert.getId(), previousClose.getCandleDate()),
                AlertTexts.daily(change, value, close, bond));
    }

    private int fire(final BondAlert alert, final String dedupKey, final String text) {
        if (!notificationQueue.enqueue(alert.getUserId(), alert.getId(), alert.getTicker(), dedupKey, text)) {
            return 0;
        }
        alert.setTriggerCount(alert.getTriggerCount() + 1);
        alert.setLastTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));
        return 1;
    }

    /** Ключ срабатывания порога: номер следующего срабатывания - повторное взведение даёт новое уведомление */
    private static String triggerKey(final BondAlert alert) {
        return "%s:%d:%d".formatted(alert.getType(), alert.getId(), alert.getTriggerCount() + 1);
    }

    /** Изменение в процентах: (value - base) / base * 100 */
    static BigDecimal change(final BigDecimal value, final BigDecimal base) {
        return value.subtract(base).multiply(HUNDRED).divide(base, CHANGE_SCALE, RoundingMode.HALF_UP);
    }

    static boolean hits(final BigDecimal change, final AlertDirection direction, final BigDecimal threshold) {
        return switch (direction) {
            case UP -> change.compareTo(threshold) >= 0;
            case DOWN -> change.compareTo(threshold.negate()) <= 0;
            case ANY -> change.abs().compareTo(threshold) >= 0;
        };
    }

    private static BigDecimal value(final AlertType type, final BondModel bond) {
        return switch (type) {
            case YTM_LEVEL -> Optional.ofNullable(bond.getCoupon()).map(CouponModel::getYieldToMaturity).orElse(null);
            case YTO_LEVEL -> Optional.ofNullable(bond.getCoupon()).map(CouponModel::getYieldToOffer).orElse(null);
            default -> Optional.ofNullable(bond.getPrice()).map(PriceModel::getPercentagePrice).orElse(null);
        };
    }

    private Map<String, BondModel> bondsByTicker() {
        return bondCache.getBonds().values().stream()
                .filter(bond -> bond.getTicker() != null)
                .collect(Collectors.toMap(BondModel::getTicker, Function.identity(), (first, second) -> first));
    }

    /** Закрытие прошлого торгового дня по uid; свечей нет - изменения за день не считаем */
    private Map<String, PriceCandle> previousCloses(final Set<String> tickers, final Map<String, BondModel> bonds,
                                                    final LocalDate today) {
        final Set<String> uids = tickers.stream()
                .map(bonds::get)
                .filter(Objects::nonNull)
                .map(BondModel::getUid)
                .collect(Collectors.toSet());
        if (uids.isEmpty()) {
            return Map.of();
        }
        return candleRepository.findLastBefore(uids, today).stream()
                .filter(candle -> candle.getClose() != null)
                .collect(Collectors.toMap(PriceCandle::getUid, Function.identity(), (first, second) -> first));
    }
}

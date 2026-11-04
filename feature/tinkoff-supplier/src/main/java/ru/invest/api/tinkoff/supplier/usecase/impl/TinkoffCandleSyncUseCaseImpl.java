package ru.invest.api.tinkoff.supplier.usecase.impl;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.usecase.CandleSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCandleProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCandleUseCase;
import ru.ttech.piapi.core.connector.exception.ServiceRuntimeException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Active
public class TinkoffCandleSyncUseCaseImpl implements CandleSyncUseCase {
    private final TinkoffCandleProvider tinkoffCandleProvider;
    private final TinkoffCandleUseCase tinkoffCandleUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public void syncAll(final AuditModel audit) {
        final List<BondModel> bonds = tinkoffBondUseCase.getAll()
                .stream()
                .filter(Objects::nonNull)
                .filter(bond -> StringUtils.isNotBlank(bond.getUid()))
                .toList();

        log.info("Processing candles, total size is {}", bonds.size());
        int failed = 0;
        for (final BondModel bond : bonds) {
            try {
                if (!sync(bond, audit)) {
                    failed++;
                }
            } catch (final CallNotPermittedException e) {
                // предохранитель SDK открылся после серии ошибок API: остальные облигации упадут так же -
                // останавливаем шаг, их свечи доберёт следующий запуск
                log.error("Candles sync stopped: T-Invest API circuit breaker is open, {} bonds not processed",
                        bonds.size() - bonds.indexOf(bond));
                return;
            }
        }
        log.info("Candles processed, failed for {} bonds", failed);
    }

    @Override
    public void syncByTicker(final String ticker, final AuditModel audit) {
        if (!sync(tinkoffBondUseCase.findByTicker(ticker), audit)) {
            throw new IllegalStateException("Failed to sync candles for bond %s, see log".formatted(ticker));
        }
    }

    /**
     * Первый раз - вся история, дальше - с последней сохранённой свечи включительно: она могла быть незавершённой.
     *
     * @return false - свечи облигации получить или сохранить не удалось; ошибка не останавливает остальные облигации
     * @throws CallNotPermittedException предохранитель SDK открыт - продолжать бессмысленно
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private boolean sync(final BondModel bond, final AuditModel audit) {
        try {
            final LocalDate today = dateTimeMapper.getNow().toLocalDate();
            final Optional<LocalDate> lastCandleDate = tinkoffCandleUseCase.findLastCandleDate(bond.getUid());
            final List<CandleModel> candles = lastCandleDate
                    .map(last -> tinkoffCandleProvider.getDailyCandles(bond.getUid(), last, today))
                    .orElseGet(() -> tinkoffCandleProvider.getDailyHistory(bond.getUid(), today));

            tinkoffCandleUseCase.replaceFrom(bond.getUid(), candles, audit);
            return true;
        } catch (final CallNotPermittedException e) {
            throw e;
        } catch (final ServiceRuntimeException e) {
            // ошибка API по облигации - одной строкой: стектрейс на каждую из 1600 облигаций забивает лог за минуты
            log.warn("Failed to sync candles for bond {}: {} {} {}, trackingId={}", bond.getTicker(), e.getErrorType(),
                    e.getErrorCode(), e.getDescription(), e.getTrackingId());
            return false;
        } catch (final RuntimeException e) {
            log.warn("Failed to sync candles for bond {}", bond.getTicker(), e);
            return false;
        }
    }
}
